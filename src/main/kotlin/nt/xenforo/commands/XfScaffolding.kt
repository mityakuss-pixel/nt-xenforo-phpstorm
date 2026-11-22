package nt.xenforo.commands

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.php.lang.psi.elements.Method
import nt.xenforo.XfBundle.message
import javax.swing.JComponent

private const val SETUP_FILE = "Setup.php"
private const val STEP_COMMANDS = "src/XF/Cli/Command/AddOn"
private const val MAKE_COMMANDS = "src/XF/Cli/Command/Make"
private const val MAKE_SUFFIX = "MakeCommand.php"
private val INSTALL_STEP = Regex("""^installStep(\d+)$""")
private val UPGRADE_STEP = Regex("""^upgrade(\d+)Step(\d+)$""")
private val UNINSTALL_STEP = Regex("""^uninstallStep(\d+)$""")
private val MAKE_NAME = Regex("""setName\('(xf-make:[\w-]+)'\)""")
private val MAKE_DESCRIPTION = Regex("""setDescription\('((?:[^'\\]|\\.)*)'\)""")

object XfSetupSteps {
    fun command(addOn: XfAddOn, methodName: String): List<String>? {
        INSTALL_STEP.matchEntire(methodName)?.let { return step(addOn, "install", listOf(it.groupValues[1])) }
        UPGRADE_STEP.matchEntire(methodName)?.let { return step(addOn, "upgrade", listOf(it.groupValues[1], it.groupValues[2])) }
        UNINSTALL_STEP.matchEntire(methodName)?.let { return step(addOn, "uninstall", listOf(it.groupValues[1])) }
        return null
    }

    fun addOn(project: Project, method: Method): XfAddOn? {
        val file = method.containingFile?.virtualFile ?: return null
        if (file.name != SETUP_FILE) return null
        val addOn = XfAddOns.find(project, file) ?: return null
        return addOn.takeIf { it.directory == file.parent }
    }

    private fun step(addOn: XfAddOn, kind: String, arguments: List<String>): List<String>? {
        val commandClass = kind.replaceFirstChar { it.uppercase() } + "Step.php"
        if (addOn.root.findFileByRelativePath("$STEP_COMMANDS/$commandClass") == null) return null
        return listOf("xf-addon:$kind-step", addOn.id) + arguments
    }
}

class XfSetupStepLineMarker : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val method = element.parent as? Method ?: return null
        if (method.nameIdentifier != element) return null
        val project = element.project
        val addOn = XfSetupSteps.addOn(project, method) ?: return null
        val arguments = XfSetupSteps.command(addOn, method.name) ?: return null
        val title = arguments.joinToString(" ")
        val action = object : AnAction(message("setup.step.run", title), null, AllIcons.Actions.Execute) {
            override fun actionPerformed(event: AnActionEvent) {
                XfCommandRunner.run(project, addOn, message("setup.step.title", method.name), arguments)
            }
        }
        return Info(AllIcons.RunConfigurations.TestState.Run, arrayOf(action)) { message("setup.step.run", title) }
    }
}

data class XfMakeCommand(val name: String, val description: String)

object XfMakeCommands {
    fun list(root: VirtualFile): List<XfMakeCommand> {
        val directory = root.findFileByRelativePath(MAKE_COMMANDS) ?: return emptyList()
        return directory.children.filter { it.name.endsWith(MAKE_SUFFIX) }.mapNotNull { file ->
            val text = VfsUtilCore.loadText(file)
            val name = MAKE_NAME.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
            XfMakeCommand(name, MAKE_DESCRIPTION.find(text)?.groupValues?.get(1)?.replace("\\'", "'").orEmpty())
        }.sortedBy { it.name }
    }
}

class XfMakeDialog(project: Project, commands: List<XfMakeCommand>, addOns: List<XfAddOn>, selected: XfAddOn?) : DialogWrapper(project) {
    var command: XfMakeCommand? = commands.firstOrNull()
    var arguments: String = ""
    var addOn: XfAddOn? = selected ?: addOns.firstOrNull()
    private val commandChoices = commands
    private val addOnChoices = addOns

    init {
        title = message("make.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(message("make.command")) {
            comboBox(commandChoices, textListCellRenderer { item -> item?.let { "${it.name}  ${it.description}" }.orEmpty() })
                .bindItem(::command)
                .align(AlignX.FILL)
        }
        row(message("make.arguments")) {
            textField().bindText(::arguments).columns(COLUMNS).align(AlignX.FILL).focused()
                .comment(message("make.arguments.comment"))
        }
        row(message("phrase.dialog.addon")) {
            comboBox(addOnChoices, textListCellRenderer { it?.id.orEmpty() }).bindItem(::addOn)
        }
    }

    private companion object {
        const val COLUMNS = 40
    }
}

class XfMakeAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val addOn = project?.let { XfAddOns.find(it, event.getData(CommonDataKeys.VIRTUAL_FILE)) }
        event.presentation.isEnabledAndVisible = addOn != null && addOn.root.findFileByRelativePath(MAKE_COMMANDS) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val current = XfAddOns.find(project, event.getData(CommonDataKeys.VIRTUAL_FILE)) ?: return
        val commands = XfMakeCommands.list(current.root)
        if (commands.isEmpty()) {
            Messages.showErrorDialog(project, message("make.error.commands"), message("make.title"))
            return
        }
        val addOns = XfDevAddOns.list(project)
        val dialog = XfMakeDialog(project, commands, addOns, addOns.firstOrNull { it.id == current.id } ?: XfDevAddOns.preferred(project, addOns, null))
        if (!dialog.showAndGet()) return
        val command = dialog.command ?: return
        val addOn = dialog.addOn ?: current
        XfDevAddOns.remember(project, addOn)
        val arguments = listOf(command.name) + ParametersListUtil.parse(dialog.arguments.trim()).filter { it.isNotEmpty() } + "--addon=${addOn.id}"
        XfCommandRunner.run(project, addOn, command.name, arguments, interactive = true)
    }
}
