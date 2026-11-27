package nt.xenforo

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import nt.xenforo.reference.XfMacroRenameHandler

class XfMacroRenameTest : XfTestCase() {
    fun testRenameLegacyNameUpdatesAllCalls() {
        addOutputFile("templates/public/demo_use.html", "<xf:macro id=\"test::style_panel\" />\n<xf:macro template=\"test\" name=\"style_panel\" />\n<xf:macro id=\"other::style_panel\" />")
        addOutputFile("templates/public/other.html", "<xf:macro id=\"style_panel\"></xf:macro>")
        val php = myFixture.addFileToProject("src/addons/Demo/Test/Pub/Controller/Demo.php", "<?php\nclass Demo { function a(\$t) { \$t->renderMacro('public:test', 'style_panel', []); } }\n")
        configureTemplate("<xf:macro name=\"style_pa<caret>nel\" arg-userStyle=\"!\">\n\t<div></div>\n</xf:macro>\n<xf:macro name=\"style_panel\" arg-userStyle=\"{\$style}\" />")
        myFixture.renameElementAtCaretUsingHandler("panel_style")
        assertEquals("<xf:macro name=\"panel_style\" arg-userStyle=\"!\">\n\t<div></div>\n</xf:macro>\n<xf:macro name=\"panel_style\" arg-userStyle=\"{\$style}\" />", myFixture.editor.document.text)
        assertEquals("<xf:macro id=\"test::panel_style\" />\n<xf:macro template=\"test\" name=\"panel_style\" />\n<xf:macro id=\"other::style_panel\" />", text("templates/public/demo_use.html"))
        assertEquals("<xf:macro id=\"style_panel\"></xf:macro>", text("templates/public/other.html"))
        assertTrue(php.text, php.text.contains("renderMacro('public:test', 'panel_style', [])"))
    }

    fun testRenameFromCallSite() {
        addOutputFile("templates/public/demo_page.html", "<xf:macro id=\"row\" arg-item=\"!\"></xf:macro>")
        configureTemplate("<xf:macro id=\"demo_page::r<caret>ow\" arg-item=\"{\$item}\" />")
        myFixture.renameElementAtCaretUsingHandler("item_row")
        assertEquals("<xf:macro id=\"demo_page::item_row\" arg-item=\"{\$item}\" />", myFixture.editor.document.text)
        assertEquals("<xf:macro id=\"item_row\" arg-item=\"!\"></xf:macro>", text("templates/public/demo_page.html"))
    }

    fun testNotOfferedForCoreDataTemplates() {
        addDataFile("templates.xml", "<templates><template type=\"public\" title=\"core_page\" version_id=\"1\" version_string=\"1\"><![CDATA[<xf:macro id=\"core_row\"></xf:macro>]]></template></templates>")
        configureTemplate("<xf:macro id=\"core_page::core_r<caret>ow\" />")
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        assertFalse(XfMacroRenameHandler().isAvailableOnDataContext(context))
    }

    private fun text(path: String): String =
        FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir("src/addons/Demo/Test/_output/$path"))!!.text
}
