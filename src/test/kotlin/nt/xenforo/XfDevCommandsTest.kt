package nt.xenforo

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import nt.xenforo.commands.XfAddOns
import nt.xenforo.commands.XfEntityPropertiesAction
import nt.xenforo.commands.XfLintFilter
import nt.xenforo.commands.XfLintTemplatesAction
import nt.xenforo.commands.XfPhpStormMetaAction
import nt.xenforo.commands.XfMakeCommand
import nt.xenforo.commands.XfMakeCommands
import nt.xenforo.commands.XfSetupStepLineMarker
import nt.xenforo.commands.XfSetupSteps

class XfDevCommandsTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        addOutputFile("templates/public/demo_page.html", "<div>\n<xf:foo />\n</div>")
        addOutputFile("templates/admin/demo_setup.less", ".a {}")
    }

    fun testLintErrorsLinkToTemplates() {
        val root = myFixture.findFileInTempDir("src").parent
        val filter = XfLintFilter(project, root)
        val line = "[Demo/Test] public:demo_page: Line 2: Unknown tag xf:foo encountered.\n"
        val result = filter.applyFilter(line, 100 + line.length)!!
        val item = result.resultItems.single()
        assertEquals("public:demo_page", line.substring(item.highlightStartOffset - 100, item.highlightEndOffset - 100))
        val info = item.hyperlinkInfo as OpenFileHyperlinkInfo
        assertEquals("demo_page.html", info.virtualFile?.name)
        assertEquals(6, info.descriptor?.offset)
        val less = filter.applyFilter("[Demo/Test] admin:demo_setup.less: Line 1: error\n", 60)!!
        assertEquals("demo_setup.less", (less.resultItems.single().hyperlinkInfo as OpenFileHyperlinkInfo).virtualFile?.name)
        assertNull(filter.applyFilter("Linted Demo/Test.\n", 20))
    }

    fun testSetupStepCommands() {
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        assertNull(XfSetupSteps.command(addOn, "installStep1"))
        for (name in listOf("InstallStep", "UpgradeStep", "UninstallStep")) myFixture.addFileToProject("src/XF/Cli/Command/AddOn/$name.php", "<?php\n")
        assertEquals(listOf("xf-addon:install-step", "Demo/Test", "3"), XfSetupSteps.command(addOn, "installStep3"))
        assertEquals(listOf("xf-addon:upgrade-step", "Demo/Test", "1000170", "2"), XfSetupSteps.command(addOn, "upgrade1000170Step2"))
        assertEquals(listOf("xf-addon:uninstall-step", "Demo/Test", "1"), XfSetupSteps.command(addOn, "uninstallStep1"))
        assertNull(XfSetupSteps.command(addOn, "install"))
        val setup = myFixture.addFileToProject(
            "src/addons/Demo/Test/Setup.php",
            "<?php\nnamespace Demo\\Test;\n\nclass Setup\n{\n\tpublic function installStep1() {}\n\tpublic function helper() {}\n}\n",
        )
        myFixture.configureFromExistingVirtualFile(setup.virtualFile)
        val marker = XfSetupStepLineMarker()
        val names = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.file, com.jetbrains.php.lang.psi.elements.Method::class.java)
            .associate { it.name to (it.nameIdentifier?.let(marker::getInfo) != null) }
        assertEquals(mapOf("installStep1" to true, "helper" to false), names)
    }

    fun testMakeCommandsAreReadFromXenForo() {
        myFixture.addFileToProject(
            "src/XF/Cli/Command/Make/EntityMakeCommand.php",
            "<?php\nclass EntityMakeCommand {\n\tfunction configure() { \$this->setName('xf-make:entity')->setDescription('Create an entity'); }\n}\n",
        )
        val root = myFixture.findFileInTempDir("src").parent
        assertEquals(listOf(XfMakeCommand("xf-make:entity", "Create an entity")), XfMakeCommands.list(root))
    }

    fun testEntityPropertiesTargetSingleEntity() {
        myFixture.addFileToProject("src/XF/Mvc/Entity/Entity.php", "<?php\nnamespace XF\\Mvc\\Entity;\n\nabstract class Entity\n{\n}\n")
        val entity = myFixture.addFileToProject(
            "src/addons/Demo/Test/Entity/Sub/Guild.php",
            "<?php\nnamespace Demo\\Test\\Entity\\Sub;\n\nclass Guild extends \\XF\\Mvc\\Entity\\Entity\n{\n}\n",
        )
        val repository = myFixture.addFileToProject(
            "src/addons/Demo/Test/Repository/GuildRepository.php",
            "<?php\nnamespace Demo\\Test\\Repository;\n\nclass GuildRepository\n{\n}\n",
        )
        val extension = myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Entity/User.php",
            "<?php\nnamespace Demo\\Test\\XF\\Entity;\n\nclass User extends XFCP_User\n{\n}\n",
        )
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        assertEquals("Demo\\Test:Sub\\Guild", XfEntityPropertiesAction.entityShortName(addOn, entity))
        assertNull(XfEntityPropertiesAction.entityShortName(addOn, repository))
        assertNull(XfEntityPropertiesAction.entityShortName(addOn, extension))
    }

    fun testCommandsNeedTheirXenForoClass() {
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        assertFalse(XfLintTemplatesAction().isAvailable(addOn))
        myFixture.addFileToProject("src/XF/Cli/Command/Development/LintTemplates.php", "<?php\n")
        assertTrue(XfLintTemplatesAction().isAvailable(addOn))
        assertFalse(XfPhpStormMetaAction().isAvailable(addOn))
    }
}
