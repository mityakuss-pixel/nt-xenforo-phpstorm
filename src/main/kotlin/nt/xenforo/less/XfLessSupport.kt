package nt.xenforo.less

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import nt.xenforo.index.XfLessKeys
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbolLocation
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.lang.XfTemplateFiles
import org.jetbrains.plugins.less.LESSElementTypes
import org.jetbrains.plugins.less.psi.LESSMixin
import org.jetbrains.plugins.less.psi.LESSMixinInvocation
import org.jetbrains.plugins.less.psi.LESSVariableDeclaration
import org.jetbrains.plugins.less.psi.LessRuleset

private const val SETUP_TEMPLATE = "public:setup.less"
private const val PREPEND_SUFFIX = "_prepend.less"
private const val STYLE_PROPERTY_PREFIX = "xf-"
private const val ICON_PREFIX = "fa-var-"
private const val VARIABLE_MARK = "@"
private const val MIXIN_MARK = "."
private const val TEMPLATE_END = "</template>"
private const val VARIABLE_TOOL = "LessUnresolvedVariable"
private const val MIXIN_TOOL = "LessUnresolvedMixin"
private const val BY_NAME_TOOL = "LessResolvedByNameOnly"
private val TOOLS = setOf(VARIABLE_TOOL, MIXIN_TOOL, BY_NAME_TOOL)

enum class XfLessKind(val mark: String) { VARIABLE(VARIABLE_MARK), MIXIN(MIXIN_MARK) }

data class XfLessSymbol(val kind: XfLessKind, val name: String, val element: PsiElement) {
    val key: String get() = kind.mark + name
}

object XfLessResolver {
    fun symbolAt(element: PsiElement): XfLessSymbol? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current.elementType == LESSElementTypes.LESS_VARIABLE) {
                val name = current.text.removePrefix(VARIABLE_MARK).trim()
                return if (name.isEmpty()) null else XfLessSymbol(XfLessKind.VARIABLE, name, current)
            }
            if (current is LESSMixinInvocation) {
                val name = current.name?.removePrefix(MIXIN_MARK)?.trim().orEmpty()
                return if (name.isEmpty()) null else XfLessSymbol(XfLessKind.MIXIN, name, current)
            }
            current = current.parent
        }
        return null
    }

    fun isKnown(file: PsiFile, symbol: XfLessSymbol): Boolean {
        val project = file.project
        if (DumbService.isDumb(project)) return false
        if (symbol.kind == XfLessKind.VARIABLE && symbol.name.startsWith(ICON_PREFIX)) return true
        if (symbol.name.startsWith(STYLE_PROPERTY_PREFIX)) return isStyleProperty(project, propertyName(symbol.name))
        if (localDeclarations(file, symbol).isNotEmpty()) return true
        return symbol.key in XfLessContext.definitions(file)
    }

    fun targets(file: PsiFile, symbol: XfLessSymbol): List<PsiElement> {
        val project = file.project
        if (DumbService.isDumb(project)) return emptyList()
        if (symbol.name.startsWith(STYLE_PROPERTY_PREFIX)) {
            val property = propertyName(symbol.name)
            return XfSymbols.targets(project, XfSymbolKeys.property(property), property)
        }
        val local = localDeclarations(file, symbol)
        if (local.isNotEmpty()) return local
        val current = XfLessContext.templateOf(file)
        return XfLessContext.templates(file)
            .filter { it != current }
            .flatMap { template -> declarationsIn(project, template, symbol) }
    }

    private fun localDeclarations(file: PsiFile, symbol: XfLessSymbol): List<PsiElement> = when (symbol.kind) {
        XfLessKind.VARIABLE -> PsiTreeUtil.findChildrenOfType(file, LESSVariableDeclaration::class.java)
            .filter { it.name == symbol.name && isVisible(it, symbol.element) }
        XfLessKind.MIXIN -> PsiTreeUtil.findChildrenOfType(file, LESSMixin::class.java)
            .filter { it.name == symbol.name && isVisible(it, symbol.element) }
    }

    private fun isVisible(declaration: PsiElement, usage: PsiElement): Boolean {
        val scope = PsiTreeUtil.getParentOfType(declaration, LessRuleset::class.java, true) ?: return true
        return PsiTreeUtil.isAncestor(scope, usage, false)
    }

    private fun declarationsIn(project: Project, template: String, symbol: XfLessSymbol): List<PsiElement> {
        val type = template.substringBefore(':')
        val title = template.substringAfter(':')
        val location = XfSymbols.locations(project, XfSymbolKeys.template(type, title)).firstOrNull() ?: return emptyList()
        if (!location.file.isValid) return emptyList()
        return if (location.isOutput) outputDeclarations(project, location, symbol) else dataDeclarations(project, location, symbol)
    }

    private fun outputDeclarations(project: Project, location: XfSymbolLocation, symbol: XfLessSymbol): List<PsiElement> {
        val provider = PsiManager.getInstance(project).findFile(location.file)?.viewProvider ?: return emptyList()
        val language = provider.languages.firstOrNull { it != XfLanguage } ?: return emptyList()
        val styles = provider.getPsi(language) ?: return emptyList()
        return when (symbol.kind) {
            XfLessKind.VARIABLE -> PsiTreeUtil.findChildrenOfType(styles, LESSVariableDeclaration::class.java)
                .filter { it.name == symbol.name && PsiTreeUtil.getParentOfType(it, LessRuleset::class.java, true) == null }
            XfLessKind.MIXIN -> PsiTreeUtil.findChildrenOfType(styles, LESSMixin::class.java)
                .filter { it.name == symbol.name && PsiTreeUtil.getParentOfType(it, LessRuleset::class.java, true) == null }
        }
    }

    private fun dataDeclarations(project: Project, location: XfSymbolLocation, symbol: XfLessSymbol): List<PsiElement> {
        val file = location.file
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.charsSequence ?: LoadTextUtil.loadText(file)
        val start = location.symbol.offset
        val end = StringUtil.indexOf(text, TEMPLATE_END, start).takeIf { it >= 0 } ?: text.length
        val pattern = when (symbol.kind) {
            XfLessKind.VARIABLE -> Regex("""(?m)^[ \t]*(@${Regex.escape(symbol.name)})\s*:""")
            XfLessKind.MIXIN -> Regex("""(?m)^[ \t]*(\.${Regex.escape(symbol.name)})\s*(?:\((?:[^(){}]|\([^(){}]*\))*\))?\s*(?:when\b[^{;]*)?\{""")
        }
        return pattern.findAll(text.subSequence(start, end))
            .map { XfSymbolTarget(project, file, start + it.groups[1]!!.range.first, symbol.key) }
            .toList()
    }

    private fun propertyName(name: String): String = name.removePrefix(STYLE_PROPERTY_PREFIX).substringBefore("--")

    private fun isStyleProperty(project: Project, property: String): Boolean {
        if (XfSymbols.locations(project, XfSymbolKeys.property(property)).isNotEmpty()) return true
        return XfSymbols.keys(project, XfSymbolKeys.PROPERTY_PREFIX).isEmpty()
    }
}

