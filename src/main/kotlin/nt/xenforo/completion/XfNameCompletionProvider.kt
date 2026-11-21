package nt.xenforo.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfIcons
import nt.xenforo.psi.XfFile
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameSlot
import nt.xenforo.reference.XfNameSlots

class XfNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val host = PsiTreeUtil.getParentOfType(parameters.position, XfReferenceHost::class.java, false) ?: return
        val caret = parameters.offset - host.textRange.startOffset
        val slot = XfNameSlots.of(host).firstOrNull { caret >= it.range.startOffset && caret <= it.range.endOffset } ?: return
        val prefix = host.text.substring(slot.range.startOffset, caret)
        val names = result.withPrefixMatcher(prefix)
        val project = host.project
        when (slot.kind) {
            XfNameKind.TEMPLATE -> addTemplates(project, slot, names, suffix = "")
            XfNameKind.MACRO -> {
                addMacros(project, slot, names)
                if (slot.range.startOffset == host.valueRange.startOffset && !prefix.contains(':')) {
                    val type = (host.containingFile.originalFile as? XfFile)?.templateType ?: return
                    addTemplates(project, XfNameSlot(XfNameKind.TEMPLATE, slot.range, XfSymbolKeys.templatePrefix(type)), names, suffix = "::")
                }
            }
            XfNameKind.PHRASE -> addDetailed(project, XfSymbolKeys.PHRASE_PREFIX, names, AllIcons.Nodes.Tag)
            XfNameKind.ROUTE -> addDetailed(project, slot.keyPrefix, names, AllIcons.Nodes.Related)
            XfNameKind.PROPERTY -> addDetailed(project, XfSymbolKeys.PROPERTY_PREFIX, names, AllIcons.Nodes.Property)
        }
    }

    private fun addTemplates(project: Project, slot: XfNameSlot, result: CompletionResultSet, suffix: String) {
        for (key in XfSymbols.keys(project, slot.keyPrefix)) {
            val name = key.removePrefix(slot.keyPrefix)
            val isStyle = name.endsWith(".less") || name.endsWith(".css")
            if (slot.lessOnly != isStyle) continue
            val lookup = LookupElementBuilder.create(name + suffix).withPresentableText(name).withIcon(XfIcons.FILE)
            result.addElement(if (suffix.isEmpty()) lookup else lookup.withInsertHandler(ReopenPopupInsertHandler))
        }
    }

    private fun addMacros(project: Project, slot: XfNameSlot, result: CompletionResultSet) {
        for (key in XfSymbols.keys(project, slot.keyPrefix)) {
            val name = key.removePrefix(slot.keyPrefix)
            val arguments = XfSymbols.detail(project, key).orEmpty()
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTailText(if (arguments.isEmpty()) "" else "($arguments)", true),
            )
        }
    }

    private fun addDetailed(project: Project, keyPrefix: String, result: CompletionResultSet, icon: javax.swing.Icon) {
        for (key in XfSymbols.keys(project, keyPrefix)) {
            val name = key.removePrefix(keyPrefix)
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(icon)
                    .withExpensiveRenderer(DetailRenderer(project, key, icon)),
            )
        }
    }
}

private class DetailRenderer(private val project: Project, private val key: String, private val icon: javax.swing.Icon) :
    LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        presentation.itemText = element.lookupString
        presentation.icon = icon
        val detail = XfSymbols.detail(project, key)
        if (!detail.isNullOrEmpty()) presentation.setTypeText(detail)
    }
}

private object ReopenPopupInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
    }
}
