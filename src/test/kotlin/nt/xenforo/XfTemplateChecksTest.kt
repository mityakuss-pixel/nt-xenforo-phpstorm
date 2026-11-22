package nt.xenforo

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import nt.xenforo.inspection.XfAdminTemplateInspection
import nt.xenforo.inspection.XfAdminTemplatePhpInspection

class XfTemplateChecksTest : XfTestCase() {
    fun testPhraseNameMustBeLiteral() {
        configureTemplate("{{ phrase(\$name) }}\n{{ phrase('ok') }}\n{{ phrase_dynamic(\$name) }}\n{{ phrase(\"also_ok\") }}\n{{ phrase(\$xf.options.boardTitle) }}")
        assertEquals(listOf("\$name", "\$xf.options.boardTitle"), highlighted(XfBundle.message("annotator.phrase.name.literal")))
    }

    fun testPhraseDynamicFix() {
        configureTemplate("{{ phrase(\$na<caret>me, {'count': 2}) }}")
        val fix = myFixture.findSingleIntention(XfBundle.message("annotator.phrase.dynamic.fix"))
        ShowIntentionActionsHandler.chooseActionAndInvoke(myFixture.file, myFixture.editor, fix, fix.text)
        assertEquals("{{ phrase_dynamic(\$name, {'count': 2}) }}", myFixture.editor.document.text)
    }

    fun testAdminTemplateInPublicTemplate() {
        myFixture.enableInspections(XfAdminTemplateInspection::class.java)
        configureTemplate(
            "<xf:include template=\"admin:helper_criteria\" />\n<xf:include template=\"public:helper_action\" />\n" +
                "<xf:macro id=\"admin:helper_macros::row\" />\n<xf:macro id=\"helper_macros::row\" />",
        )
        assertEquals(listOf("admin:helper_criteria", "admin:helper_macros::row"), highlighted(ADMIN_MESSAGE))
        configureTemplate("<xf:include template=\"admin:helper_criteria\" />", "second.html", "admin")
        assertEmpty(highlighted(ADMIN_MESSAGE))
    }

    fun testAdminTemplateInPublicController() {
        myFixture.enableInspections(XfAdminTemplatePhpInspection::class.java)
        configurePhp("Pub")
        assertEquals(listOf("admin:demo_list", "admin:demo_row"), highlighted(ADMIN_MESSAGE))
        configurePhp("Admin")
        assertEmpty(highlighted(ADMIN_MESSAGE))
    }

    private fun configurePhp(section: String) {
        val file = myFixture.addFileToProject(
            "src/addons/Demo/Test/$section/Controller/Demo.php",
            "<?php\nnamespace Demo\\Test\\$section\\Controller;\n\nclass Demo\n{\n\tpublic function actionIndex()\n\t{\n" +
                "\t\t\$this->view('Demo\\Test:View', 'admin:demo_list');\n\t\t\$this->view('Demo\\Test:View', 'demo_list');\n" +
                "\t\t\\XF::app()->templater()->renderTemplate('admin:demo_row');\n\t\t\\XF::app()->templater()->renderTemplate('public:demo_row');\n\t}\n}\n",
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    private fun highlighted(description: String): List<String> =
        myFixture.doHighlighting().filter { it.description == description }.sortedBy { it.startOffset }.map { it.text }

    private companion object {
        val ADMIN_MESSAGE: String = XfBundle.message("inspection.admin.template.message")
    }
}