object XfLessContext {
    fun implicitTemplates(type: String, title: String): List<String> {
        val prefix = title.removeSuffix(".less").substringBefore('_')
        return listOf(SETUP_TEMPLATE, "$type:$prefix$PREPEND_SUFFIX")
    }

    fun templateOf(file: PsiFile): String? {
        val virtualFile = file.viewProvider.virtualFile
        val type = XfTemplateFiles.templateType(virtualFile) ?: return null
        return "$type:${XfTemplateFiles.templateTitle(virtualFile)}"
    }

    fun templates(file: PsiFile): List<String> {
        val project = file.project
        if (DumbService.isDumb(project)) return emptyList()
        return CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(collectTemplates(project, templateOf(file)), PsiModificationTracker.getInstance(project))
        }
    }

    fun definitions(file: PsiFile): Set<String> {
        val project = file.project
        if (DumbService.isDumb(project)) return emptySet()
        return CachedValuesManager.getCachedValue(file) {
            val names = HashSet<String>()
            for (template in collectTemplates(project, templateOf(file))) {
                val (type, title) = template.substringBefore(':') to template.substringAfter(':')
                XfSymbols.detail(project, XfLessKeys.definitions(type, title))?.split(' ')?.filterTo(names) { it.isNotEmpty() }
            }
            CachedValueProvider.Result.create(names as Set<String>, PsiModificationTracker.getInstance(project))
        }
    }

    private fun collectTemplates(project: Project, current: String?): List<String> {
        current ?: return emptyList()
        val type = current.substringBefore(':')
        val title = current.substringAfter(':')
        val queue = ArrayDeque(implicitTemplates(type, title) + current)
        val visited = LinkedHashSet<String>()
        while (queue.isNotEmpty()) {
            val template = queue.removeFirst()
            if (!visited.add(template)) continue
            val templateType = template.substringBefore(':')
            val templateTitle = template.substringAfter(':')
            XfSymbols.detail(project, XfLessKeys.includes(templateType, templateTitle))?.split(' ')?.filterTo(queue) { it.isNotEmpty() }
        }
        return visited.toList()
    }
}

class XfLessInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in TOOLS) return false
        val file = element.containingFile ?: return false
        if (file.viewProvider !is XfFileViewProvider) return false
        val symbol = XfLessResolver.symbolAt(element) ?: return false
        return XfLessResolver.isKnown(file, symbol)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY
}

class XfLessGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val provider = sourceElement?.containingFile?.viewProvider as? XfFileViewProvider ?: return null
        val language: Language = provider.templateDataLanguage
        val element = provider.findElementAt(offset, language) ?: return null
        val styles = element.containingFile ?: return null
        val symbol = XfLessResolver.symbolAt(element) ?: return null
        return XfLessResolver.targets(styles, symbol).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
