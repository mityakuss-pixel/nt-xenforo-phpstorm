package nt.xenforo.commands

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Condition
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.config.PhpProjectConfigurationFacade
import com.jetbrains.php.config.interpreters.PhpInterpreterComboBox
import com.jetbrains.php.config.interpreters.PhpSdkAdditionalData
import nt.xenforo.XfBundle.message

class XfCommandConfigurable(private val project: Project) :
    BoundSearchableConfigurable(message("settings.commands.title"), ID, ID) {

    override fun createPanel(): DialogPanel {
        val settings = XfCommandSettings.getInstance(project)
        val interpreters = PhpInterpreterComboBox(project, Condition<PhpSdkAdditionalData> { true }).apply {
            isNoItemAllowed = true
            noItemText = defaultInterpreterText()
        }
        return panel {
            row(message("settings.commands.interpreter")) {
                cell(interpreters)
                    .align(AlignX.FILL)
                    .comment(message("settings.commands.interpreter.comment"))
                    .onReset { interpreters.reset(settings.interpreter) }
                    .onIsModified { interpreters.selectedItemName != settings.interpreter }
                    .onApply { settings.interpreter = interpreters.selectedItemName }
            }
            row(message("settings.commands.root")) {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(message("settings.commands.root.title")), project)
                    .align(AlignX.FILL)
                    .bindText({ settings.root.orEmpty() }, { settings.root = it })
                    .comment(message("settings.commands.root.comment"))
            }
        }
    }

    private fun defaultInterpreterText(): String {
        val name = PhpProjectConfigurationFacade.getInstance(project).interpreter?.name
        return if (name == null) message("settings.commands.interpreter.path") else message("settings.commands.interpreter.default", name)
    }

    companion object {
        const val ID = "nt.xenforo.commands"
    }
}
