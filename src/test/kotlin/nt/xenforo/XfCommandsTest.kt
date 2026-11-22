package nt.xenforo

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import nt.xenforo.commands.XfAddOnActionGroup
import nt.xenforo.commands.XfAddOnVersion
import nt.xenforo.commands.XfAddOns
import nt.xenforo.commands.XfBuildReleaseAction
import nt.xenforo.commands.XfVersion

class XfCommandsTest : BasePlatformTestCase() {
    fun testAddOnFromNestedFile() {
        myFixture.addFileToProject("forum/cmd.php", "<?php")
        myFixture.addFileToProject("forum/src/addons/Demo/Board/addon.json", "{\n    \"version_id\": 1000070,\n    \"version_string\": \"1.0.0\"\n}")
        val template = file("forum/src/addons/Demo/Board/_output/templates/public/demo.html")
        val addOn = XfAddOns.find(project, template)!!
        assertEquals("Demo/Board", addOn.id)
        assertEquals("forum", addOn.root.name)
        assertEquals("cmd.php", addOn.script?.name)
        assertEquals("Demo-Board-", addOn.releasePrefix)
        assertEquals(XfAddOnVersion(1000070, "1.0.0"), addOn.version())
    }

    fun testSingleLevelAddOnAndFolderSelection() {
        myFixture.addFileToProject("forum/src/addons/Solo/addon.json", "{}")
        val folder = file("forum/src/addons/Solo/Setup.php").parent
        assertEquals("Solo", XfAddOns.find(project, folder)?.id)
    }

    fun testFilesOutsideAddOns() {
        assertNull(XfAddOns.find(project, file("forum/src/XF/Pub/App.php")))
        myFixture.addFileToProject("other/addon.json", "{}")
        assertNull(XfAddOns.find(project, file("other/Setup.php")))
    }

    fun testConfiguredRootForLinkedAddOn() {
        myFixture.addFileToProject("forum/cmd.php", "<?php")
        myFixture.addFileToProject("forum/src/addons/Demo/Board/addon.json", "{}")
        myFixture.addFileToProject("work/Demo/Board/addon.json", "{}")
        val root = file("forum/cmd.php").parent
        val addOn = XfAddOns.find(file("work/Demo/Board/Setup.php"), root)!!
        assertEquals("Demo/Board", addOn.id)
        assertEquals(root, addOn.root)
        assertEquals("Board", addOn.directory.name)
    }

    fun testMenuShowsAddOnId() {
        myFixture.addFileToProject("forum/src/addons/Demo/Board/addon.json", "{}")
        val inside = updatedPresentation(file("forum/src/addons/Demo/Board/Setup.php"))
        assertTrue(inside.isEnabledAndVisible)
        assertEquals("XenForo: Demo/Board", inside.text)
        assertFalse(updatedPresentation(file("forum/src/XF.php")).isEnabledAndVisible)
        val build = TestActionEvent.createTestEvent(XfBuildReleaseAction(), context(file("forum/src/addons/Demo/Board/Setup.php")))
        XfBuildReleaseAction().update(build)
        assertTrue(build.presentation.isEnabledAndVisible)
    }

    fun testVersionIds() {
        assertEquals(2031370, XfVersion.id("2.3.13"))
        assertEquals(2030031, XfVersion.id("2.3.0 Beta 1"))
        assertEquals(1000010, XfVersion.id("1.0.0 Alpha"))
        assertEquals(1020352, XfVersion.id("1.2.3 RC 2"))
        assertEquals(1020352, XfVersion.id("1.2.3 Release Candidate 2"))
        assertEquals(1000042, XfVersion.id("1.0.0 Beta 12"))
        assertEquals(1000082, XfVersion.id("1.0.0.12"))
        assertEquals(2020091, XfVersion.id("2.2.0 Patch Level 1"))
        assertEquals(2031392, XfVersion.id("2.3.13 Patch 2"))
        assertEquals(2031392, XfVersion.id("2.3.13 PL 2"))
        assertEquals(1020351, XfVersion.id("1.2.3RC1"))
        assertEquals(1020351, XfVersion.id("1.2.3 rc1"))
        assertEquals(1020390, XfVersion.id("1.2.3 Patch"))
        assertNull(XfVersion.id("1.0"))
        assertNull(XfVersion.id("1.100.0"))
        assertNull(XfVersion.id("1.0.0 Beta 1.2"))
    }

    fun testNextVersion() {
        assertEquals("1.0.1", XfVersion.next("1.0.0"))
        assertEquals("2.4.10", XfVersion.next("2.4.9"))
        assertEquals("1.0.0 Beta 1", XfVersion.next("1.0.0 Beta 1"))
    }

    private fun updatedPresentation(file: VirtualFile) =
        TestActionEvent.createTestEvent(XfAddOnActionGroup(), context(file)).also { XfAddOnActionGroup().update(it) }.presentation

    private fun context(file: VirtualFile) =
        SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.VIRTUAL_FILE, file).build()

    private fun file(path: String): VirtualFile =
        myFixture.findFileInTempDir(path) ?: myFixture.addFileToProject(path, "").virtualFile
}
