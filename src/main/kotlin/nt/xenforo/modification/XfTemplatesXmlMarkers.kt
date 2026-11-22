package nt.xenforo.modification

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import nt.xenforo.XfBundle.message
import java.awt.event.MouseEvent
import java.util.TreeMap
import javax.swing.Icon

private val HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("nt.xenforo.modificationHighlighters")
private const val MODIFICATIONS_PATH = "/template_modifications"

class XfTemplatesXmlMarkers(private val project: Project) : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (XfModificationMarkers.isTemplatesXml(file)) XfTemplatesXmlMarkerService.getInstance(project).refresh(file)
    }
}

@Service(Service.Level.PROJECT)
class XfTemplatesXmlMarkerService(private val project: Project, scope: CoroutineScope) {
    init {
        project.messageBus.connect(scope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it.path.replace('\\', '/').contains(MODIFICATIONS_PATH) }) refreshAll()
            }
        })
    }

    fun refresh(file: VirtualFile) {
        ReadAction.nonBlocking<TreeMap<Int, List<XfModification>>> { XfModificationMarkers.templatesXmlHits(project, file) }
            .inSmartMode(project)
            .coalesceBy(this, file)
            .finishOnUiThread(ModalityState.defaultModalityState()) { hits -> editors(file).forEach { apply(it, hits) } }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    fun applyNow(editor: Editor, file: VirtualFile) {
        apply(editor, ReadAction.computeBlocking<TreeMap<Int, List<XfModification>>, RuntimeException> { XfModificationMarkers.templatesXmlHits(project, file) })
    }

    private fun refreshAll() {
        FileEditorManager.getInstance(project).openFiles.filter(XfModificationMarkers::isTemplatesXml).forEach(::refresh)
    }

    private fun editors(file: VirtualFile): List<Editor> {
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return emptyList()
        return EditorFactory.getInstance().getEditors(document, project).toList()
    }

    private fun apply(editor: Editor, hits: TreeMap<Int, List<XfModification>>) {
        if (editor.isDisposed) return
        val markup = editor.markupModel
        editor.getUserData(HIGHLIGHTERS)?.forEach(markup::removeHighlighter)
        val length = editor.document.textLength
        val created = hits.mapNotNull { (offset, modifications) ->
            if (offset >= length) return@mapNotNull null
            markup.addRangeHighlighter(offset, offset + 1, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.EXACT_RANGE).apply {
                gutterIconRenderer = XfModificationGutterIcon(project, modifications)
            }
        }
        editor.putUserData(HIGHLIGHTERS, created)
    }

    companion object {
        fun getInstance(project: Project): XfTemplatesXmlMarkerService = project.service()
    }
}

private class XfModificationGutterIcon(private val project: Project, private val modifications: List<XfModification>) : GutterIconRenderer() {
    override fun getIcon(): Icon = AllIcons.Gutter.OverridenMethod

    override fun getTooltipText(): String = XfModificationMarkers.tooltip(modifications)

    override fun getAccessibleName(): String = message("modification.marker.name")

    override fun getAlignment(): Alignment = Alignment.RIGHT

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            val targets = XfModificationMarkers.targets(project, modifications)
            val mouse = event.inputEvent as? MouseEvent
            if (targets.size == 1 || mouse == null) targets.first().navigate(true) else PsiTargetNavigator(targets).navigate(mouse, message("modification.marker.title"), project)
        }
    }

    override fun equals(other: Any?): Boolean = other is XfModificationGutterIcon && other.modifications.map { it.key } == modifications.map { it.key }

    override fun hashCode(): Int = modifications.map { it.key }.hashCode()
}
