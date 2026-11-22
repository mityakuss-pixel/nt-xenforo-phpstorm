package nt.xenforo.modification

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.Key
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import java.lang.ref.SoftReference
import nt.xenforo.index.XfSymbolLocation
import nt.xenforo.index.XfSymbols
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private const val CDATA_START = "<![CDATA["
private const val CDATA_END = "]]>"
private const val TEMPLATE_END = "</template>"
private const val MODIFICATION_END = "</modification>"
private const val MATCH_LIMIT = 100
private val TEXT = Key.create<SoftReference<Pair<Long, CharSequence>>>("nt.xenforo.modificationText")

private val JSON_STRING_FIELD = Regex(""""(\w+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private val JSON_ORDER = Regex(""""execution_order"\s*:\s*(-?\d+)""")
private val JSON_ENABLED = Regex(""""enabled"\s*:\s*(true|false|1|0)""")
private val XML_ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*"([^"]*)"""")
private val MODIFICATION_TAG = Regex("""<modification\s([^>]*?)(/?)>""")

data class XfModification(
    val key: String,
    val type: String,
    val template: String,
    val description: String,
    val action: String,
    val find: String,
    val replace: String,
    val order: Int,
    val enabled: Boolean,
    val file: VirtualFile,
    val offset: Int,
    val addOnId: String?,
)

data class XfTemplateSource(val file: VirtualFile, val range: TextRange, val text: CharSequence) {
    val body: CharSequence
        get() = text.subSequence(range.startOffset, range.endOffset)
}

object XfModifications {
    const val STR_REPLACE = "str_replace"
    const val PREG_REPLACE = "preg_replace"
    const val CALLBACK = "callback"

    fun forTemplate(project: Project, type: String, title: String): List<XfModification> {
        val prefix = XfSymbolKeys.modificationPrefix(type, title)
        return load(project, XfDataTracker.keys(project, XfSymbolKeys.MODIFICATION_PREFIX).filter { it.startsWith(prefix) })
    }

    fun all(project: Project): List<XfModification> = load(project, XfDataTracker.keys(project, XfSymbolKeys.MODIFICATION_PREFIX))

    fun keyExists(project: Project, key: String): Boolean =
        XfDataTracker.keys(project, XfSymbolKeys.MODIFICATION_PREFIX).any { it.endsWith(":$key") }

    fun matches(action: String, find: String, text: CharSequence): List<Int>? {
        if (action == STR_REPLACE) {
            val needle = find.replace("\r\n", "\n")
            if (needle.isEmpty()) return emptyList()
            val result = ArrayList<Int>()
            var index = StringUtil.indexOf(text, needle, 0)
            while (index >= 0 && result.size < MATCH_LIMIT) {
                result += index
                index = StringUtil.indexOf(text, needle, index + needle.length)
            }
            return result
        }
        val pattern = pattern(find.trim().replace("\r\n", "\n").replace("\\r\\n", "\\n")) ?: return null
        val matcher = pattern.matcher(text)
        val result = ArrayList<Int>()
        while (result.size < MATCH_LIMIT && matcher.find()) {
            result += matcher.start()
            if (matcher.end() == matcher.start() && matcher.end() >= text.length) break
        }
        return result
    }

    fun pattern(source: String): Pattern? {
        if (source.length < 2) return null
        val open = source[0]
        if (open.isLetterOrDigit() || open == '\\' || open.isWhitespace()) return null
        val close = when (open) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            '<' -> '>'
            else -> open
        }
        val end = source.lastIndexOf(close)
        if (end <= 0) return null
        var flags = 0
        for (flag in source.substring(end + 1)) {
            flags = flags or when (flag) {
                'i' -> Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                'm' -> Pattern.MULTILINE
                's' -> Pattern.DOTALL
                'x' -> Pattern.COMMENTS
                'u' -> Pattern.UNICODE_CASE
                'D' -> 0
                else -> return null
            }
        }
        val body = source.substring(1, end).replace("(?P<", "(?<")
        return try {
            Pattern.compile(body, flags)
        } catch (error: PatternSyntaxException) {
            null
        }
    }

    fun templateSource(project: Project, type: String, title: String): XfTemplateSource? {
        val location = XfSymbols.locations(project, XfSymbolKeys.template(type, title)).firstOrNull() ?: return null
        val file = location.file
        if (!file.isValid) return null
        val text = text(file)
        if (location.isOutput) return XfTemplateSource(file, TextRange(0, text.length), text)
        val start = location.symbol.offset
        val cdata = start >= CDATA_START.length && StringUtil.startsWith(text, start - CDATA_START.length, CDATA_START)
        val end = StringUtil.indexOf(text, if (cdata) CDATA_END else TEMPLATE_END, start).takeIf { it >= 0 } ?: return null
        return XfTemplateSource(file, TextRange(start, end), text)
    }

    fun text(file: VirtualFile): CharSequence {
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.charsSequence }
        val stamp = file.modificationStamp
        file.getUserData(TEXT)?.get()?.takeIf { it.first == stamp }?.let { return it.second }
        val text = LoadTextUtil.loadText(file)
        file.putUserData(TEXT, SoftReference(stamp to text))
        return text
    }

    private fun load(project: Project, keys: List<String>): List<XfModification> =
        keys.flatMap { key -> XfSymbols.locations(project, key).mapNotNull { parse(key, it) } }

    private fun parse(key: String, location: XfSymbolLocation): XfModification? {
        val file = location.file
        if (!file.isValid) return null
        val parts = key.removePrefix(XfSymbolKeys.MODIFICATION_PREFIX)
        val type = parts.substringBefore(':')
        val modificationKey = parts.substringAfterLast(':')
        val template = parts.substringAfter(':').substringBeforeLast(':')
        val text = text(file)
        return if (file.extension.equals("json", ignoreCase = true)) {
            val fields = JSON_STRING_FIELD.findAll(text).associate { it.groupValues[1] to StringUtil.unescapeStringCharacters(it.groupValues[2]) }
            XfModification(
                modificationKey, type, template, fields["description"].orEmpty(), fields["action"] ?: STR_REPLACE,
                fields["find"].orEmpty(), fields["replace"].orEmpty(),
                JSON_ORDER.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 10,
                JSON_ENABLED.find(text)?.groupValues?.get(1)?.let { it == "true" || it == "1" } ?: true,
                file, 0, location.addOnId,
            )
        } else {
            val tag = MODIFICATION_TAG.find(text, location.symbol.offset)?.takeIf { it.range.first == location.symbol.offset } ?: return null
            val attributes = XML_ATTRIBUTE.findAll(tag.groupValues[1]).associate { it.groupValues[1] to StringUtil.unescapeXmlEntities(it.groupValues[2]) }
            val bodyEnd = if (tag.groupValues[2] == "/") tag.range.last + 1 else StringUtil.indexOf(text, MODIFICATION_END, tag.range.last).takeIf { it >= 0 } ?: text.length
            val body = text.subSequence(tag.range.last + 1, bodyEnd)
            XfModification(
                modificationKey, type, template, attributes["description"].orEmpty(), attributes["action"] ?: STR_REPLACE,
                element(body, "find"), element(body, "replace"),
                attributes["execution_order"]?.toIntOrNull() ?: 10,
                attributes["enabled"] != "0",
                file, location.symbol.offset, location.addOnId,
            )
        }
    }

    private fun element(text: CharSequence, name: String): String {
        val open = "<$name>"
        val start = StringUtil.indexOf(text, open, 0).takeIf { it >= 0 } ?: return ""
        val end = StringUtil.indexOf(text, "</$name>", start).takeIf { it >= 0 } ?: return ""
        val raw = text.subSequence(start + open.length, end).toString()
        return if (raw.startsWith(CDATA_START) && raw.endsWith(CDATA_END)) {
            raw.substring(CDATA_START.length, raw.length - CDATA_END.length).replace("]]]]><![CDATA[>", "]]>")
        } else {
            StringUtil.unescapeXmlEntities(raw)
        }
    }
}
