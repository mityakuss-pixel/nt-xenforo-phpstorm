package nt.xenforo.editor

import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.psi.XfEndTagElement
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.psi.XfTypes
import java.util.Collections
import java.util.WeakHashMap

private val SESSION = Key.create<XfTagNameSession>("nt.xenforo.tagNameSession")
private val TAG_NAME = Regex("""[A-Za-z][\w:-]*""")

private class XfTagNameSession(var leader: RangeMarker, var follower: RangeMarker) {
    var changed = false

    fun contains(marker: RangeMarker, start: Int, end: Int): Boolean = marker.isValid && marker.startOffset <= start && end <= marker.endOffset

    fun dispose() {
        leader.dispose()
        follower.dispose()
    }
}

object XfTagNameSync {
    private val active: MutableSet<Document> = Collections.newSetFromMap(WeakHashMap())
    private var applying = false

    fun beforeChange(event: DocumentEvent) {
        if (applying) return
        val document = event.document
        val start = event.offset
        val end = event.offset + event.oldLength
        val session = document.getUserData(SESSION)
        if (session != null) {
            if (isUndoOrRedo(document)) return stop(document)
            if (session.contains(session.leader, start, end)) return
            if (session.contains(session.follower, start, end)) {
                val leader = session.leader
                session.leader = session.follower
                session.follower = leader
                return
            }
            stop(document)
        }
        start(document, start, end)
    }

    fun afterChange(event: DocumentEvent) {
        if (applying) return
        val session = event.document.getUserData(SESSION) ?: return
        if (session.contains(session.leader, event.offset, event.offset + event.newLength)) session.changed = true
    }

    fun beforeCommandFinished() {
        for (document in active.toList()) {
            val session = document.getUserData(SESSION) ?: continue
            if (!session.changed) continue
            session.changed = false
            if (!session.leader.isValid || !session.follower.isValid) {
                stop(document)
                continue
            }
            val name = document.charsSequence.subSequence(session.leader.startOffset, session.leader.endOffset).toString()
            if (!TAG_NAME.matches(name)) {
                if (name.isNotEmpty()) stop(document)
                continue
            }
            val follower = TextRange(session.follower.startOffset, session.follower.endOffset)
            if (follower.subSequence(document.charsSequence) == name) continue
            applying = true
            try {
                ApplicationManager.getApplication().runWriteAction { document.replaceString(follower.startOffset, follower.endOffset, name) }
            } finally {
                applying = false
            }
            session.follower.dispose()
            session.follower = marker(document, TextRange(follower.startOffset, follower.startOffset + name.length))
        }
    }

    private fun start(document: Document, start: Int, end: Int) {
        if (!WebEditorOptions.getInstance().isSyncTagEditing || isUndoOrRedo(document)) return
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (XfTemplateFiles.templateType(file) == null) return
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
        val documents = PsiDocumentManager.getInstance(project)
        if (!documents.isCommitted(document)) return
        val viewProvider = documents.getPsiFile(document)?.viewProvider as? XfFileViewProvider ?: return
        val name = tagName(viewProvider.findElementAt(start, XfLanguage), start, end)
            ?: tagName(viewProvider.findElementAt(start - 1, XfLanguage), start, end) ?: return
        val partner = partner(name) ?: return
        if (partner.text != name.text) return
        document.putUserData(SESSION, XfTagNameSession(marker(document, name.textRange), marker(document, partner.textRange)))
        active += document
    }

    private fun stop(document: Document) {
        document.getUserData(SESSION)?.dispose()
        document.putUserData(SESSION, null)
        active -= document
    }

    private fun tagName(element: PsiElement?, start: Int, end: Int): PsiElement? {
        if (element == null || element.elementType != XfTypes.TAG_NAME) return null
        val range = element.textRange
        return element.takeIf { range.startOffset <= start && end <= range.endOffset }
    }

    private fun partner(name: PsiElement): PsiElement? = when (val owner = name.parent) {
        is XfTagHeader -> if (owner.isBranch) null else (owner.parent as? XfTagElement)?.closingTag?.tagNameElement
        is XfEndTagElement -> (owner.parent as? XfTagElement)?.header?.tagNameElement
        else -> null
    }

    private fun marker(document: Document, range: TextRange): RangeMarker =
        document.createRangeMarker(range).apply {
            isGreedyToLeft = true
            isGreedyToRight = true
        }

    private fun isUndoOrRedo(document: Document): Boolean {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return false
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return false
        return UndoManager.getInstance(project).isUndoOrRedoInProgress
    }
}

class XfTagNameDocumentListener : DocumentListener {
    override fun beforeDocumentChange(event: DocumentEvent) = XfTagNameSync.beforeChange(event)

    override fun documentChanged(event: DocumentEvent) = XfTagNameSync.afterChange(event)
}

class XfTagNameCommandListener : CommandListener {
    override fun beforeCommandFinished(event: CommandEvent) = XfTagNameSync.beforeCommandFinished()
}
