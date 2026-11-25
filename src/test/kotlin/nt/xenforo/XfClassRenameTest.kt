package nt.xenforo

import com.intellij.psi.PsiDocumentManager
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.php.XfClassRename

class XfClassRenameTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        val classes = mapOf(
            "XF/Mvc/Entity/Repository.php" to "namespace XF\\Mvc\\Entity;\nclass Repository {}",
            "XF/Service/AbstractService.php" to "namespace XF\\Service;\nabstract class AbstractService {}",
            "XF/Repository/UserRepository.php" to "namespace XF\\Repository;\nclass UserRepository extends \\XF\\Mvc\\Entity\\Repository {}",
            "addons/Demo/Test/Service/UserStyle/Applyer.php" to "namespace Demo\\Test\\Service\\UserStyle;\nclass Applyer extends \\XF\\Service\\AbstractService {}",
            "addons/Demo/Test/XF/Entity/User.php" to "namespace Demo\\Test\\XF\\Entity;\nclass User {}",
            "addons/Demo/Test/Callback.php" to "namespace Demo\\Test;\nclass Callback { public static function render() {} }",
        )
        for ((path, text) in classes) myFixture.addFileToProject("src/$path", "<?php\n$text\n")
    }

    fun testLastSegmentOnly() {
        assertEquals("Demo\\Test:UserStyle\\Applier", XfClassRename.renameLastSegment("Demo\\Test:UserStyle\\Applyer", "Applier"))
        assertEquals("XF:Member", XfClassRename.renameLastSegment("XF:User", "MemberRepository", listOf("Repository")))
        assertEquals("XF:MemberRepository", XfClassRename.renameLastSegment("XF:UserRepository", "MemberRepository", listOf("Repository")))
        assertEquals("XF\\\\Entity\\\\Member", XfClassRename.renameLastSegment("XF\\\\Entity\\\\User", "Member"))
    }

    fun testShortNamesInPhpKeepPrefix() {
        val controller = myFixture.addFileToProject(
            "src/addons/Demo/Test/Pub/Controller/Style.php",
            "<?php\nnamespace Demo\\Test\\Pub\\Controller;\n\nclass Style\n{\n\tpublic function actionIndex()\n\t{\n" +
                "\t\t\\XF::service('Demo\\Test:UserStyle\\Applyer', null);\n\t\t\\XF::repository('XF:User');\n\t}\n}\n",
        )
        myFixture.renameElement(phpClass("\\Demo\\Test\\Service\\UserStyle\\Applyer"), "Applier")
        myFixture.renameElement(phpClass("\\XF\\Repository\\UserRepository"), "MemberRepository")
        val text = PsiDocumentManager.getInstance(project).getDocument(controller)!!.text
        assertTrue(text, text.contains("\\XF::service('Demo\\Test:UserStyle\\Applier', null);"))
        assertTrue(text, text.contains("\\XF::repository('XF:Member');"))
    }

    fun testClassNamesInOutputJsonAndTemplatesKeepNamespace() {
        val json = myFixture.addFileToProject(
            "src/addons/Demo/Test/_output/class_extensions/XF-Entity-User_Demo-Test-XF-Entity-User.json",
            "{\n    \"from_class\": \"XF\\\\Entity\\\\User\",\n    \"to_class\": \"Demo\\\\Test\\\\XF\\\\Entity\\\\User\",\n    \"execute_order\": 10,\n    \"active\": true\n}",
        )
        val template = configureTemplate("<xf:callback class=\"Demo\\Test\\Callback\" method=\"render\" />")
        myFixture.renameElement(phpClass("\\Demo\\Test\\XF\\Entity\\User"), "Member")
        myFixture.renameElement(phpClass("\\Demo\\Test\\Callback"), "Callbacks")
        val documents = PsiDocumentManager.getInstance(project)
        assertTrue(documents.getDocument(json)!!.text, documents.getDocument(json)!!.text.contains("\"to_class\": \"Demo\\\\Test\\\\XF\\\\Entity\\\\Member\""))
        assertTrue(documents.getDocument(json)!!.text, documents.getDocument(json)!!.text.contains("\"from_class\": \"XF\\\\Entity\\\\User\""))
        assertEquals("<xf:callback class=\"Demo\\Test\\Callbacks\" method=\"render\" />", documents.getDocument(template)!!.text)
    }

    private fun phpClass(fqn: String): PhpClass = PhpIndex.getInstance(project).getClassesByFQN(fqn).single()
}
