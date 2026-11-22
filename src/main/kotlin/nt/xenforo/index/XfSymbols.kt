package nt.xenforo.index

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.usageView.UsageViewTypeLocation
import nt.xenforo.XfBundle
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfTagElement
import javax.swing.Icon

private const val MACRO_KEY_PREFIX = "m:"
private const val ADD_ONS_FOLDER = "addons"
private const val ADD_ON_FILE = "addon.json"
private const val FAR_COLUMN = 80
private const val DEFAULT_TYPE = "symbol.type.default"
private val TYPE_NAMES = linkedMapOf(
    "tm:" to "symbol.type.modification",
    "t:" to "symbol.type.template",
    "m:" to "symbol.type.macro",
    "pm:" to "symbol.type.permission",
    "ap:" to "symbol.type.permission",
    "p:" to "symbol.type.phrase",
    "r:" to "symbol.type.route",
    "s:" to "symbol.type.property",
    "o:" to "symbol.type.option",
    "a:" to "symbol.type.addon",
    "x:" to "symbol.type.extension.class",
    "v:" to "symbol.type.view",
    "wp:" to "symbol.type.widget.position",
    "ck:" to "symbol.type.container",
    "je:" to "symbol.type.handler",
    "jv:" to "symbol.type.handler",
    "jc:" to "symbol.type.handler",
)

data class XfSymbolLocation(val file: VirtualFile, val symbol: XfSymbol) {
    val isOutput: Boolean
        get() = generateSequence(file.parent) { it.parent }.any { it.name == "_output" }

    val relativePath: String
        get() {
            val names = ArrayList<String>()
            var current: VirtualFile? = file
            while (current != null && current.name != ADD_ONS_FOLDER) {
                names += current.name
                current = current.parent
            }
            return if (current == null) file.name else names.asReversed().joinToString("/")
        }

    val addOnId: String?
        get() {
            val root = generateSequence(file.parent) { it.parent }.takeWhile { it.name != ADD_ONS_FOLDER }
                .firstOrNull { it.findChild(ADD_ON_FILE) != null } ?: return null
            val names = generateSequence(root) { it.parent }.takeWhile { it.name != ADD_ONS_FOLDER }.map { it.name }.toList()
            return names.asReversed().joinToString("/")
        }
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

    fun phraseText(project: Project, title: String): String? {
        val location = locations(project, XfSymbolKeys.phrase(title)).firstOrNull() ?: return null
        val file = location.file
        if (!file.isValid) return null
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.charsSequence ?: LoadTextUtil.loadText(file)
        return XfSymbolScanner.phraseText(text, location.symbol.offset, file.extension.equals("xml", ignoreCase = true))
    }

    fun targets(project: Project, key: String, name: String): List<PsiElement> {
        val inFile = key.startsWith(MACRO_KEY_PREFIX)
        return locations(project, key).mapNotNull { target(project, it, name, inFile, key) }
    }

    fun typeName(key: String): String {
        val bundleKey = TYPE_NAMES.entries.firstOrNull { key.startsWith(it.key) }?.value ?: DEFAULT_TYPE
        return XfBundle.message(bundleKey)
    }

    private fun target(project: Project, location: XfSymbolLocation, name: String, inFile: Boolean, key: String): PsiElement? {
        if (!location.file.isValid) return null
        if (!location.isOutput) return XfSymbolTarget(project, location.file, location.symbol.offset, name, typeName(key))
        val psiFile = PsiManager.getInstance(project).findFile(location.file) ?: return null
        if (!inFile) return psiFile
        val element = psiFile.viewProvider.findElementAt(location.symbol.offset, XfLanguage) ?: return psiFile
        return PsiTreeUtil.getParentOfType(element, XfTagElement::class.java) ?: element
    }
}

class XfSymbolDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? =
        if (element is XfSymbolTarget && location is UsageViewTypeLocation) element.typeName else null
}

class XfSymbolTarget(
    private val project: Project,
    private val file: VirtualFile,
    private val offset: Int,
    private val symbolName: String,
    val typeName: String = XfBundle.message(DEFAULT_TYPE),
) : FakePsiElement() {
    override fun getParent(): PsiElement? = file.parent?.let { PsiManager.getInstance(project).findDirectory(it) }

    override fun getProject(): Project = project

    override fun getManager(): PsiManager = PsiManager.getInstance(project)

    override fun getName(): String = symbolName

    override fun getPresentableText(): String = symbolName

    override fun getLocationString(): String = file.parent?.parent?.name?.let { "$it/${file.name}" } ?: file.name

    override fun getIcon(open: Boolean): Icon = file.fileType.icon ?: AllIcons.FileTypes.Xml

    override fun getTextOffset(): Int = offset

    override fun isValid(): Boolean = file.isValid

    override fun canNavigate(): Boolean = file.isValid

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file, visibleOffset()).navigate(requestFocus)
    }

    fun visibleOffset(): Int {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return offset
        if (offset > document.textLength) return offset
        val start = document.getLineStartOffset(document.getLineNumber(offset))
        if (offset - start <= FAR_COLUMN) return offset
        val text = document.charsSequence
        var indent = start
        while (indent < offset && (text[indent] == ' ' || text[indent] == '\t')) indent++
        return indent
    }

    override fun equals(other: Any?): Boolean = other is XfSymbolTarget && other.file == file && other.offset == offset

    override fun hashCode(): Int = file.hashCode() * 31 + offset
}
