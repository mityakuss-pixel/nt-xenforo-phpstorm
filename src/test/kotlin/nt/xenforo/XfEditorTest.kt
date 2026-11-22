package nt.xenforo

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction
import com.intellij.openapi.actionSystem.IdeActions

class XfEditorTest : XfTestCase() {
    fun testExpressionBracesAreClosed() {
        configureTemplate("<div>{<caret></div>")
        myFixture.type('{')
        assertEquals("<div>{{  }}</div>", myFixture.editor.document.text)
        assertEquals("<div>{{ ".length, myFixture.editor.caretModel.offset)
    }

    fun testSingleQuoteIsPaired() {
        configureTemplate("{{ phrase(<caret>) }}")
        myFixture.type('\'')
        assertEquals("{{ phrase('') }}", myFixture.editor.document.text)
        assertEquals("{{ phrase('".length, myFixture.editor.caretModel.offset)
    }

    fun testBlockTagIsClosed() {
        configureTemplate("<xf:foreach loop=\"\$items\" value=\"\$item\"<caret>")
        myFixture.type('>')
        assertEquals("<xf:foreach loop=\"\$items\" value=\"\$item\"></xf:foreach>", myFixture.editor.document.text)
    }

    fun testSelfClosingBranchIsNotClosed() {
        configureTemplate("<xf:if is=\"\$a\">a<xf:else /<caret></xf:if>")
        myFixture.type('>')
        assertEquals("<xf:if is=\"\$a\">a<xf:else /></xf:if>", myFixture.editor.document.text)
    }

    fun testTypedElseLinesUpWithIf() {
        configureTemplate("<div>\n\t<xf:if is=\"\$a\">\n\t\t<b>a</b>\n\t\t<xf:else /<caret>\n\t</xf:if>\n</div>")
        myFixture.type('>')
        assertEquals("<div>\n\t<xf:if is=\"\$a\">\n\t\t<b>a</b>\n\t<xf:else />\n\t</xf:if>\n</div>", myFixture.editor.document.text)
    }

    fun testTypedElseIfLinesUpWithIf() {
        configureTemplate("<xf:if is=\"\$a\">\n\t<b>a</b>\n\t<xf:elseif is=\"\$b\" /<caret>\n</xf:if>", "second.html")
        myFixture.type('>')
        assertEquals("<xf:if is=\"\$a\">\n\t<b>a</b>\n<xf:elseif is=\"\$b\" />\n</xf:if>", myFixture.editor.document.text)
    }

    fun testCompletedElseLinesUpWithIf() {
        configureTemplate("<div>\n\t<xf:if is=\"\$a\">\n\t\t<b>a</b>\n\t\t<xf:els<caret>\n\t</xf:if>\n</div>", "third.html")
        val items = myFixture.completeBasic()
        if (items != null) {
            myFixture.lookup.currentItem = items.first { it.lookupString == "else" }
            myFixture.finishLookup('\n')
        }
        assertEquals("<div>\n\t<xf:if is=\"\$a\">\n\t\t<b>a</b>\n\t<xf:else />\n\t</xf:if>\n</div>", myFixture.editor.document.text)
    }

    fun testEnterBetweenTags() {
        configureTemplate("<xf:if is=\"\$a\"><caret></xf:if>")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
        assertEquals("<xf:if is=\"\$a\">\n\t\n</xf:if>", myFixture.editor.document.text)
    }

    fun testLineCommentInHtmlPart() {
        configureTemplate("<div class=\"<caret>row\">{{ \$a }}</div>")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("<xf:comment><div class=\"row\">{{ \$a }}</div></xf:comment>", myFixture.editor.document.text)
    }

    fun testBlockComment() {
        configureTemplate("<div>{{ \$a }}</div>")
        myFixture.editor.selectionModel.setSelection(5, 13)
        CommentByBlockCommentAction().actionPerformedImpl(project, myFixture.editor)
        assertEquals("<div><xf:comment>{{ \$a }}</xf:comment></div>", myFixture.editor.document.text)
    }
}
