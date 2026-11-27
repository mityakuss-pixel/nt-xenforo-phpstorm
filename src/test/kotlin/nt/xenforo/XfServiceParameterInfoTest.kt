package nt.xenforo

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import nt.xenforo.php.XfConstructorSignature
import nt.xenforo.php.XfServiceParameterInfoHandler

class XfServiceParameterInfoTest : XfTestCase() {
    private var files = 0

    override fun setUp() {
        super.setUp()
        val classes = mapOf(
            "XF/App.php" to "namespace XF;\nclass App {}",
            "XF/Entity/User.php" to "namespace XF\\Entity;\nclass User {}",
            "XF/Service/AbstractService.php" to "namespace XF\\Service;\nabstract class AbstractService { public function __construct(\\XF\\App \$app) {} }",
            "addons/Vendor/AddOn/Service/Creator.php" to "namespace Vendor\\AddOn\\Service;\nuse XF\\App;\nuse XF\\Entity\\User;\n" +
                "class Creator extends \\XF\\Service\\AbstractService\n{\n\tpublic function __construct(App \$app, User \$user, protected bool \$notify = true)\n\t{\n\t\tparent::__construct(\$app);\n\t}\n}",
            "addons/Vendor/AddOn/Service/Plain.php" to "namespace Vendor\\AddOn\\Service;\nclass Plain extends \\XF\\Service\\AbstractService {}",
            "addons/Vendor/AddOn/Helper/Formatter.php" to "namespace Vendor\\AddOn\\Helper;\nclass Formatter { public function __construct(string \$format, int \$limit = 10) {} }",
        )
        for ((path, text) in classes) myFixture.addFileToProject("src/$path", "<?php\n$text\n")
    }

    fun testServiceSkipsInjectedApp() {
        val (signature, current) = parameterInfo("\\XF::service('Vendor\\AddOn:Creator', <caret>)")
        assertEquals(listOf("User \$user", "bool \$notify = true"), signature.parameters)
        assertEquals(0, current)
        val (byClass, second) = parameterInfo("\$this->service(\\Vendor\\AddOn\\Service\\Creator::class, \$user, <caret>)")
        assertEquals(listOf("User \$user", "bool \$notify = true"), byClass.parameters)
        assertEquals(1, second)
    }

    fun testInheritedConstructorAndHelper() {
        assertEquals(emptyList<String>(), parameterInfo("\\XF::app()->service('Vendor\\AddOn:Plain', <caret>)").first.parameters)
        assertEquals(listOf("string \$format", "int \$limit = 10"), parameterInfo("\\XF::helper('Vendor\\AddOn\\Helper\\Formatter', <caret>)").first.parameters)
    }

    fun testHintOpenedBeforeClassSwitchesToConstructor() {
        myFixture.addFileToProject("src/XF.php", "<?php\n\nclass XF\n{\n\tpublic static function service(\$class) {}\n}\n")
        configurePhp("\\XF::service(<caret>)")
        val handler = XfServiceParameterInfoHandler()
        val create = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        assertNotNull(handler.findElementForParameterInfo(create))
        val signature = create.itemsToShow.orEmpty().single() as XfConstructorSignature
        assertEquals(listOf("\$class"), signature.parameters)
        val inserted = "'Vendor\\AddOn:Creator', "
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(myFixture.caretOffset, inserted) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(myFixture.caretOffset + inserted.length)
        val update = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file, arrayOf(signature))
        val owner = handler.findElementForUpdatingParameterInfo(update)
        assertNotNull(owner)
        handler.updateParameterInfo(owner!!, update)
        assertEquals(listOf("User \$user", "bool \$notify = true"), signature.parameters)
        assertEquals(0, update.currentParameter)
    }

    fun testUnknownClassFallsBackToPhpStorm() {
        configurePhp("\\XF::service('Vendor\\AddOn:Missing', <caret>)")
        assertNull(XfServiceParameterInfoHandler().findElementForParameterInfo(MockCreateParameterInfoContext(myFixture.editor, myFixture.file)))
    }

    private fun parameterInfo(call: String): Pair<XfConstructorSignature, Int> {
        configurePhp(call)
        val handler = XfServiceParameterInfoHandler()
        val create = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForParameterInfo(create)
        assertNotNull(call, owner)
        val signature = create.itemsToShow.orEmpty().single() as XfConstructorSignature
        val update = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        handler.updateParameterInfo(owner!!, update)
        return signature to update.currentParameter
    }

    private fun configurePhp(call: String) {
        files++
        val prefix = "<?php\n\nnamespace Vendor\\AddOn\\Pub\\Controller;\n\nclass Demo$files\n{\n\tpublic function actionIndex(\$user)\n\t{\n\t\t\$service = "
        val caret = call.indexOf("<caret>")
        val file = myFixture.addFileToProject("src/addons/Vendor/AddOn/Check/Demo$files.php", prefix + call.replace("<caret>", "") + ";\n\t}\n}\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(prefix.length + caret)
    }
}
