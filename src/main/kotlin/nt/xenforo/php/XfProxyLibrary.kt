package nt.xenforo.php

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nt.xenforo.XfBundle.message
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

private const val LIBRARY_ID = "nt.xenforo.proxies"
private const val STUB_FILE = "xfcp.php"
private const val DEBOUNCE_MILLIS = 600L
private const val ADD_ONS_PATH = "/src/addons/"
private const val CLASS_EXTENSIONS = "class_extensions"
private const val PHP_SUFFIX = ".php"
private const val SEPARATOR = '\\'
private const val PROXY_PREFIX = "XFCP_"

@Service(Service.Level.PROJECT)
class XfProxyLibrary(private val project: Project, private val scope: CoroutineScope) {
    private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val directory: Path = PathManager.getSystemDir().resolve("nt-xenforo").resolve("xfcp").resolve(project.locationHash)

    private val file: Path = directory.resolve(STUB_FILE)

    @OptIn(FlowPreview::class)
    fun start() {
        project.messageBus.connect(scope).subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { isRelevant(it.path) }) request()
            }
        })
        scope.launch {
            requests.debounce(DEBOUNCE_MILLIS).collectLatest { update() }
        }
        request()
    }

    fun request() {
        requests.tryEmit(Unit)
    }

    fun root(): VirtualFile? = LocalFileSystem.getInstance().findFileByNioFile(directory)?.takeIf { it.isDirectory }

    fun updateNow() {
        val content = ReadAction.computeBlocking<String?, RuntimeException> { content() } ?: return
        if (!write(content)) return
        val created = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory) ?: return
        VfsUtil.markDirtyAndRefresh(false, true, true, created)
        WriteAction.runAndWait<RuntimeException> { fireChanged(created) }
    }

    private suspend fun update() {
        val content = smartReadAction(project) { content() } ?: return
        val existed = root() != null
        val changed = withContext(Dispatchers.IO) { write(content) }
        if (!changed && existed) return
        val created = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory) } ?: return
        withContext(Dispatchers.IO) { VfsUtil.markDirtyAndRefresh(false, true, true, created) }
        if (!existed) edtWriteAction { fireChanged(created) }
    }

    fun content(): String? {
        if (project.isDisposed) return null
        val extensions = XfClassExtensions.all(project).filterKeys { !isDeclaredElsewhere(it) }
        if (extensions.isEmpty() && !Files.exists(file)) return null
        return XfClassExtensions.stub(extensions)
    }

    private fun isDeclaredElsewhere(extension: String): Boolean {
        val namespace = extension.substringBeforeLast(SEPARATOR, "")
        val proxy = SEPARATOR + (if (namespace.isEmpty()) "" else namespace + SEPARATOR) + PROXY_PREFIX + extension.substringAfterLast(SEPARATOR)
        val own = FileUtil.toSystemIndependentName(directory.toString())
        return PhpIndex.getInstance(project).getClassesByFQN(proxy).any { phpClass ->
            phpClass.containingFile?.virtualFile?.path?.startsWith(own) != true
        }
    }

    private fun write(content: String): Boolean {
        Files.createDirectories(directory)
        if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8) == content) return false
        Files.writeString(file, content, StandardCharsets.UTF_8)
        return true
    }

    private fun fireChanged(root: VirtualFile) {
        AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(project, message("proxies.library"), emptyList(), listOf(root), LIBRARY_ID)
    }

    private fun isRelevant(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (!normalized.contains(ADD_ONS_PATH)) return false
        return normalized.contains("/$CLASS_EXTENSIONS") || normalized.endsWith(PHP_SUFFIX)
    }

    companion object {
        fun getInstance(project: Project): XfProxyLibrary = project.service()
    }
}

class XfProxyLibraryStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        XfProxyLibrary.getInstance(project).start()
    }
}

class XfProxyLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val root = XfProxyLibrary.getInstance(project).root() ?: return emptyList()
        return listOf(XfProxySyntheticLibrary(root))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> = listOfNotNull(XfProxyLibrary.getInstance(project).root())
}

private class XfProxySyntheticLibrary(private val root: VirtualFile) : SyntheticLibrary(LIBRARY_ID, null), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

    override fun equals(other: Any?): Boolean = other is XfProxySyntheticLibrary && other.root == root

    override fun hashCode(): Int = root.hashCode()

    override fun getPresentableText(): String = message("proxies.library")

    override fun getIcon(unused: Boolean): Icon = PhpIcons.PHP_FILE
}
