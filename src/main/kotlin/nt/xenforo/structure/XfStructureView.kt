package nt.xenforo.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import nt.xenforo.lang.XfIcons
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTagHeader

private const val LOCATION_LIMIT = 60
private const val MACRO_TAG = "macro"

private val KEY_ATTRIBUTES = mapOf(
    "if" to "is",
    "elseif" to "is",
    "foreach" to "loop",
    "set" to "var",
    "include" to "template",
    "wrap" to "template",
    "extends" to "template",
    "css" to "src",
    "js" to "src",
    "widget" to "key",
    "widgetpos" to "id",
    "form" to "action",
    "callback" to "method",
)

private val FALLBACK_ATTRIBUTES = listOf("id", "name", "template", "key", "position", "src", "var", "label")

class XfStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?): StructureViewModel = XfStructureViewModel(psiFile, editor)

        override fun isRootNodeShown(): Boolean = false
    }
}

class XfStructureViewModel(file: PsiFile, editor: Editor?) :
    StructureViewModelBase(file, editor, XfFileElement(file)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(XfTagElement::class.java, XfTagHeader::class.java)
        dataModel(file, editor)?.let { model ->
            Disposer.register(this, model)
            (root as XfFileElement).dataRoot = model.root
        }
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = (element.value as? XfTagElement)?.isBlock == false

    private fun dataModel(file: PsiFile, editor: Editor?): StructureViewModel? {
        val provider = file.viewProvider
        val dataFile = provider.languages.firstOrNull { it != XfLanguage }?.let(provider::getPsi) ?: return null
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(dataFile) as? TreeBasedStructureViewBuilder ?: return null
        return builder.createStructureViewModel(editor)
    }
}

private class XfFileElement(file: PsiFile) : XfPsiElement(file) {
    var dataRoot: StructureViewTreeElement? = null

    override fun getPresentation(): ItemPresentation = PresentationData((element as? PsiFile)?.name, null, XfIcons.FILE, null)

    override fun getChildren(): Array<TreeElement> {
        val base = (element as? PsiFile)?.viewProvider?.getPsi(XfLanguage) ?: return emptyArray()
        val own = childrenOf(base).map(::XfTagStructureElement)
        return (own + dataRoot?.children.orEmpty()).toTypedArray()
    }
}

private class XfTagStructureElement(element: PsiElement) : XfPsiElement(element) {
    override fun getPresentation(): ItemPresentation {
        val element = element
        val header = (element as? XfTagElement)?.header ?: element as? XfTagHeader ?: return PresentationData()
        val name = header.tagName
        val isMacroDefinition = name.equals(MACRO_TAG, ignoreCase = true) && (element as? XfTagElement)?.isBlock == true
        val id = header.findAttribute("id")?.attributeValue?.value
        if (isMacroDefinition && !id.isNullOrEmpty()) return PresentationData("xf:$name#$id", null, AllIcons.Nodes.Method, null)
        return PresentationData("xf:$name", location(header), XfIcons.TAG, null)
    }

    override fun getChildren(): Array<TreeElement> {
        val tag = element as? XfTagElement ?: return emptyArray()
        return childrenOf(tag).map(::XfTagStructureElement).toTypedArray()
    }

    private fun location(header: XfTagHeader): String? {
        val name = header.tagName.lowercase()
        val attribute = KEY_ATTRIBUTES[name]?.let(header::findAttribute)
            ?: FALLBACK_ATTRIBUTES.firstNotNullOfOrNull(header::findAttribute)
            ?: return null
        val value = attribute.attributeValue?.value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (value.isEmpty()) return null
        return if (value.length > LOCATION_LIMIT) value.take(LOCATION_LIMIT).trimEnd() + "…" else value
    }
}

private abstract class XfPsiElement(element: PsiElement) : StructureViewTreeElement {
    private val pointer: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(element)

    protected val element: PsiElement?
        get() = pointer.element

    override fun getValue(): Any? = element

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigationItem)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? NavigationItem)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (element as? NavigationItem)?.canNavigateToSource() == true

    override fun equals(other: Any?): Boolean = other is XfPsiElement && other.javaClass == javaClass && other.element == element

    override fun hashCode(): Int = element?.hashCode() ?: 0
}

private fun childrenOf(owner: PsiElement): List<PsiElement> {
    val result = ArrayList<PsiElement>()
    PsiTreeUtil.processElements(owner) { element ->
        if (element !== owner && isStructural(element) && nearestOwner(element) === owner.takeUnless { it is PsiFile }) result += element
        true
    }
    return result
}

private fun isStructural(element: PsiElement): Boolean = element is XfTagElement || (element is XfTagHeader && element.isBranch)

private fun nearestOwner(element: PsiElement): XfTagElement? = PsiTreeUtil.getParentOfType(element, XfTagElement::class.java, true)
