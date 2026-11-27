package nt.xenforo.reference

import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.ProcessingContext
import nt.xenforo.index.XfCallableKind
import nt.xenforo.index.XfJsHandlers
import nt.xenforo.index.XfSymbols
import nt.xenforo.model.XfAccess
import nt.xenforo.model.XfCallables
import nt.xenforo.psi.XfAccessExpression
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfNamedCall
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfStringExpr

class XfReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(XfAttributeValue::class.java), XfNameReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfAttributeValue::class.java), XfJsFileReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfAttributeValue::class.java), XfCallbackReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfStringExpr::class.java), XfNameReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfAccessExpression::class.java), XfMemberReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfAccessExpression::class.java), XfVariableReferenceProvider())
        registrar.registerReferenceProvider(psiElement(XfNamedCall::class.java), XfCallableReferenceProvider())
    }
}

private class XfCallableReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val call = element as? XfNamedCall ?: return PsiReference.EMPTY_ARRAY
        val nameElement = call.callNameElement ?: return PsiReference.EMPTY_ARRAY
        val kind = when (call) {
            is XfFunctionCallExpr -> XfCallableKind.FUNCTION
            is XfFilter -> XfCallableKind.FILTER
            else -> return PsiReference.EMPTY_ARRAY
        }
        val range = nameElement.textRange.shiftLeft(call.textRange.startOffset)
        return arrayOf(XfCallableReference(call, range, kind))
    }
}

class XfCallableReference(call: XfNamedCall, range: TextRange, private val kind: XfCallableKind) :
    PsiReferenceBase<XfNamedCall>(call, range, true) {

    override fun resolve(): PsiElement? =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, CALLABLE_RESOLVER, false, false)

    override fun getVariants(): Array<Any> = emptyArray()

    fun resolveCallable(): PsiElement? = XfCallables.target(element.project, kind, element.callName)
}

private val CALLABLE_RESOLVER = ResolveCache.AbstractResolver<XfCallableReference, PsiElement> { reference, _ -> reference.resolveCallable() }

private class XfMemberReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val expression = element as? XfAccessExpression ?: return PsiReference.EMPTY_ARRAY
        if (expression.variableName.isEmpty()) return PsiReference.EMPTY_ARRAY
        val start = expression.textRange.startOffset
        return expression.segments.mapIndexedNotNull { index, segment ->
            if (segment.name == null) return@mapIndexedNotNull null
            XfMemberReference(expression, segment.element.textRange.shiftLeft(start), index)
        }.toTypedArray()
    }
}

class XfMemberReference(expression: XfAccessExpression, range: TextRange, private val index: Int) :
    PsiReferenceBase<XfAccessExpression>(expression, range, true) {

    override fun resolve(): PsiElement? =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, MEMBER_RESOLVER, false, false)

    override fun getVariants(): Array<Any> = emptyArray()

    fun resolveMember(): PsiElement? = XfAccess.member(element.project, element, index)?.element
}

private val MEMBER_RESOLVER = ResolveCache.AbstractResolver<XfMemberReference, PsiElement> { reference, _ -> reference.resolveMember() }

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
            .ifEmpty { resolveTargets() }

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
        if (slot.kind == XfNameKind.HANDLER) return PsiElementResolveResult.createResults(XfJsHandlers.targets(project, slot.keyPrefix + name))
        if (slot.kind == XfNameKind.EXTENSION) return PsiElementResolveResult.createResults(XfTemplateExtensions.targets(element, name))
        if (XfNameSlots.isCurrentTemplate(element, slot)) {
            XfLocalMacros.definitions(element.containingFile)[name]?.let { return PsiElementResolveResult.createResults(it) }
        }
        for (key in slot.keys(element)) {
            val targets = XfSymbols.targets(project, key, name)
            if (targets.isNotEmpty()) return PsiElementResolveResult.createResults(targets)
        }
        return ResolveResult.EMPTY_ARRAY
    }
}

private val RESOLVER = ResolveCache.PolyVariantResolver<XfNameReference> { reference, _ -> reference.resolveTargets() }
