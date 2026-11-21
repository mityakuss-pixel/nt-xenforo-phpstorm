package nt.xenforo.reference

import com.intellij.openapi.util.TextRange
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.psi.XfArguments
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfFile
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfStringExpr
import nt.xenforo.psi.XfTypes

enum class XfNameKind { TEMPLATE, MACRO, PHRASE, ROUTE, PROPERTY }

data class XfNameSlot(
    val kind: XfNameKind,
    val range: TextRange,
    val keyPrefix: String,
    val lessOnly: Boolean = false,
) {
    fun name(host: XfReferenceHost): String = range.substring(host.text)

    fun keys(host: XfReferenceHost): List<String> {
        val name = name(host)
        if (kind != XfNameKind.ROUTE) return listOf(keyPrefix + name)
        val parts = name.split('/')
        return if (parts.size > 1) listOf(keyPrefix + parts[0] + "/" + parts[1], keyPrefix + parts[0]) else listOf(keyPrefix + name)
    }
}

private val ROUTE_MODIFIER = Regex("""^[a-z_]+:""")
private val INCLUDE_TAGS = setOf("include", "wrap", "extends")

object XfNameSlots {
    fun of(host: XfReferenceHost): List<XfNameSlot> = when (host) {
        is XfStringExpr -> stringSlots(host)
        else -> attributeSlots(host)
    }

    private fun attributeSlots(host: XfReferenceHost): List<XfNameSlot> {
        if (!host.node.getChildren(null).all { it.elementType == XfTypes.ATTR_QUOTE || it.elementType == XfTypes.ATTR_TEXT }) return emptyList()
        val attribute = host.parent as? XfAttribute ?: return emptyList()
        val header = attribute.header ?: return emptyList()
        val tag = header.tagName.lowercase()
        val name = attribute.attributeName.lowercase()
        val range = host.valueRange
        val value = host.value
        val type = currentType(host)
        return when {
            tag in INCLUDE_TAGS && name == "template" -> listOfNotNull(templateSlot(value, range.startOffset, type))
            tag == "macro" && name == "template" -> listOfNotNull(templateSlot(value, range.startOffset, type))
            tag == "macro" && name == "id" -> macroSlots(value, range.startOffset, type, currentTitle(host))
            tag == "macro" && name == "name" -> {
                val template = header.findAttribute("template")?.attributeValue?.value
                listOf(XfNameSlot(XfNameKind.MACRO, range, XfSymbolKeys.macroPrefix(type, template ?: currentTitle(host))))
            }
            tag == "css" && name == "src" -> cssSlots(value, range.startOffset, type)
            else -> emptyList()
        }
    }

    private fun templateSlot(value: String, start: Int, defaultType: String, lessOnly: Boolean = false): XfNameSlot? {
        val (type, nameStart) = splitType(value, defaultType)
        if (nameStart > value.length) return null
        return XfNameSlot(XfNameKind.TEMPLATE, TextRange(start + nameStart, start + value.length), XfSymbolKeys.templatePrefix(type), lessOnly)
    }

    private fun macroSlots(value: String, start: Int, defaultType: String, currentTitle: String): List<XfNameSlot> {
        val separator = value.indexOf("::")
        if (separator < 0) {
            return listOf(XfNameSlot(XfNameKind.MACRO, TextRange(start, start + value.length), XfSymbolKeys.macroPrefix(defaultType, currentTitle)))
        }
        val templatePart = value.substring(0, separator)
        val (type, nameStart) = splitType(templatePart, defaultType)
        val template = templatePart.substring(nameStart)
        return listOf(
            XfNameSlot(XfNameKind.TEMPLATE, TextRange(start + nameStart, start + separator), XfSymbolKeys.templatePrefix(type)),
            XfNameSlot(XfNameKind.MACRO, TextRange(start + separator + 2, start + value.length), XfSymbolKeys.macroPrefix(type, template)),
        )
    }

    private fun cssSlots(value: String, start: Int, defaultType: String): List<XfNameSlot> {
        val slots = ArrayList<XfNameSlot>()
        var offset = 0
        for (part in value.split(',')) {
            val trimmedStart = part.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: part.length
            val trimmed = part.trim()
            templateSlot(trimmed, start + offset + trimmedStart, defaultType, lessOnly = true)?.let { slots += it }
            offset += part.length + 1
        }
        return slots
    }

    private fun splitType(value: String, defaultType: String): Pair<String, Int> {
        val colon = value.indexOf(':')
        if (colon > 0 && value.substring(0, colon) in XfTemplateFiles.TEMPLATE_TYPES && !value.startsWith("::", colon)) {
            return value.substring(0, colon) to colon + 1
        }
        return defaultType to 0
    }

    private fun stringSlots(host: XfStringExpr): List<XfNameSlot> {
        val arguments = host.parent as? XfArguments ?: return emptyList()
        val call = arguments.parent as? XfFunctionCallExpr ?: return emptyList()
        val index = arguments.exprList.indexOf(host)
        val range = host.valueRange
        val value = host.value
        val type = currentType(host)
        return when (call.callName.lowercase() to index) {
            "phrase" to 0, "phrase_dynamic" to 0, "is_phrase" to 0 ->
                listOf(XfNameSlot(XfNameKind.PHRASE, range, XfSymbolKeys.PHRASE_PREFIX))
            "property" to 0, "property_variation" to 0 ->
                listOf(XfNameSlot(XfNameKind.PROPERTY, range, XfSymbolKeys.PROPERTY_PREFIX))
            "include" to 0, "call_macro" to 0 ->
                listOfNotNull(templateSlot(value, range.startOffset, type))
            "call_macro" to 1 -> {
                val template = (arguments.exprList.firstOrNull() as? XfStringExpr)?.value ?: return emptyList()
                val (templateType, nameStart) = splitType(template, type)
                listOf(XfNameSlot(XfNameKind.MACRO, range, XfSymbolKeys.macroPrefix(templateType, template.substring(nameStart))))
            }
            "link" to 0 -> listOf(routeSlot(value, range.startOffset, if (type == "admin") "admin" else "public"))
            "link_type" to 1 -> {
                val routeType = (arguments.exprList.firstOrNull() as? XfStringExpr)?.value ?: "public"
                listOf(routeSlot(value, range.startOffset, routeType))
            }
            else -> emptyList()
        }
    }

    private fun routeSlot(value: String, start: Int, type: String): XfNameSlot {
        val modifier = ROUTE_MODIFIER.find(value)?.value?.length ?: 0
        return XfNameSlot(XfNameKind.ROUTE, TextRange(start + modifier, start + value.length), XfSymbolKeys.routePrefix(type))
    }

    private fun currentType(host: XfReferenceHost): String =
        (host.containingFile.originalFile as? XfFile ?: host.containingFile as? XfFile)?.templateType ?: XfTemplateFiles.DEFAULT_TYPE

    private fun currentTitle(host: XfReferenceHost): String =
        (host.containingFile.originalFile as? XfFile ?: host.containingFile as? XfFile)?.templateTitle.orEmpty()
}
