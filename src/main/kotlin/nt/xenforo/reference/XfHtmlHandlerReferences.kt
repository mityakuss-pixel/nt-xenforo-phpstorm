package nt.xenforo.reference

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import nt.xenforo.index.XfJsHandlers
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfFileViewProvider

class XfHtmlHandlerReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), XfHtmlHandlerReferenceProvider())
    }
}

private class XfHtmlHandlerReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val value = element as? XmlAttributeValue ?: return PsiReference.EMPTY_ARRAY
        if (value.containingFile?.viewProvider !is XfFileViewProvider) return PsiReference.EMPTY_ARRAY
        val attribute = value.parent as? XmlAttribute ?: return PsiReference.EMPTY_ARRAY
        val keyPrefix = XfJsHandlers.prefixFor(attribute.name) ?: return PsiReference.EMPTY_ARRAY
        val range = value.valueTextRange.shiftLeft(value.textRange.startOffset)
        return XfNameSlots.handlerSlots(range.substring(value.text), range.startOffset, keyPrefix)
            .map { XfHtmlHandlerReference(value, it.range, keyPrefix) }
            .toTypedArray()
    }
}

class XfHtmlHandlerReference(value: XmlAttributeValue, range: TextRange, private val keyPrefix: String) :
    PsiPolyVariantReferenceBase<XmlAttributeValue>(value, range, true) {

    val key: String
        get() = keyPrefix + value

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        PsiElementResolveResult.createResults(XfJsHandlers.targets(element.project, key))

    override fun getVariants(): Array<Any> {
        val project = element.project
        if (DumbService.isDumb(project)) return emptyArray()
        return XfSymbols.keys(project, keyPrefix).map { key ->
            LookupElementBuilder.create(key.removePrefix(keyPrefix))
                .withIcon(AllIcons.Nodes.Class)
                .withTypeText(XfJsHandlers.className(project, key), true)
        }.toTypedArray()
    }

    companion object {
        fun at(file: PsiFile, offset: Int): XfHtmlHandlerReference? {
            val viewProvider = file.viewProvider as? XfFileViewProvider ?: return null
            for (candidate in listOf(offset, offset - 1)) {
                if (candidate < 0) continue
                val element = viewProvider.findElementAt(candidate, viewProvider.templateDataLanguage) ?: continue
                val value = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: continue
                val start = value.textRange.startOffset
                value.references.filterIsInstance<XfHtmlHandlerReference>()
                    .firstOrNull { it.rangeInElement.shiftRight(start).containsOffset(candidate) }
                    ?.let { return it }
            }
            return null
        }
    }
}
