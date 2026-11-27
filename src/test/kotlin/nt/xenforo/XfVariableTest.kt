package nt.xenforo

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import nt.xenforo.psi.XfAttribute
import nt.xenforo.reference.XfVariableReference

class XfVariableTest : XfTestCase() {
    fun testIfConditionOpensSet() {
        configureTemplate(
            "<xf:set var=\"\$hasBadges\" value=\"{{ is_addon_active('Vendor/Badges') }}\" />\n\n" +
                "<xf:if is=\"\$has<caret>Badges\">\n\t<xf:css src=\"vendor_badges.less\" />\n</xf:if>",
        )
        assertEquals("var", attributeOf(resolve()))
    }

    fun testNearestPreviousSetWins() {
        configureTemplate(
            "<xf:set var=\"\$title\" value=\"a\" />\n{\$title}\n<xf:set var=\"\$title\" value=\"b\" />\n{\$ti<caret>tle}",
        )
        val target = resolve()
        assertEquals(myFixture.file.text.lastIndexOf("var=\"\$title\"") + 5, target.textRange.startOffset)
    }

    fun testForeachValueAndKey() {
        configureTemplate("<xf:foreach loop=\"\$items\" key=\"\$id\" value=\"\$item\">{\$it<caret>em.title} {\$id}</xf:foreach>")
        assertEquals("value", attributeOf(resolve()))
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.lastIndexOf("\$id") + 2)
        assertEquals("key", attributeOf(resolve()))
    }

    fun testMacroArgument() {
        configureTemplate("<xf:macro id=\"row\" arg-user=\"!\">\n\t<xf:if is=\"\$us<caret>er.is_admin\">x</xf:if>\n</xf:macro>")
        val target = resolve()
        assertInstanceOf(target, XfAttribute::class.java)
        assertEquals("arg-user", (target as XfAttribute).attributeName)
    }

    fun testDeclarationAndGlobalsHaveNoTarget() {
        configureTemplate("<xf:set var=\"\$ti<caret>tle\" value=\"a\" />{\$xf.visitor.username}")
        assertEmpty(references())
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("\$xf") + 2)
        assertEmpty(references())
    }

    private fun references(): List<XfVariableReference> {
        val element = myFixture.file.viewProvider.findElementAt(myFixture.caretOffset, nt.xenforo.lang.XfLanguage) ?: return emptyList()
        val expression = PsiTreeUtil.getParentOfType(element, nt.xenforo.psi.XfAccessExpression::class.java, false) ?: return emptyList()
        return expression.references.filterIsInstance<XfVariableReference>().filter { it.multiResolve(false).isNotEmpty() }
    }

    private fun resolve(): PsiElement = references().single().multiResolve(false).single().element!!

    private fun attributeOf(element: PsiElement): String? = PsiTreeUtil.getParentOfType(element, XfAttribute::class.java)?.attributeName
}
