package nt.xenforo.reference

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import nt.xenforo.meta.XfGlobals
import nt.xenforo.php.XfTemplateParams
import nt.xenforo.psi.XfAccessExpression
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfTagElement

private const val SET_TAG = "set"
private const val FOREACH_TAG = "foreach"
private const val MACRO_TAG = "macro"
private const val VAR_ATTRIBUTE = "var"
private const val ARGUMENT_PREFIX = "arg-"
private val FOREACH_VARIABLES = listOf("value", "key", "i")

object XfTemplateVariables {
    fun isDeclaration(expression: XfAccessExpression): Boolean {
        val attribute = PsiTreeUtil.getParentOfType(expression, XfAttribute::class.java) ?: return false
        val tag = attribute.header?.tagName?.lowercase() ?: return false
        val name = attribute.attributeName.lowercase()
        val declares = (tag == SET_TAG && name == VAR_ATTRIBUTE) || (tag == FOREACH_TAG && name in FOREACH_VARIABLES)
        return declares && declared(attribute) == expression
    }

    fun declarations(expression: XfAccessExpression): List<PsiElement> {
        val name = expression.variableName
        if (name.isEmpty() || name == XfGlobals.ROOT || isDeclaration(expression)) return emptyList()
        val offset = expression.textRange.startOffset
        val scope = macroOf(expression)
        var tag = PsiTreeUtil.getParentOfType(expression, XfTagElement::class.java)
        while (tag != null) {
            val header = tag.header
            if (tag.tagName.equals(FOREACH_TAG, ignoreCase = true)) {
                FOREACH_VARIABLES.firstNotNullOfOrNull { attribute ->
                    declared(header?.findAttribute(attribute))?.takeIf { it.variableName == name && it.segments.isEmpty() }
                }?.let { return listOf(it) }
            }
            if (tag.tagName.equals(MACRO_TAG, ignoreCase = true)) break
            tag = PsiTreeUtil.getParentOfType(tag, XfTagElement::class.java)
        }
        val sets = sets(expression, name)
        sets.lastOrNull { it.textRange.startOffset < offset && macroOf(it) == scope }?.let { return listOf(it) }
        scope?.header?.findAttribute(ARGUMENT_PREFIX + name)?.let { return listOf(it) }
        sets.firstOrNull { macroOf(it) == scope }?.let { return listOf(it) }
        if (DumbService.isDumb(expression.project)) return emptyList()
        return XfTemplateParams.of(expression.containingFile)[name]?.map { it.element }.orEmpty()
    }

    private fun sets(expression: XfAccessExpression, name: String): List<XfAccessExpression> =
        PsiTreeUtil.findChildrenOfType(expression.containingFile, XfTagElement::class.java)
            .filter { it.tagName.equals(SET_TAG, ignoreCase = true) }
            .mapNotNull { declared(it.header?.findAttribute(VAR_ATTRIBUTE))?.takeIf { declaration -> declaration.variableName == name } }

    private fun declared(attribute: XfAttribute?): XfAccessExpression? =
        attribute?.attributeValue?.let { PsiTreeUtil.findChildOfType(it, XfAccessExpression::class.java) }

    private fun macroOf(element: PsiElement): XfTagElement? {
        var tag = PsiTreeUtil.getParentOfType(element, XfTagElement::class.java)
        while (tag != null && !tag.tagName.equals(MACRO_TAG, ignoreCase = true)) tag = PsiTreeUtil.getParentOfType(tag, XfTagElement::class.java)
        return tag
    }
}

class XfVariableReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val expression = element as? XfAccessExpression ?: return PsiReference.EMPTY_ARRAY
        val variable = expression.variableElement ?: return PsiReference.EMPTY_ARRAY
        if (expression.variableName.isEmpty() || expression.variableName == XfGlobals.ROOT) return PsiReference.EMPTY_ARRAY
        return arrayOf(XfVariableReference(expression, variable.textRange.shiftLeft(expression.textRange.startOffset)))
    }
}

class XfVariableReference(expression: XfAccessExpression, range: com.intellij.openapi.util.TextRange) :
    PsiPolyVariantReferenceBase<XfAccessExpression>(expression, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, VARIABLE_RESOLVER, false, incompleteCode)

    fun resolveDeclarations(): Array<ResolveResult> = PsiElementResolveResult.createResults(XfTemplateVariables.declarations(element))

    override fun getVariants(): Array<Any> = emptyArray()
}

private val VARIABLE_RESOLVER = ResolveCache.PolyVariantResolver<XfVariableReference> { reference, _ -> reference.resolveDeclarations() }
