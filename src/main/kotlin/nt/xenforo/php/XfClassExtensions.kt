package nt.xenforo.php

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.php.PhpIndex
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import java.util.TreeMap

private const val SEPARATOR = '\\'
private const val PROXY_PREFIX = "XFCP_"
private const val ADD_ON_SEPARATOR = '/'
private val EXTENSIONS = Key.create<CachedValue<Map<String, String>>>("nt.xenforo.classExtensions")

object XfClassExtensions {
    fun all(project: Project): Map<String, String> = CachedValuesManager.getManager(project).getCachedValue(project, EXTENSIONS, {
        CachedValueProvider.Result.create(compute(project), *XfDataTracker.files(project))
    }, false)

    private fun compute(project: Project): Map<String, String> {
        val prefixes = addOnPrefixes(project)
        val result = TreeMap<String, String>()
        for (key in XfDataTracker.keys(project, XfSymbolKeys.EXTENSION_PREFIX)) {
            val extension = key.removePrefix(XfSymbolKeys.EXTENSION_PREFIX)
            baseClass(project, extension, prefixes)?.let { result[extension] = it }
        }
        return result
    }

    fun baseClass(project: Project, extension: String): String? = baseClass(project, extension, addOnPrefixes(project))

    fun extensionClass(addOnId: String, baseClass: String): String =
        addOnId.replace(ADD_ON_SEPARATOR, SEPARATOR) + SEPARATOR + baseClass.trimStart(SEPARATOR)

    fun stub(extensions: Map<String, String>): String = buildString {
        append("<?php\n")
        val byNamespace = extensions.entries.groupBy { it.key.substringBeforeLast(SEPARATOR, "") }.toSortedMap()
        for ((namespace, entries) in byNamespace) {
            append("\nnamespace")
            if (namespace.isNotEmpty()) append(' ').append(namespace)
            append("\n{\n")
            for ((extension, base) in entries.sortedBy { it.key }) {
                append('\t').append("class ").append(PROXY_PREFIX).append(extension.substringAfterLast(SEPARATOR))
                append(" extends ").append(SEPARATOR).append(base.trimStart(SEPARATOR)).append("\n\t{\n\t}\n")
            }
            append("}\n")
        }
    }

    private fun baseClass(project: Project, extension: String, prefixes: List<String>): String? {
        val locations = XfSymbols.locations(project, XfSymbolKeys.extension(extension))
        if (locations.isEmpty()) return null
        locations.firstNotNullOfOrNull { location -> location.symbol.detail.takeIf { it.isNotEmpty() } }?.let { return it }
        prefixes.firstOrNull { extension.startsWith(it) }?.let { return extension.removePrefix(it) }
        val parts = extension.split(SEPARATOR)
        val index = PhpIndex.getInstance(project)
        return listOf(2, 1).filter { parts.size > it + 1 }
            .map { parts.drop(it).joinToString(SEPARATOR.toString()) }
            .firstOrNull { index.getClassesByFQN(SEPARATOR + it).isNotEmpty() }
    }

    private fun addOnPrefixes(project: Project): List<String> =
        XfDataTracker.keys(project, XfSymbolKeys.ADD_ON_PREFIX)
            .map { it.removePrefix(XfSymbolKeys.ADD_ON_PREFIX).replace(ADD_ON_SEPARATOR, SEPARATOR) + SEPARATOR }
            .sortedByDescending { it.length }
}
