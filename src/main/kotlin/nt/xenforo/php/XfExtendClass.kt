package nt.xenforo.php

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.XfBundle.message
import nt.xenforo.commands.XfAddOn
import nt.xenforo.commands.XfAddOnAction
import nt.xenforo.commands.XfCommandRunner
import nt.xenforo.commands.XfDevAddOns
import nt.xenforo.commands.XfMake
import nt.xenforo.commands.XfOutputFiles
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import javax.swing.JComponent

private const val SEPARATOR = '\\'
private const val PROXY_PREFIX = "XFCP_"
private const val DEFAULT_ORDER = 10
private const val MAKE_EXTENSION = "xf-make:extension"
private const val IMPORT_EXTENSIONS = "xf-dev:import-class-extensions"
private const val NO_INTERACTION = "--no-interaction"
private const val EXTENSIONS_FOLDER = "_output/class_extensions"
private val XF_SOURCE_PATHS = listOf("/src/XF/", "/src/addons/")

object XfExtendClass {
    fun show(project: Project, baseClass: String, addOns: List<XfAddOn>, selected: XfAddOn?) {
        if (addOns.isEmpty()) {
            Messages.showErrorDialog(project, message("extend.error.addons"), message("extend.title"))
            return
        }
        val dialog = XfExtendClassDialog(project, baseClass, addOns, selected)
        if (!dialog.showAndGet()) return
        val addOn = dialog.addOn ?: return
        XfDevAddOns.remember(project, addOn)
        run(project, dialog.baseClass.trim().trimStart(SEPARATOR), addOn, dialog.order)
    }

    fun run(project: Project, baseClass: String, addOn: XfAddOn, order: Int) {
        val extension = XfClassExtensions.extensionClass(addOn.id, baseClass)
        val path = baseClass.replace(SEPARATOR, '/') + ".php"
        val existing = addOn.directory.findFileByRelativePath(path)
        val registered = XfSymbols.locations(project, XfSymbolKeys.extension(extension)).any { it.symbol.detail.isNotEmpty() }
        if (existing != null && registered) {
            XfOutputFiles.open(project, existing)
            return
        }
        val title = message("extend.command", baseClass.substringAfterLast(SEPARATOR))
        if (existing == null && XfMake.isAvailable(addOn, XfMake.EXTENSION)) {
            val arguments = listOf(MAKE_EXTENSION, baseClass, "--addon=${addOn.id}", "--execute-order=$order", NO_INTERACTION)
            XfCommandRunner.run(project, addOn, title, arguments) { target ->
                target.directory.findFileByRelativePath(path)?.let { XfOutputFiles.open(project, it) }
                XfProxyLibrary.getInstance(project).request()
            }
            return
        }
        val file = existing ?: XfOutputFiles.write(addOn.directory, path, classText(extension))
        XfOutputFiles.open(project, file)
        if (!registered) {
            XfOutputFiles.write(addOn.directory, "$EXTENSIONS_FOLDER/${fileName(baseClass, extension)}", registrationText(baseClass, extension, order))
            XfCommandRunner.run(project, addOn, title, listOf(IMPORT_EXTENSIONS, "--addon=${addOn.id}"))
        }
        XfProxyLibrary.getInstance(project).request()
    }

    fun classText(extension: String): String {
        val namespace = extension.substringBeforeLast(SEPARATOR, "")
        val name = extension.substringAfterLast(SEPARATOR)
        return "<?php\n\nnamespace $namespace;\n\nclass $name extends $PROXY_PREFIX$name\n{\n}\n"
    }

    fun fileName(baseClass: String, extension: String): String = "${dashed(baseClass)}_${dashed(extension)}.json"

    fun registrationText(baseClass: String, extension: String, order: Int): String =
        XfOutputFiles.json(listOf("from_class" to baseClass, "to_class" to extension, "execute_order" to order, "active" to true)) + "\n"

    fun isXenForoClass(phpClass: PhpClass): Boolean {
        val path = phpClass.containingFile?.virtualFile?.path?.replace(SEPARATOR, '/') ?: return false
        if (XF_SOURCE_PATHS.none { path.contains(it) } || phpClass.name.startsWith(PROXY_PREFIX) || phpClass.isInterface || phpClass.isTrait) return false
        return phpClass.extendsList.referenceElements.none { it.name?.startsWith(PROXY_PREFIX) == true }
    }

