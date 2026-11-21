package nt.xenforo.reference

import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.ProcessingContext
import nt.xenforo.index.XfSymbols
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfStringExpr

class XfReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(XfAttributeValue::class.java), XfNameReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfStringExpr::class.java), XfNameReferenceProvider())
    }
}

private class XfNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val host = element as? XfReferenceHost ?: return PsiReference.EMPTY_ARRAY
        return XfNameSlots.of(host).filter { !it.range.isEmpty }.map { XfNameReference(host, it) }.toTypedArray()
    }
}

class XfNameReference(host: XfReferenceHost, val slot: XfNameSlot) :
    PsiPolyVariantReferenceBase<XfReferenceHost>(host, slot.range, true), HighlightedReference {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, incompleteCode)

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val name = when (slot.kind) {
            XfNameKind.TEMPLATE -> newElementName.removeSuffix(".html")
            XfNameKind.PHRASE -> newElementName.removeSuffix(".txt")
            XfNameKind.PROPERTY -> newElementName.removeSuffix(".json")
            else -> newElementName
        }
        return super.handleElementRename(name)
    }

    fun resolveTargets(): Array<ResolveResult> {
        val project = element.project
        if (DumbService.isDumb(project)) return ResolveResult.EMPTY_ARRAY
        val name = slot.name(element)
        for (key in slot.keys(element)) {
            val targets = XfSymbols.targets(project, key, name)
            if (targets.isNotEmpty()) return PsiElementResolveResult.createResults(targets)
        }
        return ResolveResult.EMPTY_ARRAY
    }
}

private val RESOLVER = ResolveCache.PolyVariantResolver<XfNameReference> { reference, _ -> reference.resolveTargets() }
