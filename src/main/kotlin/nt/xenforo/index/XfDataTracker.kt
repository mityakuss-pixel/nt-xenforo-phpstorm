package nt.xenforo.index

import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

private const val PHP_LANGUAGE = "PHP"
private val KEYS = Key.create<CachedValue<ConcurrentHashMap<String, List<String>>>>("nt.xenforo.cachedKeys")

@Service(Service.Level.PROJECT)
class XfDataTracker(project: Project, scope: CoroutineScope) : SimpleModificationTracker() {
    init {
        project.messageBus.connect(scope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                incModificationCount()
            }
        })
    }

    companion object {
        fun getInstance(project: Project): XfDataTracker = project.service()

        fun files(project: Project): Array<ModificationTracker> =
            arrayOf(getInstance(project), DumbService.getInstance(project).modificationTracker)

        fun php(project: Project): Array<ModificationTracker> {
            val language = Language.findLanguageByID(PHP_LANGUAGE)
            val psi = PsiModificationTracker.getInstance(project).let { if (language != null) it.forLanguage(language) else it }
            return files(project) + psi
        }

        fun keys(project: Project, prefix: String): List<String> {
            val cache = CachedValuesManager.getManager(project).getCachedValue(project, KEYS, {
                CachedValueProvider.Result.create(ConcurrentHashMap(), *files(project))
            }, false)
            cache[prefix]?.let { return it }
            val keys = XfSymbols.keys(project, prefix)
            cache[prefix] = keys
            return keys
        }
    }
}
