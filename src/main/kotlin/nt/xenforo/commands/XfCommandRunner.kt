package nt.xenforo.commands

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.Filter
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.config.PhpProjectConfigurationFacade
import com.jetbrains.php.config.commandLine.PhpCommandSettingsBuilder
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import com.jetbrains.php.run.remote.PhpRemoteInterpreterManager
import nt.xenforo.XfBundle.message
import java.nio.charset.StandardCharsets
import java.nio.file.Path

object XfCommandRunner {
    private const val NOTIFICATION_GROUP = "nt.xenforo.commands"
    private const val PHP = "php"
    private const val ANSI = "--ansi"
    private const val OPTIONS_END = "--"
    private const val ZIP_EXTENSION = "zip"
    private const val INTERACTIVE_VARIABLE = "SHELL_INTERACTIVE"

    fun run(
        project: Project,
        addOn: XfAddOn,
        title: String,
        arguments: List<String>,
        filters: List<Filter> = emptyList(),
        interactive: Boolean = false,
        onSuccess: ((XfAddOn) -> Unit)? = null,
    ) {
        FileDocumentManager.getInstance().saveAllDocuments()
        val script = addOn.script
        if (script == null) {
            notify(project, message("commands.error.script", addOn.root.presentableUrl), NotificationType.ERROR, settingsAction(project))
            return
        }
        val tabTitle = message("commands.tab", title, addOn.id)
        object : Task.Backgroundable(project, message("commands.progress", tabTitle), true) {
            private var handler: ProcessHandler? = null

            override fun run(indicator: ProgressIndicator) {
                handler = createHandler(project, addOn.root, script, arguments, interactive)
            }

            override fun onSuccess() {
                val processHandler = handler ?: return
                processHandler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        ApplicationManager.getApplication().invokeLater({ finished(project, addOn, tabTitle, event.exitCode, onSuccess) }, project.disposed)
                    }
                })
                val executor = RunContentExecutor(project, processHandler)
                    .withTitle(tabTitle)
                    .withRerun { run(project, addOn, title, arguments, filters, interactive, onSuccess) }
                    .withActivateToolWindow(true)
                filters.forEach { executor.withFilter(it) }
                executor.run()
            }

            override fun onThrowable(error: Throwable) {
                notify(project, error.message ?: error.toString(), NotificationType.ERROR, settingsAction(project))
            }
        }.queue()
    }

    fun revealLatestRelease(project: Project, addOn: XfAddOn) {
        val release = addOn.releases?.children
            ?.filter { it.extension.equals(ZIP_EXTENSION, ignoreCase = true) && it.name.startsWith(addOn.releasePrefix) }
            ?.maxByOrNull { it.timeStamp }
        if (release == null) {
            notify(project, message("commands.done", message("action.nt.xenforo.BuildRelease.text"), addOn.id), NotificationType.INFORMATION)
            return
        }
        val reveal = NotificationAction.createSimple(RevealFileAction.getActionName()) { RevealFileAction.openFile(Path.of(release.path)) }
        notify(project, message("commands.build.done", release.name), NotificationType.INFORMATION, reveal)
    }

    private fun finished(project: Project, addOn: XfAddOn, title: String, exitCode: Int, onSuccess: ((XfAddOn) -> Unit)?) {
        VfsUtil.markDirty(true, true, addOn.directory)
        LocalFileSystem.getInstance().refreshFiles(listOf(addOn.directory), true, true) {
            if (!project.isDisposed) {
                when {
                    exitCode != 0 -> notify(project, message("commands.failed", title, exitCode), NotificationType.ERROR)
                    onSuccess != null -> onSuccess(addOn)
                    else -> notify(project, message("commands.done", title, addOn.id), NotificationType.INFORMATION)
                }
            }
        }
    }

    private fun createHandler(project: Project, root: VirtualFile, script: VirtualFile, arguments: List<String>, interactive: Boolean): ProcessHandler {
        val interpreter = interpreter(project) ?: return localHandler(root, script, arguments, interactive)
        val settings = PhpCommandSettingsBuilder(project, interpreter).build()
        settings.setWorkingDir(root.path)
        settings.setScript(script.path)
        settings.addArguments(arguments)
        if (!settings.isRemote) settings.addArguments(colorOption(arguments))
        val commandLine = settings.createGeneralCommandLine()
        if (interactive) commandLine.withEnvironment(INTERACTIVE_VARIABLE, "1")
        if (!settings.isRemote) return KillableColoredProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8))
        val disabled = PhpRemoteInterpreterManager.getRemoteInterpreterPluginIsDisabledErrorMessage()
        val manager = PhpRemoteInterpreterManager.getInstance() ?: throw ExecutionException(disabled)
        val data = settings.additionalData ?: throw ExecutionException(disabled)
        return manager.getRemoteProcessHandler(project, data, commandLine, false, *settings.additionalMappings)
    }

    private fun localHandler(root: VirtualFile, script: VirtualFile, arguments: List<String>, interactive: Boolean): ProcessHandler {
        val commandLine = GeneralCommandLine(listOf(PHP, script.name) + arguments + colorOption(arguments))
            .withWorkingDirectory(Path.of(root.path))
            .withCharset(StandardCharsets.UTF_8)
        if (interactive) commandLine.withEnvironment(INTERACTIVE_VARIABLE, "1")
        return try {
            KillableColoredProcessHandler(commandLine)
        } catch (error: ExecutionException) {
            throw ExecutionException(message("commands.error.php"), error)
        }
    }

    private fun colorOption(arguments: List<String>): List<String> =
        if (OPTIONS_END in arguments || ANSI in arguments) emptyList() else listOf(ANSI)

    private fun interpreter(project: Project): PhpInterpreter? {
        val name = XfCommandSettings.getInstance(project).interpreter
            ?: return PhpProjectConfigurationFacade.getInstance(project).interpreter
        return PhpInterpretersManagerImpl.getInstance(project).findInterpreter(name)
            ?: throw ExecutionException(message("commands.error.interpreter", name))
    }

    private fun settingsAction(project: Project): NotificationAction =
        NotificationAction.createSimpleExpiring(message("commands.action.settings")) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, XfCommandConfigurable::class.java)
        }

    private fun notify(project: Project, content: String, type: NotificationType, vararg actions: NotificationAction) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP).createNotification(content, type)
        actions.forEach(notification::addAction)
        notification.notify(project)
    }
}
