package nt.xenforo.documentation

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import nt.xenforo.lang.XfLanguage

class XfParameterParts(val type: String?, val prefix: String, val name: String, val default: String?)

object XfSignatureHtml {
    private const val INDENT = "    "
    private const val MULTILINE_FROM = 2
    private const val SATURATION = 1.0f

    private val PRIMITIVES = setOf(
        "int", "float", "string", "bool", "array", "null", "mixed", "callable", "iterable", "object",
        "void", "true", "false", "self", "static", "never", "resource", "parent",
    )
    private val PARAMETER = Regex("""^(?:([^\s$&.][^\s$]*)\s+)?(&?(?:\.\.\.)?)(\$?\w+)(?:\s*=\s*(.+))?$""")
    private val DEFAULT_TOKEN = Regex("""'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?|\w+|\s+|.""")

    fun parse(parameter: String): XfParameterParts {
        val match = PARAMETER.matchEntire(parameter.trim()) ?: return XfParameterParts(null, "", parameter.trim(), null)
        val (type, prefix, name, default) = match.destructured
        return XfParameterParts(type.ifEmpty { null }, prefix, name, default.ifEmpty { null })
    }

    fun callable(prefix: String, name: String, parameters: List<String>?, suffix: String?, returnType: String?): String = buildString {
        if (prefix.isNotEmpty()) append(escape(prefix))
        append(span(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, name))
        if (parameters != null) {
            append(span(DefaultLanguageHighlighterColors.PARENTHESES, "("))
            if (parameters.size >= MULTILINE_FROM) {
                append('\n')
                parameters.forEachIndexed { index, parameter ->
                    append(INDENT).append(parameter(parse(parameter), withDefault = true))
                    if (index < parameters.lastIndex) append(span(DefaultLanguageHighlighterColors.COMMA, ","))
                    append('\n')
                }
            } else {
                parameters.forEach { append(parameter(parse(it), withDefault = true)) }
            }
            append(span(DefaultLanguageHighlighterColors.PARENTHESES, ")"))
        }
        if (suffix != null) append(escape(suffix))
        if (returnType != null) append(escape(": ")).append(type(returnType))
    }

    fun parameter(parts: XfParameterParts, withDefault: Boolean): String = buildString {
        parts.type?.let { append(type(it)).append(' ') }
        if (parts.prefix.isNotEmpty()) append(span(DefaultLanguageHighlighterColors.OPERATION_SIGN, parts.prefix))
        append(span(DefaultLanguageHighlighterColors.PARAMETER, parts.name))
        if (withDefault && parts.default != null) {
            append(' ').append(span(DefaultLanguageHighlighterColors.OPERATION_SIGN, "=")).append(' ').append(value(parts.default))
        }
    }

    fun type(type: String): String = type.split('|').joinToString(span(DefaultLanguageHighlighterColors.OPERATION_SIGN, "|")) { part ->
        val array = part.endsWith("[]")
        val base = part.removeSuffix("[]")
        val key = if (base.lowercase() in PRIMITIVES) DefaultLanguageHighlighterColors.KEYWORD else DefaultLanguageHighlighterColors.CLASS_REFERENCE
        span(key, base) + if (array) span(DefaultLanguageHighlighterColors.BRACKETS, "[]") else ""
    }

    fun code(project: Project, text: String): String =
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(StringBuilder(), project, XfLanguage, text, SATURATION).toString()

    private fun value(text: String): String = DEFAULT_TOKEN.findAll(text).joinToString("") { match ->
        val token = match.value
        val key = when {
            token.startsWith("'") || token.startsWith("\"") -> DefaultLanguageHighlighterColors.STRING
            token.first().isDigit() || (token.startsWith("-") && token.length > 1) -> DefaultLanguageHighlighterColors.NUMBER
            token.lowercase() in PRIMITIVES -> DefaultLanguageHighlighterColors.KEYWORD
            token == "[" || token == "]" -> DefaultLanguageHighlighterColors.BRACKETS
            token == "," -> DefaultLanguageHighlighterColors.COMMA
            else -> null
        }
        if (key == null) escape(token) else span(key, token)
    }

    private fun span(key: TextAttributesKey, text: String): String =
        HtmlSyntaxInfoUtil.appendStyledSpan(StringBuilder(), key, escape(text), SATURATION).toString()

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
}
