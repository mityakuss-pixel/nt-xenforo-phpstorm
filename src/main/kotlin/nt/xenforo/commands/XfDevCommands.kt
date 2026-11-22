package nt.xenforo.commands

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.XfBundle.message
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.php.XfEntityStructures

private const val DEVELOPMENT_COMMANDS = "src/XF/Cli/Command/Development"
private val LINT_ERROR = Regex("""^\[([^\]]+)] (\w+):([\w.-]+): (?:\D*(\d+))?""")
private val TEMPLATE_EXTENSIONS = listOf(".html", "", ".less", ".css")

abstract class XfDevCommandAction(private val commandClass: String) : XfAddOnAction() {
    override fun update(event: AnActionEvent) {
        val addOn = addOn(event)
        event.presentation.isEnabledAndVisible = addOn != null && isAvailable(addOn)
    }

    fun isAvailable(addOn: XfAddOn): Boolean = addOn.root.findFileByRelativePath("$DEVELOPMENT_COMMANDS/$commandClass.php") != null
}

class XfLintTemplatesAction : XfDevCommandAction("LintTemplates") {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:lint-templates", "--addon=${addOn.id}"), filters = listOf(XfLintFilter(project, addOn.root)))
}

class XfUnusedPhrasesAction : XfDevCommandAction("UnusedPhraseFinder") {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:unused-phrase-finder", "--addon=${addOn.id}"))
}

class XfEntityPropertiesAction : XfDevCommandAction("EntityClassProperties") {
    override fun update(event: AnActionEvent) {
        super.update(event)
        if (!event.presentation.isEnabledAndVisible) return
        val entity = addOn(event)?.let { entity(event, it) }
        event.presentation.text = entity?.let { message("action.entity.properties.single", it.substringAfterLast(NAMESPACE_SEPARATOR).substringAfterLast(':')) }
            ?: templatePresentation.text
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val addOn = addOn(event) ?: return
        val entity = entity(event, addOn) ?: return perform(project, addOn, templatePresentation.text)
        XfCommandRunner.run(project, addOn, event.presentation.text, listOf(ENTITY_PROPERTIES, entity))
    }

    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf(ENTITY_PROPERTIES, addOn.id))

    private fun entity(event: AnActionEvent, addOn: XfAddOn): String? {
        val project = event.project ?: return null
        if (DumbService.isDumb(project)) return null
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)?.let { PsiManager.getInstance(project).findFile(it) }
        return entityShortName(addOn, file)
    }

    companion object {
        private const val ENTITY_PROPERTIES = "xf-dev:entity-class-properties"
        private const val NAMESPACE_SEPARATOR = '\\'

        fun entityShortName(addOn: XfAddOn, file: PsiFile?): String? {
            if (file !is PhpFile) return null
            val namespace = addOn.id.replace('/', NAMESPACE_SEPARATOR)
            val prefix = "$NAMESPACE_SEPARATOR$namespace${NAMESPACE_SEPARATOR}Entity$NAMESPACE_SEPARATOR"
            val phpClass = PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java).singleOrNull { !it.isInterface && !it.isTrait } ?: return null
            if (phpClass.isAbstract || !phpClass.fqn.startsWith(prefix)) return null
            if (!XfEntityStructures.isEntity(file.project, phpClass.fqn)) return null
            return namespace + ":" + phpClass.fqn.removePrefix(prefix)
        }
    }
}

class XfPhpStormMetaAction : XfDevCommandAction("GeneratePhpStormMeta") {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:generate-phpstorm-meta"))
}

class XfOptionsStubAction : XfDevCommandAction("GenerateOptionsStub") {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:generate-options-stub"))
}

class XfClassLintAction : XfDevCommandAction("ClassLint") {
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:class-lint"))
}

class XfLintFilter(private val project: Project, private val root: VirtualFile) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = LINT_ERROR.find(line.trimEnd()) ?: return null
        val type = match.groupValues[2].takeIf { it in XfTemplateFiles.TEMPLATE_TYPES } ?: return null
        val name = match.groupValues[3]
        val directory = root.findFileByRelativePath("src/addons/${match.groupValues[1]}/_output/templates/$type") ?: return null
        val file = TEMPLATE_EXTENSIONS.firstNotNullOfOrNull { directory.findChild(name + it)?.takeIf { found -> !found.isDirectory } } ?: return null
        val lineNumber = match.groupValues[4].toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
        val start = entireLength - line.length + match.groups[2]!!.range.first
        val end = entireLength - line.length + match.groups[3]!!.range.last + 1
        return Filter.Result(start, end, OpenFileHyperlinkInfo(project, file, lineNumber))
    }
}
