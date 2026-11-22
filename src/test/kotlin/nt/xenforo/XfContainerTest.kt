package nt.xenforo

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import nt.xenforo.index.XfSymbolTarget

class XfContainerTest : XfTestCase() {
    private var files = 0

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject(
            "src/XF/Container.php",
            "<?php\nnamespace XF;\n\nclass Container implements \\ArrayAccess\n{\n" +
                "\tpublic function offsetGet(\$key) {}\n\tpublic function offsetSet(\$key, \$value): void {}\n" +
                "\tpublic function offsetExists(\$key): bool {}\n\tpublic function offsetUnset(\$key): void {}\n" +
                "\tpublic function set(\$key, \$value) {}\n\tpublic function extend(\$key, \$callable) {}\n}\n",
        )
        myFixture.addFileToProject(
            "src/XF/App.php",
            "<?php\nnamespace XF;\n\nclass App implements \\ArrayAccess\n{\n\t/** @var Container */\n\tprotected \$container;\n\n" +
                "\tpublic function initialize()\n\t{\n\t\t\$container = \$this->container;\n" +
                "\t\t\$container['router.public'] = function (Container \$c) { return null; };\n" +
                "\t\t\$container['em'] = function (Container \$c) { return \$c['router.public']; };\n\t}\n\n" +
                "\t/** @return Container|mixed */\n\tpublic function container(\$key = null) {}\n" +
                "\tpublic function offsetGet(\$key) {}\n\tpublic function offsetSet(\$key, \$value): void {}\n" +
                "\tpublic function offsetExists(\$key): bool {}\n\tpublic function offsetUnset(\$key): void {}\n}\n",
        )
        myFixture.addFileToProject("src/XF.php", "<?php\n\nclass XF\n{\n\t/** @return \\XF\\App */\n\tpublic static function app() {}\n}\n")
        myFixture.addFileToProject(
            "src/addons/Demo/Test/Listener.php",
            "<?php\nnamespace Demo\\Test;\n\nclass Listener\n{\n\tpublic static function appSetup(\\XF\\App \$app)\n\t{\n" +
                "\t\t\$app->container()->set('demo.service', function () { return null; });\n" +
                "\t\t\$app->container()->extend('em', function (\$em) { return \$em; });\n\t}\n}\n",
        )
    }

    fun testContainerCallOpensDefinitions() {
        configurePhp("\\XF::app()->container('router.pu<caret>blic');")
        assertEquals(listOf("App.php"), targets().map { it.containingFileName() })
        configurePhp("\\XF::app()->container('demo.serv<caret>ice');")
        assertEquals(listOf("Listener.php"), targets().map { it.containingFileName() })
        configurePhp("\\XF::app()->container('e<caret>m');")
        assertSameElements(targets().map { it.containingFileName() }, "App.php", "Listener.php")
    }

    fun testArrayAccessNeedsAppOrContainer() {
        configurePhp("\$app = \\XF::app();\n\t\t\$app['e<caret>m'];")
        assertNotEmpty(targets())
        configurePhp("\$callback = function (\\XF\\Container \$c) { return \$c['router.pu<caret>blic']; };")
        assertNotEmpty(targets())
        configurePhp("\$config = ['em' => 1];\n\t\t\$config['e<caret>m'];")
        assertEmpty(targets())
    }

    fun testContainerKeysAreCompleted() {
        configurePhp("\\XF::app()->container('<caret>');")
        assertContainsElements(myFixture.completeBasic()?.map { it.lookupString }.orEmpty(), "router.public", "em", "demo.service")
    }

    private fun configurePhp(statement: String) {
        files++
        val prefix = "<?php\n\nnamespace Demo\\Test\\Pub\\Controller;\n\nclass Demo$files\n{\n\tpublic function actionIndex()\n\t{\n\t\t"
        val caret = statement.indexOf("<caret>")
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Pub/Controller/Demo$files.php", prefix + statement.replace("<caret>", "") + "\n\t}\n}\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(prefix.length + caret)
    }

    private fun targets(): List<PsiElement> {
        val found = myFixture.file.findReferenceAt(myFixture.caretOffset) ?: return emptyList()
        val references = if (found is PsiMultiReference) found.references.toList() else listOf(found)
        return references.flatMap { reference ->
            if (reference is PsiPolyVariantReference) reference.multiResolve(false).mapNotNull { it.element } else listOfNotNull(reference.resolve())
        }.filterIsInstance<XfSymbolTarget>()
    }

    private fun PsiElement.containingFileName(): String = (this as XfSymbolTarget).locationString.substringAfterLast('/')
}
