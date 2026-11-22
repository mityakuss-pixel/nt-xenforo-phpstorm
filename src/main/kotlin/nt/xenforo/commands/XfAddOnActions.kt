package nt.xenforo.commands

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.execution.ParametersListUtil
import nt.xenforo.XfBundle.message

private const val BUILD_RELEASE = "xf-addon:build-release"
private const val BUMP_VERSION = "xf-addon:bump-version"
private const val SYNC_JSON = "xf-addon:sync-json"
private const val EXPORT_DATA = "xf-addon:export"
private const val EXPORT_OUTPUT = "xf-dev:export"
private const val IMPORT_OUTPUT = "xf-dev:import"
private const val DEFAULT_COMMAND = "xf-addon:upgrade"
private const val LAST_COMMAND_KEY = "nt.xenforo.commands.last"
private const val ELLIPSIS = "..."

class XfAddOnActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val addOn = XfAddOnAction.addOn(event)
        event.presentation.isEnabledAndVisible = addOn != null
        if (addOn != null) event.presentation.setText(message("group.nt.xenforo.AddOn.title", addOn.id), false)
    }
}

abstract class XfAddOnAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = addOn(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val addOn = addOn(event) ?: return
        perform(project, addOn, templatePresentation.text.removeSuffix(ELLIPSIS).removeSuffix("…"))
    }

    protected abstract fun perform(project: Project, addOn: XfAddOn, title: String)

    companion object {
        fun addOn(event: AnActionEvent): XfAddOn? {
            val project = event.project ?: return null
            return XfAddOns.find(project, event.getData(CommonDataKeys.VIRTUAL_FILE))
        }
    }
}

class XfBuildReleaseAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(BUILD_RELEASE, addOn.id)) { XfCommandRunner.revealLatestRelease(project, it) }
}

class XfBumpVersionAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) {
        val dialog = XfBumpVersionDialog(project, addOn, addOn.version())
        if (!dialog.showAndGet()) return
        val arguments = listOf(BUMP_VERSION, addOn.id, "--version-id=${dialog.versionId.trim()}", "--version-string=${dialog.versionString.trim()}")
        XfCommandRunner.run(project, addOn, title, arguments)
    }
}

class XfSyncJsonAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(SYNC_JSON, addOn.id))
}

class XfExportDataAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(EXPORT_DATA, addOn.id))
}

class XfExportOutputAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(EXPORT_OUTPUT, "--addon=${addOn.id}"))
}

class XfImportOutputAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(IMPORT_OUTPUT, "--addon=${addOn.id}"))
}

class XfRunCommandAction : XfAddOnAction() {
    override fun perform(project: Project, addOn: XfAddOn, title: String) {
        val properties = PropertiesComponent.getInstance(project)
        val initial = properties.getValue(LAST_COMMAND_KEY) ?: "$DEFAULT_COMMAND ${addOn.id}"
        val input = Messages.showInputDialog(project, message("commands.run.prompt"), message("commands.run.title", addOn.id), null, initial, null)
        val arguments = input?.let(ParametersListUtil::parse)?.filter { it.isNotEmpty() }.orEmpty()
        if (arguments.isEmpty()) return
        properties.setValue(LAST_COMMAND_KEY, input)
        XfCommandRunner.run(project, addOn, arguments.first(), arguments)
    }
}

class XfCommandSettingsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, XfCommandConfigurable::class.java)
    }
}
