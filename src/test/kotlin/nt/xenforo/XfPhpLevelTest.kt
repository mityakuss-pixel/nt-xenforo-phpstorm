package nt.xenforo

import com.intellij.ide.util.PropertiesComponent
import com.jetbrains.php.config.PhpLanguageLevel
import com.jetbrains.php.config.PhpProjectConfigurationFacade
import nt.xenforo.commands.XfAddOns
import nt.xenforo.commands.XfCommandSettings
import nt.xenforo.php.DISMISSED_ADD_ONS
import nt.xenforo.php.HIDDEN_ADD_ONS
import nt.xenforo.php.XfAddOnPhpLevel
import nt.xenforo.php.XfPhpLevelNotificationProvider

class XfPhpLevelTest : XfTestCase() {
    private lateinit var initial: PhpLanguageLevel

    override fun setUp() {
        super.setUp()
        initial = PhpProjectConfigurationFacade.getInstance(project).languageLevel
        myFixture.addFileToProject(
            "src/addons/Demo/Test/addon.json",
            "{\n    \"title\": \"Demo\",\n    \"require\": {\n        \"php\": [\"7.4\", \"PHP 7.4+\"],\n        \"XF\": [2020070, \"XenForo 2.2.0+\"]\n    }\n}",
        )
        myFixture.addFileToProject("src/addons/Demo/Plain/addon.json", "{\"title\": \"Plain\"}")
    }

    override fun tearDown() {
        try {
            PhpProjectConfigurationFacade.getInstance(project).languageLevel = initial
            PropertiesComponent.getInstance(project).setList(HIDDEN_ADD_ONS, null)
            project.putUserData(DISMISSED_ADD_ONS, null)
        } finally {
            super.tearDown()
        }
    }

    fun testLevelFromVersionString() {
        assertEquals(PhpLanguageLevel.PHP740, XfAddOnPhpLevel.level("7.4"))
        assertEquals(PhpLanguageLevel.PHP810, XfAddOnPhpLevel.level("8.1.0"))
        assertNull(XfAddOnPhpLevel.level("next"))
        assertEquals(PhpLanguageLevel.PHP740, XfAddOnPhpLevel.requiredIn("{\"require\": {\"php\": [\"7.4\", \"PHP 7.4+\"]}}"))
        assertNull(XfAddOnPhpLevel.requiredIn("{\"require\": {\"XF\": [2020070, \"XenForo\"]}}"))
        assertNull(XfAddOnPhpLevel.requiredIn("not json"))
    }

    fun testBannerFollowsProjectLevel() {
        val root = myFixture.findFileInTempDir("src").parent
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", "<?php\n").virtualFile
        val plain = myFixture.addFileToProject("src/addons/Demo/Plain/Listener.php", "<?php\n").virtualFile
        val provider = XfPhpLevelNotificationProvider()
        PhpProjectConfigurationFacade.getInstance(project).languageLevel = PhpLanguageLevel.PHP840
        assertNotNull(provider.collectNotificationData(project, file))
        assertNull(provider.collectNotificationData(project, plain))
        XfAddOnPhpLevel.apply(project, PhpLanguageLevel.PHP740)
        assertEquals(PhpLanguageLevel.PHP740, XfAddOnPhpLevel.current(project))
        assertNull(provider.collectNotificationData(project, file))
        PhpProjectConfigurationFacade.getInstance(project).languageLevel = PhpLanguageLevel.PHP840
        XfAddOnPhpLevel.hide(project, XfAddOns.find(file, root)!!)
        assertNull(provider.collectNotificationData(project, file))
    }

    fun testCloseDismissesUntilRestart() {
        val root = myFixture.findFileInTempDir("src").parent
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Repository.php", "<?php\n").virtualFile
        val provider = XfPhpLevelNotificationProvider()
        PhpProjectConfigurationFacade.getInstance(project).languageLevel = PhpLanguageLevel.PHP840
        assertNotNull(provider.collectNotificationData(project, file))
        val addOn = XfAddOns.find(file, root)!!
        XfAddOnPhpLevel.dismiss(project, addOn)
        assertNull(provider.collectNotificationData(project, file))
        assertFalse(XfAddOnPhpLevel.isHidden(project, addOn))
    }

    fun testFollowSettingSwitchesLevel() {
        val settings = XfCommandSettings.getInstance(project)
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Setup.php", "<?php\n").virtualFile
        val plain = myFixture.addFileToProject("src/addons/Demo/Plain/Setup.php", "<?php\n").virtualFile
        try {
            PhpProjectConfigurationFacade.getInstance(project).languageLevel = PhpLanguageLevel.PHP840
            XfAddOnPhpLevel.follow(project, file)
            assertEquals(PhpLanguageLevel.PHP840, XfAddOnPhpLevel.current(project))
            settings.followAddOnPhpLevel = true
            assertNull(XfPhpLevelNotificationProvider().collectNotificationData(project, file))
            XfAddOnPhpLevel.follow(project, plain)
            assertEquals(PhpLanguageLevel.PHP840, XfAddOnPhpLevel.current(project))
            XfAddOnPhpLevel.follow(project, file)
            assertEquals(PhpLanguageLevel.PHP740, XfAddOnPhpLevel.current(project))
        } finally {
            settings.followAddOnPhpLevel = false
        }
    }
}
