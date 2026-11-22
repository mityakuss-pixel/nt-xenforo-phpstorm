package nt.xenforo.modification

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import nt.xenforo.XfBundle.message
import nt.xenforo.commands.XfAddOn
import nt.xenforo.commands.XfCommandRunner
import nt.xenforo.commands.XfDevAddOns
import nt.xenforo.commands.XfMake
import nt.xenforo.commands.XfOutputFiles
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import java.util.TreeMap
import javax.swing.JComponent

private const val TEMPLATES_XML = "templates.xml"
private const val DATA_FOLDER = "_data"
private const val MAKE_MODIFICATION = "xf-make:template-modification"
private const val IMPORT_MODIFICATIONS = "xf-dev:import-template-modifications"
private const val NO_INTERACTION = "--no-interaction"
private const val DEFAULT_REPLACE = "\$0"
private const val DEFAULT_ORDER = 10
private val KEY = Regex("""[A-Za-z0-9_]+""")
private val TEMPLATE_OPEN = Regex("""<template\s([^>]*?)>""")
private val XML_ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*"([^"]*)"""")

data class XfTemplateSelection(val type: String, val title: String, val find: String, val templateText: CharSequence)

object XfModificationMarkers {
    fun hits(file: PsiFile): TreeMap<Int, List<XfModification>> = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance())
    }

    private fun compute(file: PsiFile): TreeMap<Int, List<XfModification>> {
        val result = TreeMap<Int, MutableList<XfModification>>()
        val project = file.project
        val virtualFile = file.originalFile.virtualFile ?: return TreeMap()
        if (DumbService.isDumb(project)) return TreeMap()
        val type = XfTemplateFiles.templateType(virtualFile)
        if (type != null && XfTemplateFiles.isOutputTemplate(virtualFile)) {
            val text = file.viewProvider.contents
            for (modification in XfModifications.forTemplate(project, type, XfTemplateFiles.templateTitle(virtualFile))) {
                XfModifications.matches(modification.action, modification.find, text)?.forEach { result.getOrPut(it) { ArrayList() } += modification }
            }
        }
        return TreeMap(result)
    }

    fun templatesXmlHits(project: Project, file: VirtualFile): TreeMap<Int, List<XfModification>> {
        val result = TreeMap<Int, MutableList<XfModification>>()
        if (DumbService.isDumb(project)) return TreeMap()
        for ((template, modifications) in XfModifications.all(project).groupBy { it.type to it.template }) {
            val source = XfModifications.templateSource(project, template.first, template.second) ?: continue
            if (source.file != file) continue
            val body = source.body
            for (modification in modifications) {
                XfModifications.matches(modification.action, modification.find, body)?.forEach {
                    result.getOrPut(source.range.startOffset + it) { ArrayList() } += modification
                }
            }
        }
        return TreeMap(result)
    }

    fun tooltip(modifications: List<XfModification>): String = buildString {
        append("<b>").append(StringUtil.escapeXmlEntities(message("modification.marker.title"))).append("</b>")
        for (modification in modifications) {
            append("<br>").append(StringUtil.escapeXmlEntities(modification.key))
            modification.addOnId?.let { append(" (").append(StringUtil.escapeXmlEntities(it)).append(')') }
            if (!modification.enabled) append(", ").append(StringUtil.escapeXmlEntities(message("modification.marker.disabled")))
            if (modification.description.isNotEmpty()) append(": ").append(StringUtil.escapeXmlEntities(modification.description))
        }
    }

    fun targets(project: Project, modifications: List<XfModification>): List<XfSymbolTarget> =
        modifications.map { XfSymbolTarget(project, it.file, it.offset, it.key, message("symbol.type.modification")) }

    fun isTemplatesXml(file: VirtualFile): Boolean = file.name == TEMPLATES_XML && file.parent?.name == DATA_FOLDER
}

class XfModificationLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("modification.marker.name")

    override fun getIcon() = AllIcons.Gutter.OverridenMethod

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
        if (file.viewProvider !is XfFileViewProvider || file.language != XfLanguage) return
        val hits = XfModificationMarkers.hits(file)
        if (hits.isEmpty()) return
        for (element in elements) {
            if (element.firstChild != null) continue
            val range = element.textRange
            for ((offset, modifications) in hits.subMap(range.startOffset, range.endOffset)) {
                result += marker(element, TextRange(offset, minOf(offset + 1, range.endOffset)), modifications)
            }
        }
    }

    private fun marker(element: PsiElement, range: TextRange, modifications: List<XfModification>): LineMarkerInfo<PsiElement> {
        val tooltip = XfModificationMarkers.tooltip(modifications)
        return LineMarkerInfo(
            element,
            range,
            AllIcons.Gutter.OverridenMethod,
            { tooltip },
            { event, target ->
                val targets = XfModificationMarkers.targets(target.project, modifications)
                if (targets.size == 1) targets.single().navigate(true) else PsiTargetNavigator(targets).navigate(event, message("modification.marker.title"), target.project)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { message("modification.marker.name") },
        )
    }
}

object XfModificationCreation {
    fun selection(editor: Editor, file: VirtualFile?): XfTemplateSelection? {
        if (file == null || !editor.selectionModel.hasSelection()) return null
        val document = editor.document
        val start = editor.selectionModel.selectionStart
        val end = editor.selectionModel.selectionEnd
        val find = document.getText(TextRange(start, end))
        if (find.isBlank()) return null
        val type = XfTemplateFiles.templateType(file)
        if (type != null && XfTemplateFiles.isOutputTemplate(file)) {
            return XfTemplateSelection(type, XfTemplateFiles.templateTitle(file), find, document.charsSequence)
        }
        if (!XfModificationMarkers.isTemplatesXml(file)) return null
        val text = document.charsSequence
        val head = text.subSequence(0, start).toString()
        val openStart = head.lastIndexOf("<template ")
        if (openStart < 0) return null
        val open = TEMPLATE_OPEN.find(text, openStart)?.takeIf { it.range.first == openStart } ?: return null
        val close = StringUtil.indexOf(text, "</template>", open.range.last)
        if (close < end) return null
        val attributes = XML_ATTRIBUTE.findAll(open.groupValues[1]).associate { it.groupValues[1] to StringUtil.unescapeXmlEntities(it.groupValues[2]) }
        val templateType = attributes["type"] ?: return null
        val title = attributes["title"] ?: return null
        return XfTemplateSelection(templateType, title, find, text.subSequence(open.range.last + 1, close))
    }

    fun show(project: Project, selection: XfTemplateSelection, file: VirtualFile?) {
        val addOns = XfDevAddOns.list(project)
        if (addOns.isEmpty()) {
            Messages.showErrorDialog(project, message("extend.error.addons"), message("modification.dialog.title"))
            return
        }
        val selected = XfDevAddOns.preferred(project, addOns, file)
        val dialog = XfModificationDialog(project, selection, addOns, selected)
        if (!dialog.showAndGet()) return
        val addOn = dialog.addOn ?: return
        XfDevAddOns.remember(project, addOn)
        create(project, addOn, selection, dialog.key.trim(), dialog.description.trim(), dialog.action, dialog.find, dialog.replace, dialog.order)
    }

    fun create(
        project: Project,
        addOn: XfAddOn,
        selection: XfTemplateSelection,
        key: String,
        description: String,
        action: String,
        find: String,
        replace: String,
        order: Int,
    ) {
        val title = message("modification.command", key)
        if (XfMake.isAvailable(addOn, XfMake.TEMPLATE_MODIFICATION)) {
            val arguments = listOf(
                MAKE_MODIFICATION, key, "--type=${selection.type}", "--template=${selection.title}", "--description=$description",
                "--action=$action", "--find=$find", "--replace=$replace", "--order=$order", "--addon=${addOn.id}", NO_INTERACTION,
            )
            XfCommandRunner.run(project, addOn, title, arguments)
            return
        }
        val json = XfOutputFiles.json(
            listOf(
                "template" to selection.title,
                "description" to description,
                "execution_order" to order,
                "enabled" to true,
                "action" to action,
                "find" to find,
                "replace" to replace,
            ),
        )
        XfOutputFiles.write(addOn.directory, "_output/template_modifications/${selection.type}/$key.json", json)
        XfCommandRunner.run(project, addOn, title, listOf(IMPORT_MODIFICATIONS, "--addon=${addOn.id}"))
    }

    fun suggestKey(addOnId: String?, template: String): String {
        val prefix = addOnId?.lowercase()?.replace('/', '_')?.let { "${it}_" }.orEmpty()
        return prefix + template.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }
}

class XfModificationDialog(
    private val project: Project,
    private val selection: XfTemplateSelection,
    addOns: List<XfAddOn>,
    selected: XfAddOn?,
) : DialogWrapper(project) {
    var addOn: XfAddOn? = selected ?: addOns.firstOrNull()
    var key: String = XfModificationCreation.suggestKey(addOn?.id, selection.title)
    var description: String = ""
    var action: String = XfModifications.STR_REPLACE
    var find: String = selection.find
    var replace: String = DEFAULT_REPLACE
    var order: Int = DEFAULT_ORDER
    private val choices = addOns
    private lateinit var actionBox: javax.swing.JComboBox<String>

    init {
        title = message("modification.dialog.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(message("modification.dialog.template")) { label("${selection.type}:${selection.title}") }
        row(message("phrase.dialog.addon")) {
            comboBox(choices, textListCellRenderer { it?.id.orEmpty() }).bindItem(::addOn)
        }
        row(message("modification.dialog.key")) {
            textField()
                .bindText(::key)
                .columns(COLUMNS)
                .align(AlignX.FILL)
                .focused()
                .validationOnApply { field -> validateKey(field.text.trim())?.let { error(it) } }
        }
        row(message("modification.dialog.description")) {
            textField().bindText(::description).columns(COLUMNS).align(AlignX.FILL)
        }
        row(message("modification.dialog.action")) {
            actionBox = comboBox(listOf(XfModifications.STR_REPLACE, XfModifications.PREG_REPLACE))
                .bindItem({ action }, { action = it ?: XfModifications.STR_REPLACE })
                .component
        }
        row(message("modification.dialog.find")) {
            textArea()
                .bindText(::find)
                .rows(TEXT_ROWS)
                .columns(COLUMNS)
                .align(AlignX.FILL)
                .validationOnApply { area ->
                    val current = actionBox.selectedItem as? String ?: XfModifications.STR_REPLACE
                    val count = XfModifications.matches(current, area.text, selection.templateText)
                    when {
                        count == null -> error(message("modification.error.pattern"))
                        count.isEmpty() -> error(message("modification.error.missing"))
                        count.size > 1 -> warning(message("modification.warning.many", count.size))
                        else -> null
                    }
                }
        }
        row(message("modification.dialog.replace")) {
            textArea().bindText(::replace).rows(TEXT_ROWS).columns(COLUMNS).align(AlignX.FILL)
                .comment(message("modification.dialog.replace.comment"))
        }
        row(message("modification.dialog.order")) {
            intTextField(0..Int.MAX_VALUE).bindIntText(::order).columns(ORDER_COLUMNS)
        }
    }

    private fun validateKey(key: String): String? = when {
        !KEY.matches(key) -> message("modification.error.key")
        XfModifications.keyExists(project, key) -> message("modification.error.exists", key)
        else -> null
    }

    private companion object {
        const val COLUMNS = 50
        const val TEXT_ROWS = 5
        const val ORDER_COLUMNS = 6
    }
}

class XfCreateModificationAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = event.project != null && editor != null && file != null &&
            editor.selectionModel.hasSelection() && (XfTemplateFiles.isOutputTemplate(file) || XfModificationMarkers.isTemplatesXml(file))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = XfModificationCreation.selection(editor, file)
        if (selection == null) {
            Messages.showErrorDialog(project, message("modification.error.selection"), message("modification.dialog.title"))
            return
        }
        XfModificationCreation.show(project, selection, file)
    }
}
