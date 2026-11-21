package nt.xenforo.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement

interface XfTagElement : PsiElement {
    val tagName: String
    val isBlock: Boolean
    val header: XfTagHeader?
    val closingTag: XfEndTagElement?
    val branchHeaders: List<XfTagHeader>
}

interface XfTagHeader : PsiElement {
    val tagName: String
    val tagNameElement: PsiElement?
    val isSelfClosing: Boolean
    val isBranch: Boolean

    fun getAttributeList(): List<XfAttribute>

    fun findAttribute(name: String): XfAttribute?
}

interface XfEndTagElement : PsiElement {
    val tagName: String
    val tagNameElement: PsiElement?
}

interface XfAttributeElement : PsiElement {
    val attributeName: String
    val attributeNameElement: PsiElement?
    val header: XfTagHeader?
}

interface XfReferenceHost : ContributedReferenceHost {
    val valueRange: TextRange
    val value: String
}

class XfAccessSegment(val name: String?, val element: PsiElement, val isCall: Boolean)

interface XfAccessExpression : ContributedReferenceHost {
    val variableElement: PsiElement?
    val variableName: String
    val segments: List<XfAccessSegment>
}

interface XfNamedCall : ContributedReferenceHost {
    val callName: String
    val callNameElement: PsiElement?
}
