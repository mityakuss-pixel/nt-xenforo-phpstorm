package nt.xenforo.commands

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import nt.xenforo.lang.XfTemplateFiles

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
    override fun perform(project: Project, addOn: XfAddOn, title: String) =
        XfCommandRunner.run(project, addOn, title, listOf("xf-dev:entity-class-properties", addOn.id))
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
