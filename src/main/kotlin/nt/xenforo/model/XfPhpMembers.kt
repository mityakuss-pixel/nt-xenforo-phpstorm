package nt.xenforo.model

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocProperty
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

object XfPhpMembers {
    private const val ENTITY_BASE = "\\XF\\Mvc\\Entity\\Entity"
    private const val COLUMN_TAG = "@property"
    private const val NULL_TYPE = "null"
    private const val DEFAULT_VALUE_LIMIT = 40

    private val HIDDEN_METHODS = setOf("offsetGet", "offsetSet", "offsetExists", "offsetUnset", "getIterator", "jsonSerialize")

    private val ROW_ENTITIES = mapOf(
        "\\XF\\Language" to "\\XF\\Entity\\Language",
        "\\XF\\Style" to "\\XF\\Entity\\Style",
    )

    fun of(project: Project, type: XfValueType.PhpObject): List<XfMember> {
        if (DumbService.isDumb(project)) return emptyList()
        val members = LinkedHashMap<String, XfMember>()
        val classes = classes(project, type.classes)
        for (phpClass in classes) {
            for (field in phpClass.fields) {
                if (isVisible(field)) members.putIfAbsent(field.name, property(project, field))
            }
        }
        for (phpClass in classes(project, rowEntities(type))) {
            for (field in phpClass.fields) {
                if (isColumn(field)) members.putIfAbsent(field.name, property(project, field))
            }
        }
        for (phpClass in classes) {
            for (method in phpClass.methods) {
                if (isVisible(method)) members.putIfAbsent(method.name + "()", method(project, method))
            }
        }
        return members.values.toList()
    }

    fun find(project: Project, type: XfValueType.PhpObject, name: String, isCall: Boolean): XfMember? {
        if (DumbService.isDumb(project)) return null
        val classes = classes(project, type.classes)
        if (isCall) {
            return classes.firstNotNullOfOrNull { phpClass -> phpClass.findMethodByName(name)?.takeIf(::isVisible) }?.let { method(project, it) }
        }
        classes.firstNotNullOfOrNull { phpClass -> phpClass.findFieldByName(name, false)?.takeIf(::isVisible) }?.let { return property(project, it) }
        return classes(project, rowEntities(type))
            .firstNotNullOfOrNull { phpClass -> phpClass.findFieldByName(name, false)?.takeIf(::isColumn) }
            ?.let { property(project, it) }
    }

    fun method(project: Project, className: String, methodName: String): PsiElement? {
        if (DumbService.isDumb(project)) return null
        return PhpIndex.getInstance(project).getClassesByFQN(className).firstNotNullOfOrNull { it.findOwnMethodByName(methodName) }
    }

    fun presentable(type: PhpType?): String? {
        if (type == null || type.isEmpty) return null
        return type.filterUnknown().typesSorted
            .filter { !it.startsWith("#") }
            .map { it.removePrefix("\\").substringAfterLast('\\') }
            .distinct()
            .sortedBy { it == NULL_TYPE }
            .joinToString("|")
            .ifEmpty { null }
    }

    fun parameterText(parameter: Parameter): String = buildString {
        val type = parameter.declaredType.takeUnless { it.isEmpty } ?: parameter.docType
        presentable(type)?.let { append(it).append(' ') }
        if (parameter.isPassByRef) append('&')
        if (parameter.isVariadic) append("...")
        append('$').append(parameter.name)
        val defaultValue = parameter.defaultValue?.text?.takeIf { it.length <= DEFAULT_VALUE_LIMIT && '\n' !in it }
            ?: parameter.defaultValuePresentation
        defaultValue?.takeIf { it.isNotEmpty() }?.let { append(" = ").append(it) }
    }

    fun presentableTypeText(type: String): String =
        type.split('|').joinToString("|") { it.trim().removePrefix("\\").substringAfterLast('\\') }

    private fun classes(project: Project, names: List<String>): List<PhpClass> {
        val index = PhpIndex.getInstance(project)
        return names.flatMap { index.getClassesByFQN(it) }
    }

    private fun rowEntities(type: XfValueType.PhpObject): List<String> = type.classes.mapNotNull { ROW_ENTITIES[it] }

    private fun isVisible(field: Field): Boolean {
        if (field.isConstant) return false
        if (field is PhpDocProperty) return true
        return field.modifier.isPublic && !field.modifier.isStatic
    }

    private fun isColumn(field: Field): Boolean =
        field is PhpDocProperty && PsiTreeUtil.getParentOfType(field, PhpDocTag::class.java)?.name == COLUMN_TAG

    private fun isVisible(method: Method): Boolean =
        method.access.isPublic &&
            !method.isStatic &&
            !method.name.startsWith("__") &&
            method.name !in HIDDEN_METHODS &&
            method.containingClass?.fqn != ENTITY_BASE

    private fun property(project: Project, field: Field): XfMember = XfMember(
        field.name,
        XfMemberKind.PROPERTY,
        typeText = { presentable(field.type) },
        element = { field },
        resultType = { objectType(project, field.type) },
    )

    private fun method(project: Project, method: Method): XfMember = XfMember(
        method.name,
        XfMemberKind.METHOD,
        typeText = { presentable(method.declaredType.takeUnless { it.isEmpty } ?: method.docType) },
        parameters = method.parameters.joinToString(", ", transform = ::parameterText),
        element = { method },
        resultType = { objectType(project, method.type) },
    )

    private fun objectType(project: Project, type: PhpType): XfValueType? {
        val classes = type.global(project).filterPrimitives().filterNull().filterMixed().types
            .filter { it.startsWith("\\") && !it.endsWith("[]") }
        return if (classes.isEmpty()) null else XfValueType.PhpObject(classes)
    }
}
