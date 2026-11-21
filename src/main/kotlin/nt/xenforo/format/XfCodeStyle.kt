package nt.xenforo.format

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import nt.xenforo.XfBundle
import nt.xenforo.lang.XfFileType
import nt.xenforo.lang.XfLanguage
import nt.xenforo.lang.XfTemplateFiles

private val CODE_SAMPLE = """<xf:if is="${'$'}threads">
	<div class="block-body">
		<xf:foreach loop="${'$'}threads" value="${'$'}thread">
			<div class="structItem">{${'$'}thread.title}</div>
		</xf:foreach>
	</div>
<xf:else />
	<div class="blockMessage">{{ phrase('there_are_no_threads_to_display') }}</div>
</xf:if>
"""

class XfCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun getConfigurableDisplayName(): String = XfBundle.message("settings.display.name")

    override fun getLanguage(): Language = XfLanguage

    override fun createConfigurable(settings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
        object : CodeStyleAbstractConfigurable(settings, modelSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel = XfCodeStylePanel(currentSettings, settings)
        }
}

private class XfCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(XfLanguage, currentSettings, settings) {
    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
    }
}

class XfLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = XfLanguage

    override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: CommonCodeStyleSettings.IndentOptions) {
        indentOptions.USE_TAB_CHARACTER = true
        indentOptions.TAB_SIZE = 4
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun getCodeSample(settingsType: SettingsType): String = CODE_SAMPLE
}

class XfFileIndentOptionsProvider : FileIndentOptionsProvider() {
    override fun getIndentOptions(project: Project, settings: CodeStyleSettings, file: VirtualFile): CommonCodeStyleSettings.IndentOptions? {
        if (file.fileType != XfFileType && !XfTemplateFiles.isOutputTemplate(file)) return null
        val options = settings.getCommonSettings(XfLanguage).indentOptions?.clone() as? CommonCodeStyleSettings.IndentOptions ?: return null
        options.isOverrideLanguageOptions = true
        return options
    }
}
