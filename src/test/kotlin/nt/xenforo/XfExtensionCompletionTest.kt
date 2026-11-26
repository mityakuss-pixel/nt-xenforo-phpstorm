package nt.xenforo

import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.commands.XfCommandSettings

class XfExtensionCompletionTest : XfTestCase() {
    private var files = 0

    override fun setUp() {
        super.setUp()
        val classes = mapOf(
            "XF/Entity/User.php" to "namespace XF\\Entity;\nclass User {}",
            "XF/Entity/UserGroup.php" to "namespace XF\\Entity;\nclass UserGroup {}",
            "addons/Demo/Test/XF/Entity/XFCP_User.php" to "namespace Demo\\Test\\XF\\Entity;\nclass XFCP_User extends \\XF\\Entity\\User {}",
            "addons/Demo/Test/XF/Entity/User.php" to "namespace Demo\\Test\\XF\\Entity;\nclass User extends XFCP_User {}",
        )
        for ((path, text) in classes) myFixture.addFileToProject("src/$path", "<?php\n$text\n")
    }

    fun testExtensionsAreHiddenByDefault() {
        val names = completeClasses("\$user = new \\XF\\Entity\\Us<caret>")
        assertContainsElements(names, "\\XF\\Entity\\User", "\\XF\\Entity\\UserGroup")
        val all = completeClasses("\$user = new Us<caret>")
        assertContainsElements(all, "\\XF\\Entity\\User", "\\XF\\Entity\\UserGroup")
        assertDoesntContain(all, "\\Demo\\Test\\XF\\Entity\\User", "\\Demo\\Test\\XF\\Entity\\XFCP_User")
    }

    fun testSettingShowsExtensions() {
        val settings = XfCommandSettings.getInstance(project)
        settings.hideExtensionClasses = false
        try {
            assertContainsElements(completeClasses("\$user = new Us<caret>"), "\\XF\\Entity\\User", "\\Demo\\Test\\XF\\Entity\\User")
        } finally {
            settings.hideExtensionClasses = true
        }
    }

    fun testProxyIsOfferedAfterExtends() {
        configure("<?php\nnamespace Demo\\Test\\XF\\Entity;\n\nclass Other extends XFCP_Us<caret>\n{\n}\n")
        val lookups = myFixture.completeBasic()
        if (lookups == null) {
            assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("extends XFCP_User"))
        } else {
            assertContainsElements(lookups.mapNotNull { (it.psiElement as? PhpClass)?.name }, "XFCP_User")
        }
    }

    private fun completeClasses(statement: String): List<String> {
        configure("<?php\nnamespace Demo\\Test\\Pub\\Controller;\n\nclass Demo\n{\n\tpublic function actionIndex()\n\t{\n\t\t$statement\n\t}\n}\n")
        return myFixture.completeBasic()?.mapNotNull { (it.psiElement as? PhpClass)?.fqn }.orEmpty()
    }

    private fun configure(text: String) {
        files++
        val caret = text.indexOf("<caret>")
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Check/Check$files.php", text.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(caret)
    }
}
