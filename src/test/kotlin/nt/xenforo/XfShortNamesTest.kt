package nt.xenforo

import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.php.XfShortKind
import nt.xenforo.php.XfShortNameReference
import nt.xenforo.php.XfShortNames

class XfShortNamesTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        val classes = mapOf(
            "XF/Mvc/Entity/Entity.php" to "namespace XF\\Mvc\\Entity;\nabstract class Entity {}",
            "XF/Mvc/Entity/Finder.php" to "namespace XF\\Mvc\\Entity;\nclass Finder {}",
            "XF/Mvc/Entity/Repository.php" to "namespace XF\\Mvc\\Entity;\nclass Repository {}",
            "XF/Mvc/Entity/Behavior.php" to "namespace XF\\Mvc\\Entity;\nabstract class Behavior {}",
            "XF/Service/AbstractService.php" to "namespace XF\\Service;\nabstract class AbstractService {}",
            "XF/ControllerPlugin/AbstractPlugin.php" to "namespace XF\\ControllerPlugin;\nabstract class AbstractPlugin {}",
            "XF/Job/AbstractJob.php" to "namespace XF\\Job;\nabstract class AbstractJob {}",
            "XF/Mvc/View.php" to "namespace XF\\Mvc;\nclass View {}",
            "XF/Entity/User.php" to "namespace XF\\Entity;\nclass User extends \\XF\\Mvc\\Entity\\Entity {}",
            "XF/Finder/UserFinder.php" to "namespace XF\\Finder;\nclass UserFinder extends \\XF\\Mvc\\Entity\\Finder {}",
            "XF/Repository/UserRepository.php" to "namespace XF\\Repository;\nclass UserRepository extends \\XF\\Mvc\\Entity\\Repository {}",
            "XF/Service/Thread/CreatorService.php" to "namespace XF\\Service\\Thread;\nclass CreatorService extends \\XF\\Service\\AbstractService {}",
            "XF/ControllerPlugin/LoginPlugin.php" to "namespace XF\\ControllerPlugin;\nclass LoginPlugin extends AbstractPlugin {}",
            "XF/Pub/View/Thread/View.php" to "namespace XF\\Pub\\View\\Thread;\nclass View extends \\XF\\Mvc\\View {}",
            "XF/Admin/View/Thread/View.php" to "namespace XF\\Admin\\View\\Thread;\nclass View extends \\XF\\Mvc\\View {}",
            "XF/Job/UserRenameCleanUp.php" to "namespace XF\\Job;\nclass UserRenameCleanUp extends AbstractJob {}",
            "XF/Behavior/ChangeLoggable.php" to "namespace XF\\Behavior;\nclass ChangeLoggable extends \\XF\\Mvc\\Entity\\Behavior {}",
            "addons/Demo/Test/Entity/Note.php" to "namespace Demo\\Test\\Entity;\nclass Note extends \\XF\\Mvc\\Entity\\Entity {}",
        )
        for ((path, text) in classes) myFixture.addFileToProject("src/$path", "<?php\n$text\n")
    }

    fun testFactoriesResolveWithSuffixes() {
        assertEquals("\\XF\\Finder\\UserFinder", resolved("\\XF::finder('XF:Us<caret>er');"))
        assertEquals("\\Demo\\Test\\Entity\\Note", resolved("\$this->em()->create('Demo\\Test:No<caret>te');"))
        assertEquals("\\XF\\Repository\\UserRepository", resolved("\\XF::repository('XF:Us<caret>er');"))
        assertEquals("\\XF\\Service\\Thread\\CreatorService", resolved("\$this->service('XF:Thread\\Cre<caret>ator', \$forum);"))
        assertEquals("\\XF\\ControllerPlugin\\LoginPlugin", resolved("\$this->plugin('XF:Lo<caret>gin');"))
        assertEquals("\\XF\\Job\\UserRenameCleanUp", resolved("\\XF::app()->jobManager()->enqueueUnique('rename', 'XF:UserRename<caret>CleanUp', []);"))
    }

    fun testViewUsesControllerSection() {
        assertEquals("\\XF\\Pub\\View\\Thread\\View", resolved("\$this->view('XF:Thread\\Vi<caret>ew', 'thread_view');"))
        assertEquals("\\XF\\Admin\\View\\Thread\\View", resolved("\$this->view('XF:Thread\\Vi<caret>ew', 'thread_view');", "Demo\\Test\\Admin\\Controller"))
    }

    fun testStructureArrays() {
        assertEquals("\\XF\\Entity\\User", resolved("\$structure->relations = ['User' => ['entity' => 'XF:Us<caret>er', 'type' => 1]];"))
        assertEquals("\\XF\\Behavior\\ChangeLoggable", resolved("\$structure->behaviors = ['XF:ChangeLog<caret>gable' => []];"))
        assertEquals("\\XF\\Entity\\User", resolved("\$structure->shortName = 'XF:Us<caret>er';"))
    }

    fun testShortNameFromClass() {
        assertEquals("XF:User", XfShortNames.shortName("\\XF\\Repository\\UserRepository", XfShortKind.REPOSITORY, "Pub"))
        assertEquals("XF:Thread\\View", XfShortNames.shortName("XF\\Pub\\View\\Thread\\View", XfShortKind.VIEW, "Pub"))
        assertEquals("Demo\\Test:Note", XfShortNames.shortName("Demo\\Test\\Entity\\Note", XfShortKind.ENTITY, "Pub"))
    }

    fun testCompletion() {
        configurePhp("\\XF::repository('XF:<caret>');")
        val variants = myFixture.completeBasic()?.map { it.lookupString }
        if (variants == null) assertTrue(myFixture.editor.document.text.contains("'XF:User'")) else assertContainsElements(variants, "XF:User")
        configurePhp("\$this->em()->create('<caret>');")
        assertContainsElements(myFixture.completeBasic()?.map { it.lookupString }.orEmpty(), "XF:User", "Demo\\Test:Note")
    }

    fun testCompletionSkipsExtensionsAndProxies() {
        myFixture.addFileToProject("src/addons/Vendor/Demo/XF/Entity/XFCP_User.php", "<?php\nnamespace Vendor\\Demo\\XF\\Entity;\nclass XFCP_User extends \\XF\\Entity\\User {}\n")
        myFixture.addFileToProject("src/addons/Vendor/Demo/XF/Entity/User.php", "<?php\nnamespace Vendor\\Demo\\XF\\Entity;\nclass User extends XFCP_User {}\n")
        configurePhp("\$this->em()->create('<caret>');")
        val hidden = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(hidden, "XF:User", "Demo\\Test:Note")
        assertDoesntContain(hidden, "Vendor\\Demo\\XF:User", "Vendor\\Demo\\XF:XFCP_User")
        val settings = nt.xenforo.commands.XfCommandSettings.getInstance(project)
        settings.hideExtensionClasses = false
        try {
            configurePhp("\$this->em()->create('<caret>');")
            val shown = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
            assertContainsElements(shown, "Vendor\\Demo\\XF:User")
            assertDoesntContain(shown, "Vendor\\Demo\\XF:XFCP_User")
        } finally {
            settings.hideExtensionClasses = true
        }
    }

    fun testShortNameToClassIntention() {
        val cases = listOf(
            "\\XF::em()->create('XF:Us<caret>er');" to "\\XF::em()->create(\\XF\\Entity\\User::class);",
            "\\XF::finder('XF:Us<caret>er');" to "\\XF::finder(\\XF\\Finder\\UserFinder::class);",
            "\\XF::repository('XF:Us<caret>er');" to "\\XF::repository(\\XF\\Repository\\UserRepository::class);",
            "\$structure->relations = ['User' => ['entity' => 'XF:Us<caret>er']];" to "\$structure->relations = ['User' => ['entity' => \\XF\\Entity\\User::class]];",
        )
        for ((source, expected) in cases) {
            configurePhp(source)
            val intention = myFixture.availableIntentions.singleOrNull { it.familyName == XfBundle.message("intention.short.name.family") }
            assertNotNull(source, intention)
            myFixture.launchAction(intention!!)
            assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains(expected))
        }
    }

    fun testShortNameToClassIntentionKeepsShortNamesWhereXenForoDoes() {
        for (source in listOf(
            "\$this->view('XF:Thread\\Vi<caret>ew', 'thread_view');",
            "\$structure->behaviors = ['XF:ChangeLog<caret>gable' => []];",
            "\$structure->shortName = 'XF:Us<caret>er';",
            "\\XF::em()->create('XF:Miss<caret>ing');",
        )) {
            configurePhp(source)
            assertEmpty(source, myFixture.availableIntentions.filter { it.familyName == XfBundle.message("intention.short.name.family") })
        }
    }

    private var files = 0

    private fun resolved(statement: String, namespace: String = "Demo\\Test\\Pub\\Controller"): String? {
        configurePhp(statement, namespace)
        val found = myFixture.file.findReferenceAt(myFixture.caretOffset)
        val references = if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)
        val reference = references.filterIsInstance<XfShortNameReference>().singleOrNull() ?: return null
        return (reference.multiResolve(false).firstOrNull()?.element as? PhpClass)?.fqn
    }

    private fun configurePhp(statement: String, namespace: String = "Demo\\Test\\Pub\\Controller") {
        files++
        val prefix = "<?php\n\nnamespace $namespace;\n\nclass Demo$files\n{\n\tpublic function actionIndex()\n\t{\n\t\t"
        val caret = statement.indexOf("<caret>")
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Check/Demo$files.php", prefix + statement.replace("<caret>", "") + "\n\t}\n}\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(prefix.length + caret)
    }
}
