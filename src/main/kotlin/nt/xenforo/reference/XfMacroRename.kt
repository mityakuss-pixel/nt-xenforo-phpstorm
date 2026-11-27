package nt.xenforo.reference

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.util.Processor
import nt.xenforo.lang.XfIcons
import nt.xenforo.php.XfPhpNameReference
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfTagElement
import javax.swing.Icon

private const val MACRO_TAG = "macro"
private const val OUTPUT_FOLDER = "_output"

class XfMacroTarget(val tag: XfTagElement) : FakePsiElement(), PsiNameIdentifierOwner {
    private val value: XfAttributeValue?
        get() {
            val header = tag.header ?: return null
            return (header.findAttribute("id") ?: header.findAttribute("name"))?.attributeValue
        }

    override fun getParent(): PsiElement = tag

    override fun getContainingFile(): PsiFile = tag.containingFile

    override fun getProject(): Project = tag.project

    override fun getManager(): PsiManager = tag.manager

    override fun getName(): String? = value?.value

    override fun getPresentableText(): String? = name

    override fun getIcon(open: Boolean): Icon = XfIcons.FILE

    override fun getNameIdentifier(): PsiElement? = value

    override fun getNavigationElement(): PsiElement = value ?: tag

    override fun getTextRange() = (value ?: tag).textRange

    override fun getTextOffset(): Int = value?.let { it.textRange.startOffset + it.valueRange.startOffset } ?: tag.textOffset

    override fun isValid(): Boolean = tag.isValid

    override fun isWritable(): Boolean = tag.isWritable

    override fun isPhysical(): Boolean = true

    override fun getUseScope(): SearchScope = GlobalSearchScope.projectScope(tag.project)

    override fun setName(name: String): PsiElement {
        val value = value ?: return this
        if (value.value != name) ElementManipulators.handleContentChange(value, value.valueRange, name)
        return this
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean = another == this || another == tag

    override fun equals(other: Any?): Boolean = other is XfMacroTarget && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    companion object {
        fun matches(target: XfMacroTarget, resolved: List<PsiElement?>): Boolean =
            resolved.any { it != null && target.manager.areElementsEquivalent(it, target.tag) }

        fun of(element: PsiElement?): XfMacroTarget? {
            val tag = element as? XfTagElement ?: return null
            if (!tag.tagName.equals(MACRO_TAG, ignoreCase = true) || !tag.isBlock || !tag.isWritable) return null
            val file = tag.containingFile?.virtualFile ?: return null
            if (generateSequence(file.parent) { it.parent }.none { it.name == OUTPUT_FOLDER }) return null
            return XfMacroTarget(tag).takeIf { !it.name.isNullOrEmpty() && it.name?.contains("::") != true }
        }
    }
}

class XfMacroRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean = target(dataContext) != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        val target = target(dataContext) ?: return
        PsiElementRenameHandler.rename(target, project, target, editor, dataContext.getData(PsiElementRenameHandler.DEFAULT_NAME))
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        invoke(project, CommonDataKeys.EDITOR.getData(dataContext), CommonDataKeys.PSI_FILE.getData(dataContext), dataContext)
    }

    private fun target(dataContext: DataContext): XfMacroTarget? {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
        val offset = editor.caretModel.offset
        return references(file, offset).firstNotNullOfOrNull { reference ->
            when (reference) {
                is XfNameReference -> if (reference.slot.kind == XfNameKind.MACRO) single(reference.multiResolve(false).map { it.element }) else null
                is XfPhpNameReference -> if (reference.slot.kind == XfNameKind.MACRO) single(reference.multiResolve(false).map { it.element }) else null
                else -> null
            }
        }
    }

    private fun references(file: PsiFile, offset: Int): List<PsiReference> {
        for (candidate in listOf(offset, offset - 1)) {
            if (candidate < 0) continue
            val found = file.findReferenceAt(candidate) ?: continue
            return if (found is PsiMultiReference) found.references.toList() else listOf(found)
        }
        return emptyList()
    }

    private fun single(elements: List<PsiElement?>): XfMacroTarget? = elements.singleOrNull()?.let { XfMacroTarget.of(it) }
}

class XfMacroReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.elementToSearch as? XfMacroTarget ?: return
        val name = target.name ?: return
        parameters.optimizer.searchWord(name, parameters.effectiveSearchScope, UsageSearchContext.ANY, true, target)
    }
}
