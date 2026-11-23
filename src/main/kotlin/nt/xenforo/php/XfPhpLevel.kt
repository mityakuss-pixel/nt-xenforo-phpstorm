package nt.xenforo.php

import com.google.gson.JsonParser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.jetbrains.php.config.PhpLanguageLevel
import com.jetbrains.php.config.PhpProjectConfigurationFacade
import nt.xenforo.XfBundle.message
import nt.xenforo.commands.XfAddOn
import nt.xenforo.commands.XfAddOnAction
import nt.xenforo.commands.XfAddOns
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.JComponent

private const val PHP_EXTENSION = "php"
private const val CORE_ADD_ON = "XF"
internal const val HIDDEN_ADD_ONS = "nt.xenforo.phpLevel.hidden"
private val VERSION = Regex("""(\d+)\.(\d+)""")
internal val DISMISSED_ADD_ONS = Key.create<MutableSet<String>>("nt.xenforo.phpLevel.dismissed")

object XfAddOnPhpLevel {
    fun required(addOn: XfAddOn): PhpLanguageLevel? {
        val json = addOn.json ?: return null
        val text = FileDocumentManager.getInstance().getCachedDocument(json)?.text ?: VfsUtilCore.loadText(json)
        return requiredIn(text)
    }

    fun requiredIn(json: String): PhpLanguageLevel? {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val require = root.get("require")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val php = require.get("php")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return level(php)
    }

    fun level(version: String): PhpLanguageLevel? {
        val match = VERSION.find(version) ?: return null
        val key = "${match.groupValues[1]}.${match.groupValues[2]}"
        return PhpLanguageLevel.entries.firstOrNull { it.versionString == key }
    }

    fun current(project: Project): PhpLanguageLevel = PhpProjectConfigurationFacade.getInstance(project).languageLevel

    fun mismatch(project: Project, addOn: XfAddOn): PhpLanguageLevel? {
        if (addOn.id == CORE_ADD_ON) return null
        val required = required(addOn) ?: return null
        return required.takeIf { it != current(project) }
    }

    fun apply(project: Project, level: PhpLanguageLevel) {
        PhpProjectConfigurationFacade.getInstance(project).languageLevel = level
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    fun isHidden(project: Project, addOn: XfAddOn): Boolean =
        PropertiesComponent.getInstance(project).getList(HIDDEN_ADD_ONS).orEmpty().contains(addOn.id)

    fun hide(project: Project, addOn: XfAddOn) {
        val properties = PropertiesComponent.getInstance(project)
        properties.setList(HIDDEN_ADD_ONS, properties.getList(HIDDEN_ADD_ONS).orEmpty() + addOn.id)
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    fun isDismissed(project: Project, addOn: XfAddOn): Boolean = project.getUserData(DISMISSED_ADD_ONS)?.contains(addOn.id) == true

    fun dismiss(project: Project, addOn: XfAddOn) {
        val dismissed = project.getUserData(DISMISSED_ADD_ONS) ?: ConcurrentHashMap.newKeySet<String>().also { project.putUserData(DISMISSED_ADD_ONS, it) }
        dismissed.add(addOn.id)
        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}

class XfPhpLevelNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.extension.equals(PHP_EXTENSION, ignoreCase = true)) return null
        val addOn = XfAddOns.find(project, file) ?: return null
        if (XfAddOnPhpLevel.isHidden(project, addOn) || XfAddOnPhpLevel.isDismissed(project, addOn)) return null
        val required = XfAddOnPhpLevel.mismatch(project, addOn) ?: return null
        val current = XfAddOnPhpLevel.current(project)
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning).apply {
                text = message("php.level.banner", addOn.id, required.versionString, current.versionString)
                createActionLabel(message("php.level.switch", required.versionString)) { XfAddOnPhpLevel.apply(project, required) }
                createActionLabel(message("php.level.hide")) { XfAddOnPhpLevel.hide(project, addOn) }
                setCloseAction { XfAddOnPhpLevel.dismiss(project, addOn) }
            }
        }
    }
}

class XfPhpLevelAction : XfAddOnAction() {
    override fun update(event: AnActionEvent) {
        val project = event.project
        val addOn = addOn(event)
        val required = if (project != null && addOn != null) XfAddOnPhpLevel.required(addOn) else null
        event.presentation.isVisible = required != null
        event.presentation.isEnabled = required != null && project != null && required != XfAddOnPhpLevel.current(project)
        if (required != null) event.presentation.setText(message("action.php.level.text", required.versionString), false)
    }

    override fun perform(project: Project, addOn: XfAddOn, title: String) {
        XfAddOnPhpLevel.required(addOn)?.let { XfAddOnPhpLevel.apply(project, it) }
    }
}
