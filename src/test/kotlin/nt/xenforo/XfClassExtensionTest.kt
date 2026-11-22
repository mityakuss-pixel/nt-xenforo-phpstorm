package nt.xenforo

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.IndexingTestUtil
import com.jetbrains.php.PhpIndex
import nt.xenforo.commands.XfAddOns
import nt.xenforo.php.XfClassExtensions
import nt.xenforo.php.XfExtendClass
import nt.xenforo.php.XfProxyLibrary

class XfClassExtensionTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject("src/addons/XF/addon.json", "{\"title\": \"XenForo\"}")
        myFixture.addFileToProject("src/addons/Demo/Test/_output/phrases/demo.txt", "Demo")
        myFixture.addFileToProject("src/XF/Entity/User.php", "<?php\nnamespace XF\\Entity;\n\nclass User\n{\n\tpublic function canView() {}\n}\n")
        myFixture.addFileToProject("src/XF/Pub/Controller/ThreadController.php", "<?php\nnamespace XF\\Pub\\Controller;\n\nclass ThreadController\n{\n\tpublic function actionIndex() {}\n}\n")
    }

    fun testExtensionsFromOutputDataAndClasses() {
        addOutputFile(
            "class_extensions/XF-Entity-User_Demo-Test-XF-Entity-User.json",
            "{\n    \"from_class\": \"XF\\\\Entity\\\\User\",\n    \"to_class\": \"Demo\\\\Test\\\\XF\\\\Entity\\\\User\",\n    \"execute_order\": 10,\n    \"active\": true\n}",
        )
        myFixture.addFileToProject(
            "src/addons/Other/Tools/_data/class_extensions.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<class_extensions>\n  <extension from_class=\"XF\\Entity\\User\" to_class=\"Other\\Tools\\Entity\\Member\" execute_order=\"10\" active=\"1\"/>\n</class_extensions>",
        )
        myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Pub/Controller/ThreadController.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Pub\\Controller;\n\nclass ThreadController extends XFCP_ThreadController\n{\n}\n",
        )
        val extensions = XfClassExtensions.all(project)
        assertEquals("XF\\Entity\\User", extensions["Demo\\Test\\XF\\Entity\\User"])
        assertEquals("XF\\Entity\\User", extensions["Other\\Tools\\Entity\\Member"])
        assertEquals("XF\\Pub\\Controller\\ThreadController", extensions["Demo\\Test\\XF\\Pub\\Controller\\ThreadController"])
    }

    fun testCoreAddOnClassesAreNotExtensions() {
        myFixture.addFileToProject("src/addons/XF/Proxy.php", "<?php\nnamespace XF;\n\nclass Proxy extends XFCP_Proxy\n{\n}\n")
        assertFalse(XfClassExtensions.all(project).containsKey("XF\\Proxy"))
    }

    fun testStubDeclaresProxiesByNamespace() {
        val stub = XfClassExtensions.stub(
            sortedMapOf(
                "Demo\\Test\\XF\\Entity\\User" to "XF\\Entity\\User",
                "Demo\\Test\\XF\\Entity\\Post" to "XF\\Entity\\Post",
                "Global" to "XF\\Entity\\Thread",
            ),
        )
        assertEquals(
            "<?php\n\nnamespace\n{\n\tclass XFCP_Global extends \\XF\\Entity\\Thread\n\t{\n\t}\n}\n" +
                "\nnamespace Demo\\Test\\XF\\Entity\n{\n\tclass XFCP_Post extends \\XF\\Entity\\Post\n\t{\n\t}\n" +
                "\tclass XFCP_User extends \\XF\\Entity\\User\n\t{\n\t}\n}\n",
            stub,
        )
    }

    fun testProxyClassResolvesToBaseClass() {
        myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Entity/User.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Entity;\n\nclass User extends XFCP_User\n{\n}\n",
        )
        XfProxyLibrary.getInstance(project).updateNow()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val index = PhpIndex.getInstance(project)
        val proxy = index.getClassesByFQN("\\Demo\\Test\\XF\\Entity\\XFCP_User").single()
        assertEquals("\\XF\\Entity\\User", proxy.superFQN)
        val extension = index.getClassesByFQN("\\Demo\\Test\\XF\\Entity\\User").single()
        assertNotNull(extension.findMethodByName("canView"))
    }

    fun testProxyFromExtensionHintIsNotDeclaredTwice() {
        myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Entity/User.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Entity;\n\nclass User extends XFCP_User\n{\n}\n",
        )
        myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Pub/Controller/ThreadController.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Pub\\Controller;\n\nclass ThreadController extends XFCP_ThreadController\n{\n}\n",
        )
        addOutputFile(
            "extension_hint.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Entity\n{\n\tclass XFCP_User extends \\XF\\Entity\\User {}\n}\n",
        )
        val stub = XfProxyLibrary.getInstance(project).content()!!
        assertFalse(stub, stub.contains("class XFCP_User "))
        assertTrue(stub, stub.contains("class XFCP_ThreadController extends \\XF\\Pub\\Controller\\ThreadController"))
    }

    fun testExtendWithoutMakeCommandWritesClassAndRegistration() {
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        XfExtendClass.run(project, "XF\\Pub\\Controller\\ThreadController", addOn, 20)
        val classFile = myFixture.findFileInTempDir("src/addons/Demo/Test/XF/Pub/Controller/ThreadController.php")
        assertEquals(
            "<?php\n\nnamespace Demo\\Test\\XF\\Pub\\Controller;\n\nclass ThreadController extends XFCP_ThreadController\n{\n}\n",
            VfsUtilCore.loadText(classFile),
        )
        val registration = myFixture.findFileInTempDir(
            "src/addons/Demo/Test/_output/class_extensions/XF-Pub-Controller-ThreadController_Demo-Test-XF-Pub-Controller-ThreadController.json",
        )
        assertEquals(
            "{\n    \"from_class\": \"XF\\\\Pub\\\\Controller\\\\ThreadController\",\n" +
                "    \"to_class\": \"Demo\\\\Test\\\\XF\\\\Pub\\\\Controller\\\\ThreadController\",\n" +
                "    \"execute_order\": 20,\n    \"active\": true\n}\n",
            VfsUtilCore.loadText(registration),
        )
    }

    fun testExtendIntentionOnCoreClass() {
        myFixture.configureByText("Usage.php", "<?php\n\n\$user = new \\XF\\Entity\\Us<caret>er();\n")
        assertNotEmpty(myFixture.filterAvailableIntentions("Extend class in XenForo add-on"))
    }

    fun testExtendIntentionIsNotOfferedForExtension() {
        val file = myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Entity/User.php",
            "<?php\n\nnamespace Demo\\Test\\XF\\Entity;\n\nclass Us<caret>er extends XFCP_User\n{\n}\n".replace("<caret>", ""),
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("class User") + 8)
        assertEmpty(myFixture.filterAvailableIntentions("Extend class in XenForo add-on"))
    }

    fun testReplyCheckWrapsParentAction() {
        myFixture.configureByText(
            "ThreadController.php",
            "<?php\n\nclass ThreadController extends XFCP_ThreadController\n{\n\tpublic function actionIndex(\$params)\n\t{\n\t\treturn parent::action<caret>Index(\$params);\n\t}\n}\n",
        )
        myFixture.launchAction(myFixture.findSingleIntention("Check that parent reply is a view"))
        val text = myFixture.editor.document.text
        assertTrue(text, text.contains("\$reply = parent::actionIndex(\$params);"))
        assertTrue(text, text.contains("if (\$reply instanceof \\XF\\Mvc\\Reply\\View)"))
        assertTrue(text, text.trimEnd().endsWith("return \$reply;\n\t}\n}"))
        val caretLine = myFixture.editor.document.getLineNumber(myFixture.caretOffset)
        assertTrue(myFixture.editor.document.getText(com.intellij.openapi.util.TextRange(myFixture.editor.document.getLineStartOffset(caretLine - 1), myFixture.editor.document.getLineEndOffset(caretLine - 1))).trim().endsWith("{"))
    }

    fun testReplyCheckOnlyForActions() {
        myFixture.configureByText("Entity.php", "<?php\n\nclass User extends XFCP_User\n{\n\tpublic function getName()\n\t{\n\t\treturn parent::get<caret>Name();\n\t}\n}\n")
        assertEmpty(myFixture.filterAvailableIntentions("Check that parent reply is a view"))
    }
}
