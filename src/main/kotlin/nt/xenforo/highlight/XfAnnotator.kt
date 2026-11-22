package nt.xenforo.highlight

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.elementType
import nt.xenforo.XfBundle
import nt.xenforo.meta.XfTags
import nt.xenforo.psi.XfArguments
import nt.xenforo.psi.XfDoubleQuotedExpr
import nt.xenforo.psi.XfEndTagElement
import nt.xenforo.psi.XfExpr
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfStringExpr
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTestExpr
import nt.xenforo.psi.XfTypes

private const val PHRASE_FUNCTION = "phrase"
private const val PHRASE_DYNAMIC_FUNCTION = "phrase_dynamic"

class XfPhraseDynamicFix(call: XfFunctionCallExpr) : LocalQuickFixAndIntentionActionOnPsiElement(call) {
    override fun getText(): String = XfBundle.message("annotator.phrase.dynamic.fix")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val name = (startElement as? XfFunctionCallExpr)?.callNameElement ?: return
        val document = file.viewProvider.document ?: return
        document.replaceString(name.textRange.startOffset, name.textRange.endOffset, PHRASE_DYNAMIC_FUNCTION)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

class XfAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element.elementType == XfTypes.IDENTIFIER -> highlightIdentifier(element, holder)
            element is XfTagElement -> checkTag(element, holder)
            element is XfEndTagElement -> checkEndTag(element, holder)
            element is XfFunctionCallExpr -> checkPhraseName(element, holder)
        }
    }

    private fun checkPhraseName(call: XfFunctionCallExpr, holder: AnnotationHolder) {
        if (!call.callName.equals(PHRASE_FUNCTION, ignoreCase = true)) return
        val name = call.arguments?.exprList?.firstOrNull() ?: return
        if (isLiteralName(name)) return
        holder.newAnnotation(HighlightSeverity.ERROR, XfBundle.message("annotator.phrase.name.literal"))
            .range(name)
            .withFix(XfPhraseDynamicFix(call))
            .create()
    }

    private fun isLiteralName(expression: XfExpr): Boolean = when (expression) {
        is XfStringExpr -> expression.value.isNotEmpty()
        is XfDoubleQuotedExpr -> expression.exprList.isEmpty() && expression.textLength > 2
        else -> false
    }

    private fun highlightIdentifier(element: PsiElement, holder: AnnotationHolder) {
        val key = identifierKey(element) ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(key).create()
    }

    private fun identifierKey(element: PsiElement): TextAttributesKey? = when (val parent = element.parent) {
        is XfFunctionCallExpr -> XfHighlighterColors.FUNCTION.takeIf { parent.callNameElement == element }
        is XfFilter, is XfTestExpr -> XfHighlighterColors.FILTER
        else -> XfHighlighterColors.METHOD.takeIf { nextSignificantSibling(element) is XfArguments }
    }

    private fun nextSignificantSibling(element: PsiElement): PsiElement? {
        var sibling = element.nextSibling
        while (sibling != null && sibling.elementType == TokenType.WHITE_SPACE) sibling = sibling.nextSibling
        return sibling
    }

    private fun checkTag(tag: XfTagElement, holder: AnnotationHolder) {
        val header = tag.header ?: return
        val nameElement = header.tagNameElement ?: return
        val name = header.tagName
        val closing = tag.closingTag
        if (tag.isBlock && closing == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, XfBundle.message("annotator.missing.closing.tag", name)).range(nameElement).create()
        }
        if (closing != null && closing.tagName.isNotEmpty() && !closing.tagName.equals(name, ignoreCase = true)) {
            closing.tagNameElement?.let {
                holder.newAnnotation(HighlightSeverity.ERROR, XfBundle.message("annotator.closing.tag.mismatch", closing.tagName, name)).range(it).create()
            }
        }
        val lower = name.lowercase()
        if (lower == "else" || lower == "elseif") {
            checkMisplacedBranch(tag, lower, nameElement, holder)
        }
        checkBranches(tag, holder)
    }

    private fun checkMisplacedBranch(tag: XfTagElement, name: String, nameElement: PsiElement, holder: AnnotationHolder) {
        val owner = generateSequence(tag.parent) { it.parent }.filterIsInstance<XfTagElement>().firstOrNull()
        val message = when {
            owner != null && XfTags.isBranch(owner.tagName, name) -> XfBundle.message("annotator.branch.not.self.closing", name)
            owner != null && owner.tagName.lowercase() in XfTags.BRANCHES.keys -> XfBundle.message("annotator.branch.not.allowed", name, owner.tagName)
            name == "else" -> XfBundle.message("annotator.else.outside")
            else -> XfBundle.message("annotator.elseif.outside")
        }
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(nameElement).create()
    }

    private fun checkBranches(tag: XfTagElement, holder: AnnotationHolder) {
        val branches = tag.branchHeaders
        if (branches.isEmpty()) return
        val owner = tag.tagName
        var elseSeen = false
        for (branch in branches) {
            val nameElement = branch.tagNameElement ?: continue
            val name = branch.tagName.lowercase()
            val message = when {
                !XfTags.isBranch(owner, name) && owner.lowercase() in XfTags.BRANCHES.keys -> XfBundle.message("annotator.branch.not.allowed", name, owner)
                !XfTags.isBranch(owner, name) -> if (name == "else") XfBundle.message("annotator.else.outside") else XfBundle.message("annotator.elseif.outside")
                elseSeen && name == "else" -> XfBundle.message("annotator.only.one.else")
                elseSeen -> XfBundle.message("annotator.elseif.after.else")
                else -> null
            }
            if (name == "else") elseSeen = true
            if (message != null) holder.newAnnotation(HighlightSeverity.ERROR, message).range(nameElement).create()
        }
    }

    private fun checkEndTag(endTag: XfEndTagElement, holder: AnnotationHolder) {
        if (endTag.parent is XfTagElement) return
        val nameElement = endTag.tagNameElement ?: endTag
        holder.newAnnotation(HighlightSeverity.ERROR, XfBundle.message("annotator.stray.closing.tag", endTag.tagName)).range(nameElement).create()
    }
}
