package nt.xenforo

import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocProperty
import nt.xenforo.documentation.XfDocumentationTarget
import nt.xenforo.documentation.XfDocumentationTargetProvider
import nt.xenforo.php.XfTemplateParams
import nt.xenforo.reference.XfMemberReference

class XfTemplateParamsTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject("src/XF/Mvc/Entity/Entity.php", "<?php\nnamespace XF\\Mvc\\Entity;\n\nabstract class Entity\n{\n}\n")
        myFixture.addFileToProject(
            "src/XF/Entity/Thread.php",
            "<?php\nnamespace XF\\Entity;\n\n/**\n * @property int \$thread_id\n * @property string \$title\n */\nclass Thread extends \\XF\\Mvc\\Entity\\Entity\n{\n\tpublic function canReply() { return true; }\n}\n",
        )
        myFixture.addFileToProject(
            "src/addons/Demo/Test/Pub/Controller/DemoController.php",
            "<?php\nnamespace Demo\\Test\\Pub\\Controller;\n\nuse XF\\Entity\\Thread;\n\nclass DemoController\n{\n" +
                "\tpublic function actionView()\n\t{\n\t\t\$thread = new Thread();\n" +
                "\t\t\$viewParams = ['thread' => \$thread, 'count' => 5];\n\t\t\$viewParams['extra'] = new Thread();\n" +
                "\t\treturn \$this->view('Demo:View', 'demo_view', \$viewParams);\n\t}\n\n" +
                "\tpublic function actionList()\n\t{\n\t\treturn \$this->view('Demo:List', 'public:demo_view', ['items' => []]);\n\t}\n}\n",
        )
        myFixture.addFileToProject(
            "src/addons/Demo/Test/Admin/Controller/DemoController.php",
            "<?php\nnamespace Demo\\Test\\Admin\\Controller;\n\nclass DemoController\n{\n\tpublic function actionIndex()\n\t{\n\t\treturn \$this->view('', 'demo_view', ['adminOnly' => 1]);\n\t}\n}\n",
        )
    }

    fun testParametersFromAllControllers() {
        configureTemplate("<div></div>", "demo_view.html")
        assertEquals(setOf("thread", "count", "extra", "items"), XfTemplateParams.of(myFixture.file).keys)
    }

    fun testVariableCompletionShowsParameters() {
        configureTemplate("{\$<caret>}", "demo_view.html")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "thread", "count", "extra", "items", "xf")
        assertDoesntContain(variants, "adminOnly")
    }

    fun testMembersOfEntityParameter() {
        configureTemplate("{\$thread.<caret>}", "demo_view.html")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "title", "thread_id", "canReply")
    }

    fun testParameterFieldResolvesToProperty() {
        configureTemplate("{\$extra.ti<caret>tle}", "demo_view.html")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as XfMemberReference
        assertInstanceOf(reference.resolve(), PhpDocProperty::class.java)
    }

    fun testDocumentationNamesControllers() {
        configureTemplate("{\$thr<caret>ead.title}", "demo_view.html")
        val target = XfDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single() as XfDocumentationTarget
        val html = target.html()
        assertTrue(html, html.contains("Thread"))
        assertTrue(html, html.contains("Demo\\Test\\Pub\\Controller\\DemoController::actionView()"))
        assertTrue(html, html.contains("actionList()"))
    }

    fun testGutterLinksToControllers() {
        configureTemplate("<div>\n</div>", "demo_view.html")
        val gutter = myFixture.findAllGutters().single { it.tooltipText?.contains("Rendered by") == true }
        assertTrue(gutter.tooltipText!!.contains("actionView()"))
    }

    fun testAdminTemplateUsesAdminControllers() {
        configureTemplate("<div></div>", "demo_view.html", "admin")
        assertEquals(setOf("adminOnly"), XfTemplateParams.of(myFixture.file).keys)
    }
}
