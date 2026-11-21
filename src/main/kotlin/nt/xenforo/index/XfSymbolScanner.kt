package nt.xenforo.index

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import nt.xenforo.lang.XfTemplateFiles

private const val DETAIL_LIMIT = 160
private const val METADATA_FILE = "_metadata.json"
private const val CDATA_START = "<![CDATA["

private val DATA_FILES = setOf("templates.xml", "phrases.xml", "routes.xml", "style_properties.xml")
private val OUTPUT_FOLDERS = setOf("phrases", "routes", "style_properties")

private val XML_ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*"([^"]*)"""")
private val MACRO_TAG = Regex("""<xf:macro\b((?:\s+[\w:-]+\s*=\s*"[^"]*")*)\s*(/?)>""")
private val XML_TEMPLATE = Regex("""<template\s([^>]*?)(/?)>""")
private val XML_PHRASE = Regex("""<phrase\s([^>]*?)(/>|>)""")
private val XML_ROUTE = Regex("""<route\s([^>]*?)/?>""")
private val XML_STYLE_PROPERTY = Regex("""<style_property\s([^>]*?)/?>""")
private val JSON_FIELD = Regex(""""(\w+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")

object XfSymbolScanner {
    fun isIndexed(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val name = file.name
        if (name == METADATA_FILE) return false
        val parent = file.parent ?: return false
        if (parent.name == "_data") return name in DATA_FILES
        if (XfTemplateFiles.templateType(file) != null) return file.hasAncestorChain(parent.name, "templates", "_output")
        return parent.name in OUTPUT_FOLDERS && parent.parent?.name == "_output"
    }

    fun scan(file: VirtualFile, text: CharSequence): Map<String, XfSymbol> {
        val result = HashMap<String, XfSymbol>()
        val parent = file.parent ?: return result
        when {
            parent.name == "_data" -> when (file.name) {
                "templates.xml" -> scanTemplatesXml(text, result)
                "phrases.xml" -> scanPhrasesXml(text, result)
                "routes.xml" -> scanRoutesXml(text, result)
                "style_properties.xml" -> scanStylePropertiesXml(text, result)
            }
            XfTemplateFiles.templateType(file) != null -> {
                val type = parent.name
                val title = XfTemplateFiles.templateTitle(file)
                result[XfSymbolKeys.template(type, title)] = XfSymbol(0, "")
                if (file.name.endsWith(".html", ignoreCase = true)) scanMacros(text, 0, text.length, type, title, result)
            }
            parent.name == "phrases" -> result[XfSymbolKeys.phrase(file.nameWithoutExtension)] = XfSymbol(0, detail(text))
            parent.name == "routes" -> scanRouteJson(text, result)
            parent.name == "style_properties" -> {
                val fields = jsonFields(text)
                result[XfSymbolKeys.property(file.nameWithoutExtension)] = XfSymbol(0, detail(fields["title"].orEmpty()))
            }
        }
        return result
    }

    private fun scanTemplatesXml(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        for (match in XML_TEMPLATE.findAll(text)) {
            val attributes = xmlAttributes(match.groupValues[1])
            val type = attributes["type"] ?: continue
            val title = attributes["title"] ?: continue
            val tagEnd = match.range.last + 1
            val bodyStart = if (StringUtil.startsWith(text, tagEnd, CDATA_START)) tagEnd + CDATA_START.length else tagEnd
            result[XfSymbolKeys.template(type, title)] = XfSymbol(bodyStart, attributes["addon_id"].orEmpty())
            if (match.groupValues[2] == "/" || title.contains('.')) continue
            val bodyEnd = StringUtil.indexOf(text, "</template>", bodyStart).takeIf { it >= 0 } ?: text.length
            scanMacros(text, bodyStart, bodyEnd, type, title, result)
        }
    }

    private fun scanMacros(text: CharSequence, start: Int, end: Int, type: String, title: String, result: MutableMap<String, XfSymbol>) {
        for (match in MACRO_TAG.findAll(text.subSequence(start, end))) {
            if (match.groupValues[2] == "/") continue
            val attributes = xmlAttributes(match.groupValues[1])
            val id = attributes["id"] ?: attributes["name"] ?: continue
            if (id.contains("::")) continue
            val arguments = attributes.keys.filter { it.startsWith("arg-") }.joinToString(", ") { it.removePrefix("arg-") }
            result.putIfAbsent(XfSymbolKeys.macro(type, title, id), XfSymbol(start + match.range.first, arguments))
        }
    }

    private fun scanPhrasesXml(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        for (match in XML_PHRASE.findAll(text)) {
            val title = xmlAttributes(match.groupValues[1])["title"] ?: continue
            val bodyStart = match.range.last + 1
            val value = if (match.groupValues[2] == "/>") "" else phraseValue(text, bodyStart)
            result[XfSymbolKeys.phrase(title)] = XfSymbol(match.range.first, detail(value))
        }
    }

    private fun phraseValue(text: CharSequence, start: Int): String {
        val end = StringUtil.indexOf(text, "</phrase>", start).takeIf { it >= 0 } ?: return ""
        val raw = text.subSequence(start, end).toString()
        return if (raw.startsWith(CDATA_START) && raw.endsWith("]]>")) raw.substring(CDATA_START.length, raw.length - 3) else StringUtil.unescapeXmlEntities(raw)
    }

    private fun scanRoutesXml(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        for (match in XML_ROUTE.findAll(text)) {
            val attributes = xmlAttributes(match.groupValues[1])
            addRoute(attributes, match.range.first, result)
        }
    }

    private fun scanRouteJson(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        addRoute(jsonFields(text), 0, result)
    }

    private fun addRoute(fields: Map<String, String>, offset: Int, result: MutableMap<String, XfSymbol>) {
        val type = fields["route_type"] ?: return
        val prefix = fields["route_prefix"] ?: return
        val subName = fields["sub_name"].orEmpty()
        val route = if (subName.isEmpty()) prefix else "$prefix/$subName"
        result[XfSymbolKeys.route(type, route)] = XfSymbol(offset, fields["controller"].orEmpty())
    }

    private fun scanStylePropertiesXml(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        for (match in XML_STYLE_PROPERTY.findAll(text)) {
            val attributes = xmlAttributes(match.groupValues[1])
            val name = attributes["property_name"] ?: continue
            result[XfSymbolKeys.property(name)] = XfSymbol(match.range.first, detail(attributes["title"].orEmpty()))
        }
    }

    private fun xmlAttributes(text: String): Map<String, String> =
        XML_ATTRIBUTE.findAll(text).associate { it.groupValues[1] to StringUtil.unescapeXmlEntities(it.groupValues[2]) }

    private fun jsonFields(text: CharSequence): Map<String, String> =
        JSON_FIELD.findAll(text).associate { it.groupValues[1] to StringUtil.unescapeStringCharacters(it.groupValues[2]) }

    private fun detail(text: CharSequence): String {
        val collapsed = text.toString().replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length > DETAIL_LIMIT) collapsed.substring(0, DETAIL_LIMIT) + "…" else collapsed
    }
}
