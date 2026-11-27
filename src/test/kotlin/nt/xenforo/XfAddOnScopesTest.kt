package nt.xenforo

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScopeProvider
import nt.xenforo.commands.XfAddOnPackageSet
import nt.xenforo.commands.XfAddOnScopesProvider
import nt.xenforo.commands.XfAddOnSearchScopesProvider

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

    fun testSearchScopesForFindInFilesAndTodo() {
        addOutputFile("phrases/demo.txt", "Demo")
        val inside = myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", "<?php\n").virtualFile
        val outside = myFixture.addFileToProject("src/addons/Demo/Plain/Listener.php", "<?php\n").virtualFile
        val provider = SearchScopeProvider.EP_NAME.extensionList.filterIsInstance<XfAddOnSearchScopesProvider>().single()
        assertEquals("XenForo", provider.displayName)
        val scope = provider.getSearchScopes(project, SimpleDataContext.getProjectContext(project)).single() as GlobalSearchScope
        assertEquals("XenForo: Demo/Test", scope.displayName)
        assertTrue(scope.contains(inside))
        assertFalse(scope.contains(outside))
    }
}
