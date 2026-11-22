package nt.xenforo.index

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

private const val JS_EXTENSION = "js"
private const val MINIFIED_SUFFIX = ".min.js"
private const val COMPILED_SUFFIX = "-compiled.js"
private const val NODE_MODULES = "node_modules"
private const val ELEMENT_PREFIX = "je:"
private const val EVENT_PREFIX = "jv:"
private const val CLASS_PREFIX = "jc:"
private const val INIT_ATTRIBUTE = "data-xf-init"
private const val ATTRIBUTE_PREFIX = "data-xf-"
private const val XF_MARKER = "XF."

private val NOT_EVENTS = setOf("init", "key")
private val EVENT_NAME = Regex("""[a-z]+""")
private val ELEMENT_REGISTER = Regex("""XF\.Element\.register\(\s*['"]([\w-]+)['"]\s*,\s*['"]([\w$.]+)['"]""")
private val EVENT_REGISTER = Regex("""XF\.Event\.register\(\s*['"]([\w-]+)['"]\s*,\s*['"]([\w-]+)['"]\s*,\s*['"]([\w$.]+)['"]""")
private val CLASS_DEFINITION = Regex(
    """(?m)^[ \t]*(?:window\.)?((?:XF|[A-Z][\w$]*)(?:\.[A-Z$][\w$]*)+)\s*=\s*(?:XF\.(?:create|extend)\b|XF\.(?:Element|Event)\.newHandler\b|class\b)""",
)

object XfJsHandlers {
    fun isIndexed(file: VirtualFile): Boolean {
        val name = file.name
        if (!file.extension.equals(JS_EXTENSION, ignoreCase = true)) return false
        if (name.endsWith(MINIFIED_SUFFIX) || name.endsWith(COMPILED_SUFFIX)) return false
        return generateSequence(file.parent) { it.parent }.none { it.name == NODE_MODULES }
    }

    fun scan(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        if (!text.contains(XF_MARKER)) return
        for (match in ELEMENT_REGISTER.findAll(text)) {
            result.putIfAbsent(ELEMENT_PREFIX + match.groupValues[1], XfSymbol(match.range.first, match.groupValues[2]))
        }
        for (match in EVENT_REGISTER.findAll(text)) {
            result.putIfAbsent(eventPrefix(match.groupValues[1]) + match.groupValues[2], XfSymbol(match.range.first, match.groupValues[3]))
        }
        for (match in CLASS_DEFINITION.findAll(text)) {
            val group = match.groups[1] ?: continue
            result.putIfAbsent(CLASS_PREFIX + group.value, XfSymbol(group.range.first, ""))
        }
    }

    fun prefixFor(attribute: String): String? {
        val name = attribute.lowercase()
        if (name == INIT_ATTRIBUTE) return ELEMENT_PREFIX
        if (!name.startsWith(ATTRIBUTE_PREFIX)) return null
        val event = name.removePrefix(ATTRIBUTE_PREFIX)
        if (event in NOT_EVENTS || !EVENT_NAME.matches(event)) return null
        return eventPrefix(event)
    }

    fun className(project: Project, key: String): String? =
        XfSymbols.detail(project, key)?.takeIf { it.isNotEmpty() }

    fun registration(project: Project, key: String): XfSymbolLocation? = XfSymbols.locations(project, key).firstOrNull()

    fun targets(project: Project, key: String): List<PsiElement> {
        if (DumbService.isDumb(project)) return emptyList()
        val className = className(project, key) ?: return emptyList()
        val definitions = XfSymbols.targets(project, CLASS_PREFIX + className, className)
        return definitions.ifEmpty { XfSymbols.targets(project, key, key.substringAfterLast(':')) }
    }

    fun classLocation(project: Project, className: String): XfSymbolLocation? = XfSymbols.locations(project, CLASS_PREFIX + className).firstOrNull()

    private fun eventPrefix(event: String): String = "$EVENT_PREFIX$event:"
}
