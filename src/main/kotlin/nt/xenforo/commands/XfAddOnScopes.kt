package nt.xenforo.commands

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import nt.xenforo.XfBundle.message
import nt.xenforo.lang.XfIcons

private const val SCOPE_PREFIX = "XenForo: "
private const val SCOPE_GROUP = "XenForo"

class XfAddOnPackageSet(private val directory: VirtualFile, text: String) : AbstractPackageSet(text) {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean =
        VfsUtilCore.isAncestor(directory, file, false)
}

object XfAddOnScopes {
    fun named(project: Project): List<NamedScope> = ReadAction.computeBlocking<List<NamedScope>, RuntimeException> { collect(project) }

    private fun collect(project: Project): List<NamedScope> {
        if (project.isDisposed || DumbService.isDumb(project)) return emptyList()
        return XfDevAddOns.list(project).sortedBy { it.id }.map { addOn ->
            val pattern = "file:" + (VfsUtilCore.getRelativePath(addOn.directory, addOn.root) ?: addOn.directory.path) + "//*"
            NamedScope(SCOPE_PREFIX + addOn.id, { message("scope.addon", addOn.id) }, XfIcons.FILE, XfAddOnPackageSet(addOn.directory, pattern))
        }
    }
}

class XfAddOnScopesProvider(private val project: Project) : CustomScopesProvider {
    override fun getCustomScopes(): List<NamedScope> = XfAddOnScopes.named(project)
}

class XfAddOnSearchScopesProvider : SearchScopeProvider {
    override fun getDisplayName(): String = SCOPE_GROUP

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> =
        XfAddOnScopes.named(project).map { GlobalSearchScopesCore.filterScope(project, it) }
}
