package nt.xenforo.php

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import nt.xenforo.XfBundle.message
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfLanguage
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.model.XfPhpMembers
import nt.xenforo.model.XfValueType

private const val VIEW_METHOD = "view"
private const val PARAMS_INDEX = 2

data class XfTemplateParam(val name: String, val type: PhpType, val element: PsiElement)

class XfTemplateData(val calls: List<MethodReference>, val params: Map<String, List<XfTemplateParam>>) {
    val types = ConcurrentHashMap<String, Optional<XfValueType>>()
    val presentable = ConcurrentHashMap<String, Optional<String>>()

    companion object {
        val EMPTY = XfTemplateData(emptyList(), emptyMap())
    }
}

object XfTemplateParams {
    fun calls(project: Project, type: String, title: String): List<MethodReference> {
        if (DumbService.isDumb(project)) return emptyList()
        val manager = PsiManager.getInstance(project)
        return XfSymbols.locations(project, XfSymbolKeys.view(type, title)).flatMap { location ->
            val psi = location.file.takeIf { it.isValid }?.let(manager::findFile) ?: return@flatMap emptyList()
            PsiTreeUtil.findChildrenOfType(psi, MethodReference::class.java).filter { isViewOf(it, type, title) }
        }
    }

    fun data(file: PsiFile): XfTemplateData {
        val base = file.originalFile.viewProvider.getPsi(XfLanguage) ?: return XfTemplateData.EMPTY
        return CachedValuesManager.getCachedValue(base) {
            CachedValueProvider.Result.create(compute(base), *XfDataTracker.php(base.project))
        }
    }

    fun of(file: PsiFile): Map<String, List<XfTemplateParam>> = data(file).params

    fun type(file: PsiFile, name: String): XfValueType? {
        val data = data(file)
        val params = data.params[name] ?: return null
        val project = file.project
        return data.types.getOrPut(name) {
            val classes = params.flatMap { param ->
                (XfPhpMembers.objectType(project, param.type) as? XfValueType.PhpObject)?.classes.orEmpty()
            }.distinct()
            Optional.ofNullable(if (classes.isEmpty()) null else XfValueType.PhpObject(classes))
        }.orElse(null)
    }

    fun presentableType(file: PsiFile, name: String): String? {
        val data = data(file)
        val params = data.params[name] ?: return null
        val project = file.project
        return data.presentable.getOrPut(name) {
            Optional.ofNullable(params.mapNotNull { XfPhpMembers.presentable(it.type.global(project)) }.distinct().joinToString("|").ifEmpty { null })
        }.orElse(null)
    }

    fun location(call: MethodReference): String {
        val method = PsiTreeUtil.getParentOfType(call, Method::class.java)
        val owner = method?.containingClass?.fqn?.trimStart('\\')
        return if (owner != null) "$owner::${method.name}()" else call.containingFile.name
    }

    private fun compute(file: PsiFile): XfTemplateData {
        val virtualFile = file.virtualFile ?: return XfTemplateData.EMPTY
        val type = XfTemplateFiles.templateType(virtualFile) ?: return XfTemplateData.EMPTY
        val calls = calls(file.project, type, XfTemplateFiles.templateTitle(virtualFile))
        if (calls.isEmpty()) return XfTemplateData.EMPTY
        val result = LinkedHashMap<String, MutableList<XfTemplateParam>>()
        for (call in calls) {
            for (param in params(call)) result.getOrPut(param.name) { ArrayList() } += param
        }
        return XfTemplateData(calls, result)
    }

    private fun isViewOf(call: MethodReference, type: String, title: String): Boolean {
        if (call.name != VIEW_METHOD) return false
        val literal = call.parameters.getOrNull(1) as? StringLiteralExpression ?: return false
        val slot = XfPhpNames.slot(literal) ?: return false
        return slot.keyPrefix == XfSymbolKeys.templatePrefix(type) && slot.name(literal) == title
    }

    private fun params(call: MethodReference): List<XfTemplateParam> = when (val argument = call.parameters.getOrNull(PARAMS_INDEX)) {
        is ArrayCreationExpression -> entries(argument)
        is Variable -> variableEntries(call, argument)
        else -> emptyList()
    }

    private fun entries(array: ArrayCreationExpression): List<XfTemplateParam> = array.hashElements.mapNotNull { element ->
        val key = (element.key as? StringLiteralExpression)?.contents?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val value = element.value as? PhpTypedElement ?: return@mapNotNull null
        XfTemplateParam(key, value.type, element.key ?: value)
    }

    private fun variableEntries(call: MethodReference, variable: Variable): List<XfTemplateParam> {
        val function = PsiTreeUtil.getParentOfType(call, Function::class.java) ?: return emptyList()
        val name = variable.name
        val result = ArrayList<XfTemplateParam>()
        for (assignment in PsiTreeUtil.findChildrenOfType(function, AssignmentExpression::class.java)) {
            if (assignment.textRange.startOffset > call.textRange.startOffset) continue
            when (val target = assignment.variable) {
                is Variable -> if (target.name == name) (assignment.value as? ArrayCreationExpression)?.let { result += entries(it) }
                is ArrayAccessExpression -> {
                    if ((target.value as? Variable)?.name != name) continue
                    val key = (target.index?.value as? StringLiteralExpression)?.contents?.takeIf { it.isNotEmpty() } ?: continue
                    val value = assignment.value as? PhpTypedElement ?: continue
                    result += XfTemplateParam(key, value.type, target)
                }
            }
        }
        return result
    }
}

class XfTemplateControllerMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("view.marker.name")

    override fun getIcon() = AllIcons.Nodes.Controller

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val first = elements.firstOrNull { it.firstChild == null && it.textRange.startOffset == 0 && it.containingFile?.language == XfLanguage } ?: return
        val file = first.containingFile ?: return
        val virtualFile = file.originalFile.virtualFile ?: return
        val type = XfTemplateFiles.templateType(virtualFile) ?: return
        if (DumbService.isDumb(file.project)) return
        val calls = XfTemplateParams.data(file).calls
        if (calls.isEmpty()) return
        val tooltip = buildString {
            append("<b>").append(StringUtil.escapeXmlEntities(message("view.marker.title"))).append("</b>")
            calls.map(XfTemplateParams::location).distinct().forEach { append("<br>").append(StringUtil.escapeXmlEntities(it)) }
        }
        result += LineMarkerInfo(
            first,
            TextRange(0, minOf(1, first.textLength)),
            AllIcons.Nodes.Controller,
            { tooltip },
            { event, target ->
                val targets = XfTemplateParams.calls(target.project, type, XfTemplateFiles.templateTitle(virtualFile))
                if (targets.size == 1) targets.single().navigate(true) else PsiTargetNavigator(targets).navigate(event, message("view.marker.title"), target.project)
            },
            GutterIconRenderer.Alignment.LEFT,
            { message("view.marker.name") },
        )
    }
}

class XfTemplateControllerGotoProvider : GotoRelatedProvider() {
    override fun getItems(context: PsiElement): List<GotoRelatedItem> {
        val file = context.containingFile ?: return emptyList()
        val virtualFile = file.originalFile.virtualFile ?: return emptyList()
        val type = XfTemplateFiles.templateType(virtualFile) ?: return emptyList()
        return XfTemplateParams.calls(context.project, type, XfTemplateFiles.templateTitle(virtualFile))
            .map { GotoRelatedItem(it, message("view.marker.group")) }
    }
}
