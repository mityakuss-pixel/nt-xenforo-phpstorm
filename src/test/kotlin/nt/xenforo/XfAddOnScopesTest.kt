package nt.xenforo

import nt.xenforo.commands.XfAddOnPackageSet
import nt.xenforo.commands.XfAddOnScopesProvider

class XfAddOnScopesTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject("src/addons/Demo/Plain/addon.json", "{\"title\": \"Plain\"}")
    }

    fun testScopePerAddOnWithDevelopmentOutput() {
        addOutputFile("phrases/demo.txt", "Demo")
        val inside = myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", "<?php\n").virtualFile
        val outside = myFixture.addFileToProject("src/addons/Demo/Plain/Listener.php", "<?php\n").virtualFile
        val scopes = XfAddOnScopesProvider(project).customScopes
        assertEquals(listOf("XenForo: Demo/Test"), scopes.map { it.scopeId })
        val set = scopes.single().value as XfAddOnPackageSet
        assertTrue(set.contains(inside, project, null))
        assertFalse(set.contains(outside, project, null))
    }
}