    fun classAt(element: PsiElement?): PhpClass? {
        if (element == null) return null
        val reference = PsiTreeUtil.getParentOfType(element, ClassReference::class.java, false)
        if (reference != null) return reference.resolve() as? PhpClass
        val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false) ?: return null
        return phpClass.takeIf { it.nameIdentifier?.textRange?.contains(element.textRange) == true }
    }

    fun extendableAtCaret(file: PsiFile, offset: Int): PhpClass? {
        val phpClass = classAt(file.findElementAt(offset)) ?: offset.takeIf { it > 0 }?.let { classAt(file.findElementAt(it - 1)) }
        return phpClass?.takeIf(::isXenForoClass)
    }

    fun extendableSelected(element: PsiElement?): PhpClass? {
        val phpClass = when (element) {
            is PhpClass -> element
            is PhpFile -> PsiTreeUtil.findChildrenOfType(element, PhpClass::class.java).filterNot { it.isInterface || it.isTrait }.singleOrNull()
            else -> null
        }
        return phpClass?.takeIf(::isXenForoClass)
    }

    private fun dashed(className: String): String = className.replace(Regex("[^a-zA-Z0-9_-]"), "-").trimStart('-')
}

class XfExtendClassDialog(
    private val project: Project,
    var baseClass: String,
    addOns: List<XfAddOn>,
    selected: XfAddOn?,
) : DialogWrapper(project) {
    var addOn: XfAddOn? = selected ?: addOns.firstOrNull()
    var order: Int = DEFAULT_ORDER
    private val choices = addOns

    init {
        title = message("extend.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(message("extend.class")) {
            textField()
                .bindText(::baseClass)
                .columns(COLUMNS)
                .align(AlignX.FILL)
                .focused()
                .validationOnApply { field -> validateClass(field.text)?.let { error(it) } }
        }
        row(message("extend.addon")) {
            comboBox(choices, textListCellRenderer { it?.id.orEmpty() }).bindItem(::addOn)
        }
        row(message("extend.order")) {
            intTextField(0..Int.MAX_VALUE).bindIntText(::order).columns(ORDER_COLUMNS)
        }
    }

    private fun validateClass(text: String): String? {
        val name = text.trim().trimStart(SEPARATOR)
        if (name.isEmpty()) return message("extend.error.class")
        if (PhpIndex.getInstance(project).getClassesByFQN(SEPARATOR + name).isEmpty()) return message("extend.error.missing", name)
        return null
    }

    private companion object {
        const val COLUMNS = 40
        const val ORDER_COLUMNS = 6
    }
}

class XfExtendClassIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = message("intention.extend.family")

    override fun getText(): String = message("intention.extend.text")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val phpClass = XfExtendClass.classAt(element) ?: return false
        return XfExtendClass.isXenForoClass(phpClass)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val phpClass = XfExtendClass.classAt(element) ?: return
        val addOns = XfDevAddOns.list(project)
        if (addOns.isEmpty() && editor != null) {
            HintManager.getInstance().showErrorHint(editor, message("extend.error.addons"))
            return
        }
        val selected = XfDevAddOns.preferred(project, addOns, element.containingFile?.virtualFile)
        XfExtendClass.show(project, phpClass.fqn.trimStart(SEPARATOR), addOns, selected)
    }
}

class XfExtendClassAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = XfAddOnAction.addOn(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val addOns = XfDevAddOns.list(project)
        val current = XfAddOnAction.addOn(event)
        val selected = addOns.firstOrNull { it.id == current?.id } ?: XfDevAddOns.preferred(project, addOns, null)
        XfExtendClass.show(project, classAtCaret(event).orEmpty(), addOns, selected)
    }

    private fun classAtCaret(event: AnActionEvent): String? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val file: PsiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null
        val phpClass = XfExtendClass.classAt(file.findElementAt(editor.caretModel.offset)) ?: return null
        return phpClass.fqn.trimStart(SEPARATOR).takeIf { XfExtendClass.isXenForoClass(phpClass) }
    }
}

class XfExtendThisClassAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && target(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val phpClass = target(event) ?: return
        val addOns = XfDevAddOns.list(project)
        val selected = XfDevAddOns.preferred(project, addOns, event.getData(CommonDataKeys.VIRTUAL_FILE))
        XfExtendClass.show(project, phpClass.fqn.trimStart(SEPARATOR), addOns, selected)
    }

    private fun target(event: AnActionEvent): PhpClass? {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && file != null) return XfExtendClass.extendableAtCaret(file, editor.caretModel.offset)
        return XfExtendClass.extendableSelected(event.getData(CommonDataKeys.PSI_ELEMENT) ?: file)
    }
}
