package nt.xenforo.index

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

enum class XfCallableKind(val keyPrefix: String, val single: String, val plural: String, val skippedParameters: Int) {
    FUNCTION("function:", "addFunction", "addFunctions", 2),
    FILTER("filter:", "addFilter", "addFilters", 3),
    TEST("test:", "addTest", "addTests", 2),
}

private const val PHP_EXTENSION = "php"
private const val ADD_ONS_FOLDER = "addons"
private const val CONFIG_FILE = "config.php"
private const val SOURCE_FOLDER = "src"
private const val TAG_KEY_PREFIX = "tag:"
private const val TAG_SINGLE = "setTag"
private const val TAG_PLURAL = "setTags"
private val REGISTRATION_MARKERS = listOf("addFunction", "addFilter", "addTest", TAG_SINGLE)

class XfTemplaterIndex : FileBasedIndexExtension<String, XfSymbol>() {
    override fun getName(): ID<String, XfSymbol> = XF_TEMPLATER_INDEX

    override fun getIndexer(): DataIndexer<String, XfSymbol, FileContent> = DataIndexer { content -> scan(content) }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<XfSymbol> = XfSymbolExternalizer

    override fun getVersion(): Int = 2

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        !file.isDirectory && file.extension.equals(PHP_EXTENSION, ignoreCase = true) &&
            ((file.name == CONFIG_FILE && file.parent?.name == SOURCE_FOLDER) || generateSequence(file.parent) { it.parent }.any { it.name == ADD_ONS_FOLDER })
    }

    override fun dependsOnFileContent(): Boolean = true

    private fun scan(content: FileContent): Map<String, XfSymbol> {
        val text = content.contentAsText
        if (REGISTRATION_MARKERS.none { text.contains(it) }) return emptyMap()
        val result = HashMap<String, XfSymbol>()
        for (call in PsiTreeUtil.findChildrenOfType(content.psiFile, MethodReference::class.java)) {
            val name = call.name ?: continue
            val kind = XfCallableKind.entries.firstOrNull { it.single == name || it.plural == name }
            when {
                kind != null -> collect(call, kind.keyPrefix, plural = kind.plural == name, result)
                name == TAG_SINGLE || name == TAG_PLURAL -> collect(call, TAG_KEY_PREFIX, plural = name == TAG_PLURAL, result)
            }
        }
        return result
    }

    private fun collect(call: MethodReference, keyPrefix: String, plural: Boolean, result: MutableMap<String, XfSymbol>) {
        val parameters = call.parameters
        if (!plural) {
            val name = (parameters.getOrNull(0) as? StringLiteralExpression)?.contents ?: return
            val value = parameters.getOrNull(1) ?: return
            result.putIfAbsent(keyPrefix + name.lowercase(), XfSymbol(value.textRange.startOffset, name))
            return
        }
        val array = parameters.getOrNull(0) as? ArrayCreationExpression ?: return
        for (element in array.hashElements) {
            val name = (element.key as? StringLiteralExpression)?.contents ?: continue
            val value = element.value ?: continue
            result.putIfAbsent(keyPrefix + name.lowercase(), XfSymbol(value.textRange.startOffset, name))
        }
    }
}

val XF_TEMPLATER_INDEX: ID<String, XfSymbol> = ID.create("nt.xenforo.index.XfTemplaterIndex")

object XfCustomCallables {
    fun names(project: Project, kind: XfCallableKind): List<String> {
        if (DumbService.isDumb(project)) return emptyList()
        val names = ArrayList<String>()
        FileBasedIndex.getInstance().processAllKeys(
            XF_TEMPLATER_INDEX,
            { key -> if (key.startsWith(kind.keyPrefix)) names += key.removePrefix(kind.keyPrefix); true },
            GlobalSearchScope.allScope(project),
            null,
        )
        return names.filter { locations(project, kind, it).isNotEmpty() }
    }

    fun locations(project: Project, kind: XfCallableKind, name: String): List<XfSymbolLocation> {
        if (DumbService.isDumb(project)) return emptyList()
        val locations = ArrayList<XfSymbolLocation>()
        FileBasedIndex.getInstance().processValues(
            XF_TEMPLATER_INDEX,
            kind.keyPrefix + name.lowercase(),
            null,
            { file, value -> locations += XfSymbolLocation(file, value); true },
            GlobalSearchScope.allScope(project),
        )
        return locations
    }
}

object XfTagRegistrations {
    fun names(project: Project): List<String> {
        if (DumbService.isDumb(project)) return emptyList()
        val names = ArrayList<String>()
        FileBasedIndex.getInstance().processAllKeys(
            XF_TEMPLATER_INDEX,
            { key -> if (key.startsWith(TAG_KEY_PREFIX)) names += key.removePrefix(TAG_KEY_PREFIX); true },
            GlobalSearchScope.allScope(project),
            null,
        )
        return names.filter { locations(project, it).isNotEmpty() }
    }

    fun locations(project: Project, name: String): List<XfSymbolLocation> {
        if (DumbService.isDumb(project)) return emptyList()
        val locations = ArrayList<XfSymbolLocation>()
        FileBasedIndex.getInstance().processValues(
            XF_TEMPLATER_INDEX,
            TAG_KEY_PREFIX + name.lowercase(),
            null,
            { file, value -> locations += XfSymbolLocation(file, value); true },
            GlobalSearchScope.allScope(project),
        )
        return locations
    }
}
