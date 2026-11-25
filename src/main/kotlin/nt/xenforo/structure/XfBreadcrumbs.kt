package nt.xenforo.structure

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfTagElement
import javax.swing.Action
import javax.swing.Icon

private const val TAG_PREFIX = "xf:"
private val SKIPPED_TAGS = setOf("comment", "plain")
private val PROVIDERS = ExtensionPointName.create<BreadcrumbsProvider>("com.intellij.breadcrumbsInfoProvider")

class XfBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(XfLanguage, HTMLLanguage.INSTANCE)

    override fun acceptElement(element: PsiElement): Boolean = when {
        element is XfTagElement -> isTemplateTag(element)
        else -> delegate(element)?.acceptElement(element) == true
    }

    override fun acceptStickyElement(element: PsiElement): Boolean = when {
        element is XfTagElement -> isTemplateTag(element)
        else -> delegate(element)?.acceptStickyElement(element) == true
    }

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is XfTagElement -> TAG_PREFIX + element.tagName
        else -> delegate(element)?.getElementInfo(element).orEmpty()
    }

    override fun getElementIcon(element: PsiElement): Icon? = if (element is XfTagElement) null else delegate(element)?.getElementIcon(element)

    override fun getElementTooltip(element: PsiElement): String? = if (element is XfTagElement) null else delegate(element)?.getElementTooltip(element)

    override fun getChildren(element: PsiElement): List<PsiElement> = if (element is XfTagElement) emptyList() else delegate(element)?.getChildren(element).orEmpty()

    override fun getContextActions(element: PsiElement): List<Action> = if (element is XfTagElement) emptyList() else delegate(element)?.getContextActions(element).orEmpty()

    override fun getParent(element: PsiElement): PsiElement? {
        if (!isInTemplate(element)) return delegate(element)?.getParent(element)
        return enclosingTag(element)
    }

    private fun isTemplateTag(element: XfTagElement): Boolean = element.isBlock && element.tagName.lowercase() !in SKIPPED_TAGS

    private fun isInTemplate(element: PsiElement): Boolean = element.containingFile?.viewProvider is XfFileViewProvider

    private fun enclosingTag(element: PsiElement): PsiElement? {
        val viewProvider = element.containingFile?.viewProvider as? XfFileViewProvider ?: return null
        val range = element.textRange ?: return null
        val template = enclosing(viewProvider.findElementAt(range.startOffset, XfLanguage), element, range) { it is XfTagElement && isTemplateTag(it) }
        val markup = enclosing(viewProvider.findElementAt(range.startOffset, HTMLLanguage.INSTANCE), element, range) { it is XmlTag && acceptElement(it) }
        return listOfNotNull(template, markup).minByOrNull { it.textRange.length }
    }

    private fun enclosing(start: PsiElement?, element: PsiElement, range: TextRange, accepted: (PsiElement) -> Boolean): PsiElement? {
        var current = start
        while (current != null) {
            if (current != element && accepted(current) && current.textRange.contains(range) && current.textRange != range) return current
            current = current.parent
        }
        return null
    }

    private fun delegate(element: PsiElement): BreadcrumbsProvider? {
        var language: Language? = element.language
        while (language != null) {
            val current = language
            PROVIDERS.extensionList.firstOrNull { it !is XfBreadcrumbsProvider && it.languages.any { candidate -> candidate == current } }?.let { return it }
            language = language.baseLanguage
        }
        return null
    }
}
