package nt.xenforo.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import nt.xenforo.XfBundle.message
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class XfBumpVersionDialog(project: Project, addOn: XfAddOn, private val current: XfAddOnVersion?) : DialogWrapper(project) {
    var versionString: String = current?.string?.let(XfVersion::next).orEmpty()
    var versionId: String = XfVersion.id(versionString)?.toString().orEmpty()

    private lateinit var idField: JBTextField
    private var calculatedId: String = versionId

    init {
        title = message("bump.title", addOn.id)
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        if (current != null) {
            row(message("bump.current")) { label("${current.string} (${current.id})") }
        }
        row(message("bump.string")) {
            textField()
                .columns(COLUMNS)
                .bindText(::versionString)
                .focused()
                .validationOnApply { if (it.text.isBlank()) error(message("bump.error.string")) else null }
                .applyToComponent { document.addDocumentListener(StringListener(this)) }
        }
        row(message("bump.id")) {
            idField = textField()
                .columns(COLUMNS)
                .bindText(::versionId)
                .validationOnApply { field -> validateId(field.text)?.let { error(it) } }
                .comment(message("bump.id.comment"))
                .component
        }
    }

    private fun validateId(text: String): String? {
        val id = text.trim().toIntOrNull() ?: return message("bump.error.id")
        if (current != null && id < current.id) return message("bump.error.downgrade", current.id)
        return null
    }

    private inner class StringListener(private val field: JBTextField) : DocumentAdapter() {
        override fun textChanged(event: DocumentEvent) {
            if (!::idField.isInitialized || idField.text != calculatedId) return
            calculatedId = XfVersion.id(field.text)?.toString().orEmpty()
            idField.text = calculatedId
        }
    }

    private companion object {
        const val COLUMNS = 20
    }
}
