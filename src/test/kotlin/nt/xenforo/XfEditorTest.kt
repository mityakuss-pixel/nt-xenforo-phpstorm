package nt.xenforo

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.search.PsiTodoSearchHelper
import nt.xenforo.editor.XfTypedHandler

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

    fun testUncommentBlock() {
        configureTemplate("<div><xf:comment>{{ \$a }}</xf:comment></div>")
        myFixture.editor.caretModel.moveToOffset(20)
        CommentByBlockCommentAction().actionPerformedImpl(project, myFixture.editor)
        assertEquals("<div>{{ \$a }}</div>", myFixture.editor.document.text)
    }

    fun testTodoStopsBeforeClosingCommentTag() {
        val file = configureTemplate("<xf:comment>TODO check this</xf:comment>\n<div></div>\n<xf:comment>\n\tTODO second\n</xf:comment>")
        val todos = PsiTodoSearchHelper.getInstance(project).findTodoItems(file).map { it.textRange.substring(file.text) }.sorted()
        assertEquals(listOf("TODO check this", "TODO second"), todos)
    }

    fun testAutoPopupInExpressions() {
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("{{ <caret> }}", '$'))
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("{{ \$a ~ <caret> }}", '$'))
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("{<caret>}", '$'))
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("{{ \$xf<caret> }}", '.'))
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("<xf:if is=\"<caret>\"></xf:if>", '$'))
        assertEquals(TypedHandlerDelegate.Result.STOP, autoPopup("<xf:if is=\"\$a && <caret>\"></xf:if>", '$'))
    }

    private fun autoPopup(text: String, char: Char): TypedHandlerDelegate.Result {
        configureTemplate(text, "popup${text.hashCode().toUInt()}.html")
        return XfTypedHandler().checkAutoPopup(char, project, myFixture.editor, myFixture.file)
    }

    fun testEnterInsideCommentKeepsSingleClosingTag() {
        configureTemplate("<xf:comment>note<caret></xf:comment>")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
        assertEquals(1, Regex("</xf:comment>").findAll(myFixture.editor.document.text).count())
    }
}
