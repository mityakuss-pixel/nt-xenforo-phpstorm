package nt.xenforo.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.ASTNode
import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.PairedBraceMatcher
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlTagNameProvider
import nt.xenforo.completion.tagLookup
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.meta.XfTags
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTypes

private val ACCESS_OWNERS = setOf(XfTypes.VARIABLE, XfTypes.IDENTIFIER, XfTypes.RPAREN)

private fun isTemplate(file: PsiFile): Boolean = file.viewProvider is XfFileViewProvider

private fun baseElementAt(file: PsiFile, offset: Int): PsiElement? =
    file.viewProvider.findElementAt(offset, XfLanguage)

class XfTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!isTemplate(file)) return Result.CONTINUE
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset
        val popup = when (charTyped) {
            ':' -> text.endsWithAt(offset, "<xf") || text.endsWithAt(offset, "</xf")
            '|', '$' -> baseElementAt(file, maxOf(0, offset - 1))?.language == XfLanguage
            '.' -> baseElementAt(file, maxOf(0, offset - 1))?.elementType in ACCESS_OWNERS
            else -> false
        }
        if (!popup) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!isTemplate(file)) return Result.CONTINUE
        when (c) {
            '{' -> closeExpression(editor)
            '>' -> closeTag(project, editor, file)
            '(' -> if (baseElementAt(file, maxOf(0, editor.caretModel.offset - 2))?.elementType == XfTypes.IDENTIFIER) {
                AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
            }
        }
        return Result.CONTINUE
    }

    private fun closeExpression(editor: Editor) {
        val document = editor.document
        val text = document.charsSequence
        val offset = editor.caretModel.offset
        if (!text.endsWithAt(offset, "{{") || text.endsWithAt(offset, "{{{")) return
        if (text.startsWithAt(offset, "}}") || text.startsWithAt(offset, " }}")) return
        document.insertString(offset, "  }}")
        editor.caretModel.moveToOffset(offset + 1)
    }

    private fun closeTag(project: Project, editor: Editor, file: PsiFile) {
        val document = editor.document
        val offset = editor.caretModel.offset
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val token = baseElementAt(file, offset - 1) ?: return
        if (token.elementType != XfTypes.TAG_END || token.parent.elementType != XfTypes.START_TAG) return
        val tag = token.parent.parent as? XfTagElement ?: return
        val name = tag.tagName
        if (name.isEmpty() || tag.closingTag != null || name.lowercase() in BRANCH_TAGS) return
        document.insertString(offset, "</xf:$name>")
    }
}

private val BRANCH_TAGS = setOf("else", "elseif")

private fun CharSequence.endsWithAt(offset: Int, suffix: String): Boolean =
    offset >= suffix.length && subSequence(offset - suffix.length, offset).toString() == suffix

private fun CharSequence.startsWithAt(offset: Int, prefix: String): Boolean =
    offset + prefix.length <= length && subSequence(offset, offset + prefix.length).toString() == prefix

class XfEnterHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): EnterHandlerDelegate.Result {
        if (!isTemplate(file) || originalHandler == null) return EnterHandlerDelegate.Result.Continue
        val offset = caretOffset.get()
        if (!isBetweenTags(file, editor.document, offset)) return EnterHandlerDelegate.Result.Continue
        originalHandler.execute(editor, editor.caretModel.currentCaret, dataContext)
        return EnterHandlerDelegate.Result.Default
    }

    private fun isBetweenTags(file: PsiFile, document: Document, offset: Int): Boolean {
        if (offset == 0 || !document.charsSequence.startsWithAt(offset, "</xf:")) return false
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        val previous = baseElementAt(file, offset - 1) ?: return false
        return previous.elementType == XfTypes.TAG_END && previous.parent.elementType == XfTypes.START_TAG
    }
}

class XfQuoteHandler : SimpleTokenSetQuoteHandler(XfTypes.STRING) {
    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        if (iterator.tokenType != XfTypes.STRING) return false
        val text = editor.document.charsSequence.subSequence(iterator.start, iterator.end)
        return text.length < 2 || text.last() != '\'' || text.contains('\n')
    }
}

class XfBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = BRACE_PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean =
        lbraceType != XfTypes.EXPR_START && lbraceType != XfTypes.VAR_START

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}

private val BRACE_PAIRS = arrayOf(
    BracePair(XfTypes.EXPR_START, XfTypes.EXPR_END, true),
    BracePair(XfTypes.VAR_START, XfTypes.VAR_END, false),
    BracePair(XfTypes.LPAREN, XfTypes.RPAREN, false),
    BracePair(XfTypes.LBRACKET, XfTypes.RBRACKET, false),
    BracePair(XfTypes.LBRACE, XfTypes.RBRACE, false),
)

class XfFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        PsiTreeUtil.processElements(root) { element ->
            when {
                element is XfTagElement -> tagRegion(element, document)?.let { descriptors += FoldingDescriptor(element.node, it) }
                element.elementType == XfTypes.COMMENT && isMultiline(element.textRange, document) ->
                    descriptors += FoldingDescriptor(element.node, element.textRange)
            }
            true
        }
        return descriptors.toTypedArray()
    }

    private fun tagRegion(tag: XfTagElement, document: Document): TextRange? {
        val header = tag.header ?: return null
        val closing = tag.closingTag ?: return null
        val range = TextRange(header.textRange.endOffset, closing.textRange.startOffset)
        return range.takeIf { !it.isEmpty && isMultiline(it, document) }
    }

    private fun isMultiline(range: TextRange, document: Document): Boolean =
        document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset)

    override fun getPlaceholderText(node: ASTNode): String =
        if (node.elementType == XfTypes.COMMENT) "<xf:comment>...</xf:comment>" else "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}

class XfCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null

    override fun getBlockCommentPrefix(): String = "<xf:comment>"

    override fun getBlockCommentSuffix(): String = "</xf:comment>"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}

class XfMultipleLangCommentProvider : MultipleLangCommentProvider {
    override fun canProcess(file: PsiFile, viewProvider: FileViewProvider): Boolean = viewProvider is XfFileViewProvider

    override fun getLineCommenter(file: PsiFile, editor: Editor, lineStartLanguage: Language, lineEndLanguage: Language): Commenter? =
        if (lineStartLanguage.isKindOf(XfLanguage) || lineStartLanguage.isKindOf(HTMLLanguage.INSTANCE)) XfCommenter() else null
}

class XfHtmlTagNameProvider : XmlTagNameProvider {
    override fun addTagNameVariants(elements: MutableList<LookupElement>, tag: XmlTag, prefix: String?) {
        if (!isTemplate(tag.containingFile)) return
        for (info in XfTags.ALL) {
            if (!info.nested) elements += tagLookup(info, "xf:")
        }
    }
}
