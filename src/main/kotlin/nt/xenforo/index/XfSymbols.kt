package nt.xenforo.index

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfTagElement
import javax.swing.Icon

private const val MACRO_KEY_PREFIX = "m:"

data class XfSymbolLocation(val file: VirtualFile, val symbol: XfSymbol) {
    val isOutput: Boolean
        get() = generateSequence(file.parent) { it.parent }.any { it.name == "_output" }
}

object XfSymbols {
    fun keys(project: Project, prefix: String): List<String> {
        val keys = ArrayList<String>()
        FileBasedIndex.getInstance().processAllKeys(
            XF_SYMBOL_INDEX,
            { key -> if (key.startsWith(prefix)) keys += key; true },
            GlobalSearchScope.allScope(project),
            null,
        )
        return keys
    }

    fun locations(project: Project, key: String): List<XfSymbolLocation> {
        val locations = ArrayList<XfSymbolLocation>()
        FileBasedIndex.getInstance().processValues(
            XF_SYMBOL_INDEX,
            key,
            null,
            { file, value -> locations += XfSymbolLocation(file, value); true },
            GlobalSearchScope.allScope(project),
        )
        return if (locations.any { it.isOutput }) locations.filter { it.isOutput } else locations
    }

    fun detail(project: Project, key: String): String? = locations(project, key).firstOrNull()?.symbol?.detail

    fun targets(project: Project, key: String, name: String): List<PsiElement> {
        val inFile = key.startsWith(MACRO_KEY_PREFIX)
        return locations(project, key).mapNotNull { target(project, it, name, inFile) }
    }

    private fun target(project: Project, location: XfSymbolLocation, name: String, inFile: Boolean): PsiElement? {
        if (!location.file.isValid) return null
        if (!location.isOutput) return XfSymbolTarget(project, location.file, location.symbol.offset, name)
        val psiFile = PsiManager.getInstance(project).findFile(location.file) ?: return null
        if (!inFile) return psiFile
        val element = psiFile.viewProvider.findElementAt(location.symbol.offset, XfLanguage) ?: return psiFile
        return PsiTreeUtil.getParentOfType(element, XfTagElement::class.java) ?: element
    }
}

class XfSymbolTarget(
    private val project: Project,
    private val file: VirtualFile,
    private val offset: Int,
    private val symbolName: String,
) : FakePsiElement() {
    override fun getParent(): PsiElement? = file.parent?.let { PsiManager.getInstance(project).findDirectory(it) }

    override fun getProject(): Project = project

    override fun getManager(): PsiManager = PsiManager.getInstance(project)

    override fun getName(): String = symbolName

    override fun getPresentableText(): String = symbolName

    override fun getLocationString(): String = file.parent?.parent?.name?.let { "$it/${file.name}" } ?: file.name

    override fun getIcon(open: Boolean): Icon = AllIcons.FileTypes.Xml

    override fun getTextOffset(): Int = offset

    override fun isValid(): Boolean = file.isValid

    override fun canNavigate(): Boolean = file.isValid

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, offset).navigate(requestFocus)
    }

    override fun equals(other: Any?): Boolean = other is XfSymbolTarget && other.file == file && other.offset == offset

    override fun hashCode(): Int = file.hashCode() * 31 + offset
}
