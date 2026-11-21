package nt.xenforo.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfStringExpr

class XfAttributeValueManipulator : AbstractElementManipulator<XfAttributeValue>() {
    override fun handleContentChange(element: XfAttributeValue, range: TextRange, newContent: String): XfAttributeValue {
        val text = range.replace(element.text, newContent)
        val replacement = createElement(element.project, "<xf:include template=$text />", XfAttributeValue::class.java)
        return element.replace(replacement) as XfAttributeValue
    }

    override fun getRangeInElement(element: XfAttributeValue): TextRange = element.valueRange
}

class XfStringExprManipulator : AbstractElementManipulator<XfStringExpr>() {
    override fun handleContentChange(element: XfStringExpr, range: TextRange, newContent: String): XfStringExpr {
        val escaped = newContent.replace("\\", "\\\\").replace("'", "\\'")
        val text = range.replace(element.text, escaped)
        val replacement = createElement(element.project, "{{ $text }}", XfStringExpr::class.java)
        return element.replace(replacement) as XfStringExpr
    }

    override fun getRangeInElement(element: XfStringExpr): TextRange = element.valueRange
}

private fun <T : com.intellij.psi.PsiElement> createElement(project: Project, text: String, type: Class<T>): T {
    val file = PsiFileFactory.getInstance(project).createFileFromText("dummy.html", XfLanguage, text)
    return PsiTreeUtil.findChildOfType(file, type) ?: error("Cannot create ${type.simpleName} from $text")
}
