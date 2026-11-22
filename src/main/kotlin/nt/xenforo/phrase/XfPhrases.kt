package nt.xenforo.phrase

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlText
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.XfBundle.message
import nt.xenforo.commands.XfAddOn
import nt.xenforo.commands.XfAddOns
import nt.xenforo.commands.XfCommandRunner
import nt.xenforo.commands.XfDevAddOns
import nt.xenforo.commands.XfMake
import nt.xenforo.commands.XfOutputFiles
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.php.XfPhpNameReference
import nt.xenforo.php.XfPhpNames
import nt.xenforo.psi.XfStringExpr
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameReference
import javax.swing.JComponent

private const val MAKE_PHRASE = "xf-make:phrase"
private const val IMPORT_PHRASES = "xf-dev:import-phrases"
private const val NO_INTERACTION = "--no-interaction"
private const val CORE_PHRASES_XML = "src/addons/XF/_data/phrases.xml"
private const val CORE_PHRASES_OUTPUT = "src/addons/XF/_output/phrases"
private const val TITLE_LIMIT = 50
private val TITLE = Regex("""[A-Za-z0-9_.]+""")
private val NOT_SLUG = Regex("""[^a-z0-9]+""")
private val TEMPLATE_SYNTAX = Regex("""[{}<>$]""")
private val LETTER = Regex("""\p{L}""")

private val TRANSLIT = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
    'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
    'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
    'э' to "e", 'ю' to "yu", 'я' to "ya",
)

object XfPhrases {
    fun exists(project: Project, title: String): Boolean = XfSymbols.locations(project, XfSymbolKeys.phrase(title)).isNotEmpty()

    fun isValidTitle(title: String): Boolean = TITLE.matches(title)

    fun isChecked(project: Project, file: VirtualFile?): Boolean {
        val addOn = XfAddOns.find(project, file) ?: return false
        return addOn.root.findFileByRelativePath(CORE_PHRASES_XML) != null || addOn.root.findFileByRelativePath(CORE_PHRASES_OUTPUT) != null
    }

    fun suggestTitle(addOnId: String?, text: String): String {
        val prefix = addOnId?.lowercase()?.replace('/', '_')?.let { "${it}_" }.orEmpty()
        val latin = text.lowercase().map { TRANSLIT[it] ?: it.toString() }.joinToString("")
        val slug = latin.replace(NOT_SLUG, "_").trim('_').take(TITLE_LIMIT).trimEnd('_')
        return prefix + slug.ifEmpty { "phrase" }
    }

    fun create(project: Project, addOn: XfAddOn, title: String, text: String) {
        XfDevAddOns.remember(project, addOn)
        val commandTitle = message("phrase.command", title)
        if (XfMake.isAvailable(addOn, XfMake.PHRASE)) {
            XfCommandRunner.run(project, addOn, commandTitle, listOf(MAKE_PHRASE, title, "--text=$text", "--addon=${addOn.id}", NO_INTERACTION))
            return
        }
        XfOutputFiles.write(addOn.directory, "_output/phrases/$title.txt", text)
        XfCommandRunner.run(project, addOn, commandTitle, listOf(IMPORT_PHRASES, "--addon=${addOn.id}"))
    }

    fun ask(project: Project, file: VirtualFile?, title: String, text: String, onCreate: (String) -> Unit = {}) {
        val addOns = XfDevAddOns.list(project)
        if (addOns.isEmpty()) {
            Messages.showErrorDialog(project, message("extend.error.addons"), message("phrase.dialog.title"))
            return
        }
        val dialog = XfPhraseDialog(project, title, text, addOns, XfDevAddOns.preferred(project, addOns, file))
        if (!dialog.showAndGet()) return
        val addOn = dialog.addOn ?: return
        val created = dialog.phraseTitle.trim()
        onCreate(created)
        create(project, addOn, created, dialog.text)
    }
}

