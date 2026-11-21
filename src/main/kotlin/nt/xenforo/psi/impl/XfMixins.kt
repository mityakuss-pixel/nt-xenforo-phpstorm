package nt.xenforo.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import nt.xenforo.psi.XfAccessExpression
import nt.xenforo.psi.XfAccessSegment
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfAttributeElement
import nt.xenforo.psi.XfEndTagElement
import nt.xenforo.psi.XfNamedCall
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.psi.XfTypes

abstract class XfTagMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfTagElement {
    override val header: XfTagHeader?
        get() = node.findChildByType(XfTypes.START_TAG)?.psi as? XfTagHeader

    override val tagName: String
        get() = header?.tagName.orEmpty()

    override val isBlock: Boolean
        get() = header?.isSelfClosing == false && header?.node?.findChildByType(XfTypes.TAG_END) != null

    override val closingTag: XfEndTagElement?
        get() = node.findChildByType(XfTypes.END_TAG)?.psi as? XfEndTagElement

    override val branchHeaders: List<XfTagHeader>
        get() = node.getChildren(null).filter { it.elementType == XfTypes.BRANCH_TAG }.mapNotNull { it.psi as? XfTagHeader }
}

abstract class XfTagHeaderMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfTagHeader {
    override val tagNameElement: PsiElement?
        get() = node.findChildByType(XfTypes.TAG_NAME)?.psi

    override val tagName: String
        get() = tagNameElement?.text.orEmpty()

    override val isSelfClosing: Boolean
        get() = node.findChildByType(XfTypes.TAG_SELF_CLOSE) != null

    override val isBranch: Boolean
        get() = node.elementType == XfTypes.BRANCH_TAG

    override fun findAttribute(name: String): XfAttribute? =
        getAttributeList().firstOrNull { it.attributeName.equals(name, ignoreCase = true) }
}

abstract class XfEndTagMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfEndTagElement {
    override val tagNameElement: PsiElement?
        get() = node.findChildByType(XfTypes.TAG_NAME)?.psi

    override val tagName: String
        get() = tagNameElement?.text.orEmpty()
}

abstract class XfAttributeMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfAttributeElement {
    override val attributeNameElement: PsiElement?
        get() = node.findChildByType(XfTypes.ATTR_NAME)?.psi

    override val attributeName: String
        get() = attributeNameElement?.text.orEmpty()

    override val header: XfTagHeader?
        get() = parent as? XfTagHeader
}

abstract class XfAttributeValueMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfReferenceHost {
    override val valueRange: TextRange
        get() = quotedContentRange(node, XfTypes.ATTR_QUOTE)

    override val value: String
        get() = valueRange.substring(text)

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

abstract class XfStringExprMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfReferenceHost {
    override val valueRange: TextRange
        get() {
            val text = text
            val start = minOf(1, text.length)
            val end = if (text.length > 1 && text.endsWith("'") && !text.endsWith("\\'")) text.length - 1 else text.length
            return TextRange(start, maxOf(start, end))
        }

    override val value: String
        get() = valueRange.substring(text).replace("\\'", "'").replace("\\\\", "\\")

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

abstract class XfAccessExpressionMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfAccessExpression {
    override val variableElement: PsiElement?
        get() = node.findChildByType(XfTypes.VARIABLE)?.psi

    override val variableName: String
        get() = variableElement?.text?.removePrefix("$").orEmpty()

    override val segments: List<XfAccessSegment>
        get() {
            val result = ArrayList<XfAccessSegment>()
            var child = node.findChildByType(XfTypes.VARIABLE)?.treeNext
            while (child != null) {
                val type = child.elementType
                if (type == TokenType.WHITE_SPACE) {
                    child = child.treeNext
                    continue
                }
                if (type != XfTypes.DOT && type != XfTypes.ARROW) break
                val target = significant(child.treeNext) ?: break
                if (target.elementType == XfTypes.IDENTIFIER) {
                    val after = significant(target.treeNext)
                    val isCall = after?.elementType == XfTypes.ARGUMENTS
                    result += XfAccessSegment(target.text, target.psi, isCall)
                    child = (if (isCall) after else target).treeNext
                } else {
                    result += XfAccessSegment(null, target.psi, false)
                    child = target.treeNext
                }
            }
            return result
        }

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

private fun significant(start: ASTNode?): ASTNode? {
    var current = start
    while (current != null && current.elementType == TokenType.WHITE_SPACE) current = current.treeNext
    return current
}

abstract class XfNamedCallMixin(node: ASTNode) : ASTWrapperPsiElement(node), XfNamedCall {
    override val callNameElement: PsiElement?
        get() = node.findChildByType(XfTypes.IDENTIFIER)?.psi

    override val callName: String
        get() = callNameElement?.text.orEmpty()

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

private fun quotedContentRange(node: ASTNode, quoteType: com.intellij.psi.tree.IElementType): TextRange {
    val first = node.firstChildNode
    val last = node.lastChildNode
    val length = node.textLength
    val start = if (first?.elementType == quoteType) first.textLength else 0
    val end = if (last != null && last !== first && last.elementType == quoteType) length - last.textLength else length
    return TextRange(start, maxOf(start, end))
}
