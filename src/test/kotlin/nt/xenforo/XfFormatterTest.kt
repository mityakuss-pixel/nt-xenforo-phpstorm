package nt.xenforo

import com.intellij.openapi.command.WriteCommandAction
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
            """
            <xf:macro id="row" arg-user="!">
            	<xf:textboxrow name="username"
            		label="{{ phrase('user_name') }}" />
            </xf:macro>
            """.trimIndent(),
        )
    }

    private fun doTest(before: String, after: String) {
        configureTemplate(before)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        assertEquals(after, myFixture.editor.document.text)
    }
}
