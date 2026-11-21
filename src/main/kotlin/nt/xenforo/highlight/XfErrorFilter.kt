package nt.xenforo.highlight

import com.intellij.codeInsight.highlighting.TemplateLanguageErrorFilter
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfBranchTag
import nt.xenforo.psi.XfTokenSets

private const val RUNTIME_PLACEHOLDER = "<!--XF:"

class XfErrorFilter : TemplateLanguageErrorFilter(XfTokenSets.TEMPLATE_EDGES, XfFileViewProvider::class.java, "HTML", "CSS", "LESS") {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        if (!super.shouldHighlightErrorElement(element)) return false
        val provider = element.containingFile?.viewProvider as? XfFileViewProvider ?: return true
        if (element.language.isKindOf(XfLanguage)) return true
        if (isOnPlaceholderLine(provider.contents, element.textRange.startOffset)) return false
        val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return true
        val tagRange = tag.textRange
        val base = provider.getPsi(XfLanguage) ?: return true
        return PsiTreeUtil.findChildrenOfType(base, XfBranchTag::class.java).none { tagRange.contains(it.textRange) }
    }

    private fun isOnPlaceholderLine(text: CharSequence, offset: Int): Boolean {
        var start = offset.coerceIn(0, text.length)
        while (start > 0 && text[start - 1] != '\n') start--
        var end = offset.coerceIn(0, text.length)
        while (end < text.length && text[end] != '\n') end++
        return text.subSequence(start, end).contains(RUNTIME_PLACEHOLDER)
    }
}
