package nt.xenforo

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleManager

class XfFormatterTest : XfTestCase() {
    fun testBranchesAlignWithIf() {
        doTest(
            """
            <div class="block">
            <xf:if is="${'$'}threads">
            <ol>
            <xf:foreach loop="${'$'}threads" value="${'$'}thread">
            <li>{${'$'}thread.title}</li>
            </xf:foreach>
            </ol>
            <xf:elseif is="${'$'}canPost" />
            <span>{{ phrase('be_first') }}</span>
            <xf:else />
            <span>{{ phrase('nothing') }}</span>
            </xf:if>
            </div>
            """.trimIndent(),
            """
            <div class="block">
            	<xf:if is="${'$'}threads">
            		<ol>
            			<xf:foreach loop="${'$'}threads" value="${'$'}thread">
            				<li>{${'$'}thread.title}</li>
            			</xf:foreach>
            		</ol>
            	<xf:elseif is="${'$'}canPost" />
            		<span>{{ phrase('be_first') }}</span>
            	<xf:else />
            		<span>{{ phrase('nothing') }}</span>
            	</xf:if>
            </div>
            """.trimIndent(),
        )
    }

    fun testTopLevelBlocksAndMultilineAttributes() {
        doTest(
            """
            <xf:macro id="row" arg-user="!">
            <xf:textboxrow name="username"
            label="{{ phrase('user_name') }}" />
            </xf:macro>
            """.trimIndent(),
            "<xf:macro id=\"row\" arg-user=\"!\">\n\t<xf:textboxrow name=\"username\"\n\t\t\t\t   label=\"{{ phrase('user_name') }}\" />\n</xf:macro>",
        )
    }

    fun testAttributesFollowHtmlAlignSetting() {
        val html = CodeStyle.getSettings(project).getCustomSettings(HtmlCodeStyleSettings::class.java)
        val previous = html.HTML_ALIGN_ATTRIBUTES
        html.HTML_ALIGN_ATTRIBUTES = false
        try {
            doTest(
                "<xf:formrow rowtype=\"fullWidth\"\nlabel=\"x\">\n</xf:formrow>",
                "<xf:formrow rowtype=\"fullWidth\"\n\tlabel=\"x\">\n</xf:formrow>",
            )
        } finally {
            html.HTML_ALIGN_ATTRIBUTES = previous
        }
    }

    private fun doTest(before: String, after: String) {
        configureTemplate(before)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        assertEquals(after, myFixture.editor.document.text)
    }
}
