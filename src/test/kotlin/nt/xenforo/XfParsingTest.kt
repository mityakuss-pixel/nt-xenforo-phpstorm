package nt.xenforo

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfBranchTag
import nt.xenforo.psi.XfFile
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfTag
import nt.xenforo.psi.XfTernaryExpr
import nt.xenforo.psi.XfTestExpr
import nt.xenforo.psi.XfVariableExpr

class XfParsingTest : XfTestCase() {
    fun testOutputTemplateUsesTemplateLanguage() {
        val file = configureTemplate("<div>{{ phrase('x') }}</div>")
        assertInstanceOf(file.viewProvider, XfFileViewProvider::class.java)
        assertInstanceOf(file.viewProvider.getPsi(XfLanguage), XfFile::class.java)
    }

    fun testHtmlOutsideOutputStaysHtml() {
        val file = myFixture.addFileToProject("public/index.html", "<div>{{ x }}</div>")
        assertFalse(file.viewProvider is XfFileViewProvider)
    }

    fun testBranchesBelongToIf() {
        val tag = parseTags(
            """
            <xf:if is="${'$'}a">
            	one
            <xf:elseif is="${'$'}b" />
            	two
            <xf:else />
            	three
            </xf:if>
            """.trimIndent(),
        ).single { it.tagName == "if" }
        assertEquals(listOf("elseif", "else"), tag.branchHeaders.map { it.tagName })
        assertEquals("if", tag.closingTag?.tagName)
    }

    fun testExpressionAttributesAreParsedAsExpressions() {
        val base = baseFile("<xf:if is=\"\$xf.visitor.user_id && \$thread is not empty\">x</xf:if>")
        assertNotNull(PsiTreeUtil.findChildOfType(base, XfTestExpr::class.java))
        assertNotNull(PsiTreeUtil.findChildOfType(base, XfVariableExpr::class.java))
        assertNoErrors(base)
    }

    fun testStringAttributesKeepInterpolation() {
        val base = baseFile("<xf:textboxrow name=\"title\" label=\"{{ phrase('title') }}\" value=\"{\$thread.title}\" />")
        val attributes = PsiTreeUtil.findChildrenOfType(base, XfAttribute::class.java).map { it.attributeName }
        assertEquals(listOf("name", "label", "value"), attributes)
        assertEquals("phrase", PsiTreeUtil.findChildOfType(base, XfFunctionCallExpr::class.java)?.callName)
        assertNoErrors(base)
    }

    fun testExpressionsWithFiltersHashesAndTernary() {
        val base = baseFile("{{ \$a ? phrase('x', {'name': \$user.username|escape('js')}) : number(\$count, 2)|raw }}")
        assertNotNull(PsiTreeUtil.findChildOfType(base, XfTernaryExpr::class.java))
        assertEquals(setOf("escape", "raw"), PsiTreeUtil.findChildrenOfType(base, XfFilter::class.java).map { it.callName }.toSet())
        assertNoErrors(base)
    }

    fun testTagsInsideHtmlAttributes() {
        val base = baseFile("<li class=\"row<xf:if is=\"\$selected\"> is-selected</xf:if>\" data-x=\"{{ \$x }}\">x</li>")
        assertEquals("if", PsiTreeUtil.findChildOfType(base, XfTag::class.java)?.tagName)
        assertNoErrors(base)
    }

    fun testCommentAndPlainAreNotParsed() {
        val base = baseFile("<xf:comment><xf:if is=\"broken\"></xf:comment><xf:plain>{{ not parsed }}</xf:plain>")
        assertNull(PsiTreeUtil.findChildrenOfType(base, XfTag::class.java).firstOrNull { it.tagName == "if" })
        assertNoErrors(base)
    }

    fun testUnclosedExpressionRecovers() {
        val base = baseFile("<div>{{ \$a + }}</div><xf:if is=\"\$b\">ok</xf:if>")
        assertNotEmpty(PsiTreeUtil.findChildrenOfType(base, PsiErrorElement::class.java))
        assertEquals("if", PsiTreeUtil.findChildOfType(base, XfTag::class.java)?.tagName)
    }

    fun testElseOutsideIfIsNotABranch() {
        val base = baseFile("<div><xf:else /></div>")
        assertNull(PsiTreeUtil.findChildOfType(base, XfBranchTag::class.java))
    }

    private fun baseFile(text: String): com.intellij.psi.PsiFile = requireNotNull(configureTemplate(text).viewProvider.getPsi(XfLanguage))

    private fun parseTags(text: String) = PsiTreeUtil.findChildrenOfType(baseFile(text), XfTag::class.java).toList()

    private fun assertNoErrors(file: com.intellij.psi.PsiFile) {
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
        assertEmpty(errors)
    }
}
