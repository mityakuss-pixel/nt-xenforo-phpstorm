package nt.xenforo.model

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import nt.xenforo.index.XfOptionDetail
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.meta.XfGlobal
import nt.xenforo.meta.XfGlobals
import nt.xenforo.psi.XfAccessExpression

sealed interface XfValueType {
    data object Globals : XfValueType
    data object Options : XfValueType
    data object AddOns : XfValueType
    data class Keys(val keys: List<String>) : XfValueType
    data class PhpObject(val classes: List<String>) : XfValueType
}

enum class XfMemberKind { GLOBAL, KEY, OPTION, ADD_ON, PROPERTY, METHOD }

class XfMember(
    val name: String,
    val kind: XfMemberKind,
    typeText: () -> String?,
    val parameters: String? = null,
    element: () -> PsiElement? = { null },
    resultType: () -> XfValueType? = { null },
) {
    val typeText: String? by lazy(LazyThreadSafetyMode.NONE, typeText)
    val element: PsiElement? by lazy(LazyThreadSafetyMode.NONE, element)
    val resultType: XfValueType? by lazy(LazyThreadSafetyMode.NONE, resultType)
}

object XfMembers {
    fun of(project: Project, type: XfValueType): List<XfMember> {
        if (needsIndex(type) && DumbService.isDumb(project)) return emptyList()
        return when (type) {
            XfValueType.Globals -> XfGlobals.ALL.map { global(project, it) }
            XfValueType.Options -> XfSymbols.keys(project, XfSymbolKeys.OPTION_PREFIX).map { option(project, it.removePrefix(XfSymbolKeys.OPTION_PREFIX)) }
            XfValueType.AddOns -> XfSymbols.keys(project, XfSymbolKeys.ADD_ON_PREFIX)
                .map { it.removePrefix(XfSymbolKeys.ADD_ON_PREFIX) }
                .filter { '/' !in it }
                .map { addOn(project, it) }
            is XfValueType.Keys -> type.keys.map(::key)
            is XfValueType.PhpObject -> XfPhpMembers.of(project, type)
        }
    }

    fun find(project: Project, type: XfValueType, name: String, isCall: Boolean): XfMember? {
        if (needsIndex(type) && DumbService.isDumb(project)) return null
        if (isCall && type !is XfValueType.PhpObject) return null
        return when (type) {
            XfValueType.Globals -> XfGlobals.find(name)?.let { global(project, it) }
            XfValueType.Options -> option(project, name).takeIf { XfSymbols.locations(project, XfSymbolKeys.option(name)).isNotEmpty() }
            XfValueType.AddOns -> addOn(project, name).takeIf { XfSymbols.locations(project, XfSymbolKeys.addOn(name)).isNotEmpty() }
            is XfValueType.Keys -> key(name).takeIf { name in type.keys }
            is XfValueType.PhpObject -> XfPhpMembers.find(project, type, name, isCall)
        }
    }

    private fun needsIndex(type: XfValueType): Boolean = type !is XfValueType.Globals && type !is XfValueType.Keys

    private fun global(project: Project, global: XfGlobal): XfMember = XfMember(
        global.name,
        XfMemberKind.GLOBAL,
        typeText = { global.typeText },
        element = { XfPhpMembers.method(project, global.source.className, global.source.methodName) },
        resultType = { global.type },
    )

    private fun option(project: Project, id: String): XfMember {
        val key = XfSymbolKeys.option(id)
        val detail by lazy(LazyThreadSafetyMode.NONE) { XfSymbols.detail(project, key).orEmpty() }
        return XfMember(
            id,
            XfMemberKind.OPTION,
            typeText = { XfOptionDetail.dataType(detail).ifEmpty { null } },
            element = { XfSymbols.targets(project, key, id).firstOrNull() },
            resultType = { XfOptionDetail.subOptions(detail).takeIf { it.isNotEmpty() }?.let { XfValueType.Keys(it) } },
        )
    }

    private fun addOn(project: Project, id: String): XfMember {
        val key = XfSymbolKeys.addOn(id)
        return XfMember(id, XfMemberKind.ADD_ON, typeText = { "int" }, element = { XfSymbols.targets(project, key, id).firstOrNull() })
    }

    private fun key(name: String): XfMember = XfMember(name, XfMemberKind.KEY, typeText = { null })
}

object XfAccess {
    fun rootType(variableName: String): XfValueType? = if (variableName == XfGlobals.ROOT) XfValueType.Globals else null

    fun ownerType(project: Project, expression: XfAccessExpression, index: Int): XfValueType? {
        var type = rootType(expression.variableName) ?: return null
        val segments = expression.segments
        for (position in 0 until index) {
            val segment = segments.getOrNull(position) ?: return null
            val name = segment.name ?: return null
            type = XfMembers.find(project, type, name, segment.isCall)?.resultType ?: return null
        }
        return type
    }

    fun member(project: Project, expression: XfAccessExpression, index: Int): XfMember? {
        val segment = expression.segments.getOrNull(index) ?: return null
        val name = segment.name ?: return null
        val owner = ownerType(project, expression, index) ?: return null
        return XfMembers.find(project, owner, name, segment.isCall)
    }

    fun segmentIndex(expression: XfAccessExpression, element: PsiElement): Int =
        expression.segments.indexOfFirst { it.element == element }
}
