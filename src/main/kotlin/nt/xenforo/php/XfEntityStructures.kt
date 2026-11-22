package nt.xenforo.php

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import java.util.concurrent.ConcurrentHashMap

private const val STRUCTURE_METHOD = "getStructure"
private const val CALLBACK_SEPARATOR = "::"
private const val ENTITY_BASE = "\\XF\\Mvc\\Entity\\Entity"
private const val FINDER_BASE = "\\XF\\Mvc\\Entity\\Finder"
private const val SEPARATOR = '\\'
private const val CLASS_CONSTANT = "class"
private const val HIERARCHY_LIMIT = 30

enum class XfEntityFieldKind { COLUMN, GETTER, RELATION }

data class XfEntityField(val name: String, val kind: XfEntityFieldKind, val type: String?, val entity: String?, val element: PsiElement)

private val STRUCTURES = Key.create<CachedValue<ConcurrentHashMap<String, Map<String, XfEntityField>>>>("nt.xenforo.entityStructures")

private val COLUMN_TYPES = mapOf(
    "INT" to "int", "UINT" to "int", "FLOAT" to "float", "BOOL" to "bool", "STR" to "string", "BINARY" to "string",
    "SERIALIZED" to "mixed", "SERIALIZED_ARRAY" to "array", "JSON" to "mixed", "JSON_ARRAY" to "array",
    "LIST_LINES" to "array", "LIST_COMMA" to "array", "LIST_ARRAY" to "array",
)

object XfEntityStructures {
    fun of(project: Project, entity: String): Map<String, XfEntityField> {
        if (DumbService.isDumb(project)) return emptyMap()
        val cache = CachedValuesManager.getManager(project).getCachedValue(project, STRUCTURES, {
            CachedValueProvider.Result.create(ConcurrentHashMap(), *XfDataTracker.php(project))
        }, false)
        val fqn = normalize(entity)
        return cache.getOrPut(fqn) { compute(project, fqn) }
    }

    fun shortName(name: String): String? {
        val colon = name.indexOf(':')
        if (colon <= 0 || colon == name.length - 1) return null
        val prefix = name.substring(0, colon).replace('/', SEPARATOR)
        return "$SEPARATOR$prefix${SEPARATOR}Entity$SEPARATOR${name.substring(colon + 1).replace('/', SEPARATOR)}"
    }

    fun entityForFinder(project: Project, finder: String): String? {
        val fqn = normalize(finder)
        if (!fqn.contains("${SEPARATOR}Finder$SEPARATOR")) return null
        val entityPath = fqn.replace("${SEPARATOR}Finder$SEPARATOR", "${SEPARATOR}Entity$SEPARATOR")
        val index = PhpIndex.getInstance(project)
        return listOf(entityPath.removeSuffix("Finder"), entityPath).firstOrNull { it.length > 1 && index.getClassesByFQN(it).isNotEmpty() }
    }

    fun isEntity(project: Project, fqn: String): Boolean = isSubclass(project, fqn, ENTITY_BASE)

    fun isFinder(project: Project, fqn: String): Boolean = isSubclass(project, fqn, FINDER_BASE)

    fun classValue(element: PsiElement?): String? = when (element) {
        is StringLiteralExpression -> element.contents.takeIf { it.isNotEmpty() }?.let { shortName(it) ?: normalize(it) }
        is ClassConstantReference -> if (element.name == CLASS_CONSTANT) (element.classReference as? ClassReference)?.fqn else null
        else -> null
    }

    private fun isSubclass(project: Project, fqn: String, base: String): Boolean {
        var current: PhpClass? = PhpIndex.getInstance(project).getClassesByFQN(normalize(fqn)).firstOrNull()
        var depth = 0
        while (current != null && depth++ < HIERARCHY_LIMIT) {
            if (current.fqn == base) return true
            current = current.superClass
        }
        return false
    }

    private fun compute(project: Project, entity: String): Map<String, XfEntityField> {
        val index = PhpIndex.getInstance(project)
        val base = entity.trimStart(SEPARATOR)
        val extensions = XfClassExtensions.all(project).filterValues { it.trimStart(SEPARATOR) == base }.keys
        val classes = (listOf(entity) + extensions.map { SEPARATOR + it }).flatMap { index.getClassesByFQN(it) }
        val methods = classes.mapNotNull { it.findOwnMethodByName(STRUCTURE_METHOD) } + listenerMethods(project, base)
        val result = LinkedHashMap<String, XfEntityField>()
        for (method in methods) {
            for (assignment in PsiTreeUtil.findChildrenOfType(method, AssignmentExpression::class.java)) {
                when (val target = assignment.variable) {
                    is FieldReference -> {
                        val kind = kind(target.name) ?: continue
                        val array = assignment.value as? ArrayCreationExpression ?: continue
                        for (element in array.hashElements) {
                            val key = element.key as? StringLiteralExpression ?: continue
                            add(result, field(key, kind, element.value))
                        }
                    }
                    is ArrayAccessExpression -> {
                        val kind = kind((target.value as? FieldReference)?.name) ?: continue
                        val key = target.index?.value as? StringLiteralExpression ?: continue
                        add(result, field(key, kind, assignment.value))
                    }
                }
            }
        }
        return result
    }

    private fun listenerMethods(project: Project, entity: String): List<Method> {
        val index = PhpIndex.getInstance(project)
        val prefix = XfSymbolKeys.structureListenerPrefix(entity)
        return XfDataTracker.keys(project, prefix).mapNotNull { key ->
            val callback = key.removePrefix(prefix)
            val className = callback.substringBefore(CALLBACK_SEPARATOR)
            val methodName = callback.substringAfter(CALLBACK_SEPARATOR, "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            index.getClassesByFQN(normalize(className)).firstNotNullOfOrNull { it.findMethodByName(methodName) }
        }
    }

    private fun add(result: MutableMap<String, XfEntityField>, field: XfEntityField?) {
        if (field != null && field.name.isNotEmpty()) result.putIfAbsent(field.name, field)
    }

    private fun kind(name: String?): XfEntityFieldKind? = when (name) {
        "columns" -> XfEntityFieldKind.COLUMN
        "getters" -> XfEntityFieldKind.GETTER
        "relations" -> XfEntityFieldKind.RELATION
        else -> null
    }

    private fun field(key: StringLiteralExpression, kind: XfEntityFieldKind, value: PsiElement?): XfEntityField? {
        val options = (value as? ArrayCreationExpression)?.hashElements?.associate {
            (it.key as? StringLiteralExpression)?.contents to it.value
        }.orEmpty()
        return when (kind) {
            XfEntityFieldKind.COLUMN -> {
                val type = (options["type"] as? ClassConstantReference)?.name?.let { COLUMN_TYPES[it] }
                XfEntityField(key.contents, kind, type, null, key)
            }
            XfEntityFieldKind.RELATION -> XfEntityField(key.contents, kind, null, classValue(options["entity"]), key)
            XfEntityFieldKind.GETTER -> XfEntityField(key.contents, kind, null, null, key)
        }
    }

    private fun normalize(fqn: String): String = if (fqn.startsWith(SEPARATOR)) fqn else SEPARATOR + fqn
}
