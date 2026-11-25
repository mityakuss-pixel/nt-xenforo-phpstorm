package nt.xenforo

import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.codeInsight.editorActions.XmlTagNameSynchronizer
import com.intellij.openapi.actionSystem.IdeActions

class XfTagNameSyncTest : XfTestCase() {
    private val template = "<xf:select name=\"payment\">\n\t<xf:option value=\"1\">One</xf:option>\n</xf:select>"

    fun testTypingInOpeningTagRenamesClosingTag() {
        configureTemplate(template.replaceFirst("select", "sel<caret>ect"))
        myFixture.type("ab")
        assertEquals(template.replace("select", "selabect"), myFixture.editor.document.text)
    }

    fun testReplacingSelectedNameRenamesClosingTag() {
        configureTemplate(template)
        val start = template.indexOf("select")
        myFixture.editor.selectionModel.setSelection(start, start + "select".length)
        myFixture.editor.caretModel.moveToOffset(start + "select".length)
        myFixture.type("radio")
        assertEquals(template.replace("select", "radio"), myFixture.editor.document.text)
    }

    fun testTypingInClosingTagRenamesOpeningTag() {
        configureTemplate(template.replaceFirst("</xf:select>", "</xf:select<caret>>"))
        myFixture.type("s")
        assertEquals(template.replace("select", "selects"), myFixture.editor.document.text)
    }

    fun testErasingAndTypingNewName() {
        configureTemplate(template.replaceFirst("select", "select<caret>"))
        repeat("select".length) { myFixture.type('\b') }
        myFixture.type("radio")
        assertEquals(template.replace("select", "radio"), myFixture.editor.document.text)
    }

    fun testNestedTagsAreNotTouched() {
        configureTemplate(template.replaceFirst("option", "opt<caret>ion"))
        myFixture.type("x")
        assertEquals(template.replace("option", "optxion"), myFixture.editor.document.text)
    }

    fun testUndoRestoresBothTags() {
        configureTemplate(template.replaceFirst("select", "sel<caret>ect"))
        myFixture.type("a")
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(template, myFixture.editor.document.text)
    }

    fun testHtmlTagInTemplateRenamesClosingTag() {
        configureTemplate("<di<caret>v class=\"block\">\n\t<xf:if is=\"\$x\">1</xf:if>\n</div>")
        XmlTagNameSynchronizer.getInstance(project).waitForSynchronizersCreation()
        myFixture.type("x")
        assertEquals("<dixv class=\"block\">\n\t<xf:if is=\"\$x\">1</xf:if>\n</dixv>", myFixture.editor.document.text)
    }

    fun testDisabledSettingKeepsClosingTag() {
        val options = WebEditorOptions.getInstance()
        options.isSyncTagEditing = false
        try {
            configureTemplate(template.replaceFirst("select", "sel<caret>ect"))
            myFixture.type("a")
            assertEquals(template.replaceFirst("select", "selaect"), myFixture.editor.document.text)
        } finally {
            options.isSyncTagEditing = true
        }
    }
}