class XfPhraseDialog(
    private val project: Project,
    var phraseTitle: String,
    var text: String,
    addOns: List<XfAddOn>,
    selected: XfAddOn?,
) : DialogWrapper(project) {
    var addOn: XfAddOn? = selected ?: addOns.firstOrNull()
    private val choices = addOns

    init {
        setTitle(message("phrase.dialog.title"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(message("phrase.dialog.name")) {
            textField()
                .bindText(::phraseTitle)
                .columns(COLUMNS)
                .align(AlignX.FILL)
                .validationOnApply { field -> validateTitle(field.text.trim())?.let { error(it) } }
        }
        row(message("phrase.dialog.text")) {
            textArea()
                .bindText(::text)
                .rows(TEXT_ROWS)
                .columns(COLUMNS)
                .align(AlignX.FILL)
                .focused()
        }
        row(message("phrase.dialog.addon")) {
            comboBox(choices, textListCellRenderer { it?.id.orEmpty() }).bindItem(::addOn)
        }
    }

    private fun validateTitle(title: String): String? = when {
        !XfPhrases.isValidTitle(title) -> message("phrase.error.title")
        XfPhrases.exists(project, title) -> message("phrase.error.exists", title)
        else -> null
    }

    private companion object {
        const val COLUMNS = 40
        const val TEXT_ROWS = 4
    }
}

class XfCreatePhraseFix(private val phrase: String) : LocalQuickFix {
    override fun getFamilyName(): String = message("phrase.fix.family")

    override fun getName(): String = message("phrase.fix.name", phrase)

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile?.virtualFile
        XfPhrases.ask(project, file, phrase, "")
    }
}

private fun checkPhrase(holder: ProblemsHolder, element: PsiElement, range: TextRange, name: String, resolved: Boolean) {
    if (resolved || name.isEmpty() || !XfPhrases.isValidTitle(name)) return
    holder.registerProblem(element, message("phrase.unknown", name), ProblemHighlightType.WARNING, range, XfCreatePhraseFix(name))
}

class XfUnknownPhraseInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        if (DumbService.isDumb(project) || !XfPhrases.isChecked(project, holder.file.originalFile.virtualFile)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is XfStringExpr) return
                for (reference in element.references) {
                    if (reference !is XfNameReference || reference.slot.kind != XfNameKind.PHRASE) continue
                    checkPhrase(holder, element, reference.rangeInElement, reference.slot.name(element), reference.multiResolve(false).isNotEmpty())
                }
            }
        }
    }
}

class XfUnknownPhrasePhpInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        if (DumbService.isDumb(project) || !XfPhrases.isChecked(project, holder.file.originalFile.virtualFile)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is StringLiteralExpression) return
                val slot = XfPhpNames.slot(element)?.takeIf { it.kind == XfNameKind.PHRASE } ?: return
                val reference = XfPhpNameReference(element, slot)
                checkPhrase(holder, element, reference.rangeInElement, reference.name, reference.multiResolve(false).isNotEmpty())
            }
        }
    }
}

class XfExtractPhraseIntention : IntentionAction {
    override fun getFamilyName(): String = message("phrase.extract.family")

    override fun getText(): String = message("phrase.extract.text")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null || file.viewProvider !is XfFileViewProvider) return false
        return range(editor, file) != null && XfAddOns.find(project, file.virtualFile) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val range = range(editor, file) ?: return
        val document = editor.document
        val text = document.getText(range)
        val addOn = XfAddOns.find(project, file.virtualFile)
        XfPhrases.ask(project, file.virtualFile, XfPhrases.suggestTitle(addOn?.id, text), text) { title ->
            WriteCommandAction.runWriteCommandAction(project, message("phrase.extract.family"), null, {
                document.replaceString(range.startOffset, range.endOffset, "{{ phrase('$title') }}")
            }, file)
        }
    }

    private fun range(editor: Editor, file: PsiFile): TextRange? {
        val document = editor.document
        val selection = editor.selectionModel
        val raw = if (selection.hasSelection()) {
            TextRange(selection.selectionStart, selection.selectionEnd)
        } else {
            val provider = file.viewProvider as? XfFileViewProvider ?: return null
            val element = provider.findElementAt(editor.caretModel.offset, provider.templateDataLanguage) ?: return null
            val value = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
            when {
                value != null -> value.valueTextRange
                else -> PsiTreeUtil.getParentOfType(element, XmlText::class.java, false)?.textRange ?: return null
            }
        }
        val text = document.getText(raw)
        val start = raw.startOffset + (text.length - text.trimStart().length)
        val end = raw.endOffset - (text.length - text.trimEnd().length)
        if (start >= end) return null
        val trimmed = document.getText(TextRange(start, end))
        if (TEMPLATE_SYNTAX.containsMatchIn(trimmed) || !LETTER.containsMatchIn(trimmed)) return null
        return TextRange(start, end)
    }
}
