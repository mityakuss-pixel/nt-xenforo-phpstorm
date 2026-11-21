package nt.xenforo.less

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import nt.xenforo.index.XfLessKeys
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfTemplateFiles

private const val SETUP_TEMPLATE = "public:setup.less"
private const val PREPEND_SUFFIX = "_prepend.less"
private const val STYLE_PROPERTY_PREFIX = "xf-"
private const val ICON_PREFIX = "fa-var-"
private const val VARIABLE_MARK = "@"
private const val MIXIN_MARK = "."
private const val VARIABLE_TOOL = "LessUnresolvedVariable"
private const val MIXIN_TOOL = "LessUnresolvedMixin"
private const val BY_NAME_TOOL = "LessResolvedByNameOnly"
private val TOOLS = setOf(VARIABLE_TOOL, MIXIN_TOOL, BY_NAME_TOOL)

object XfLessContext {
    fun implicitTemplates(type: String, title: String): List<String> {
        val prefix = title.removeSuffix(".less").substringBefore('_')
        return listOf(SETUP_TEMPLATE, "$type:$prefix$PREPEND_SUFFIX")
    }

    fun definitions(file: PsiFile): Set<String> {
        val virtualFile = file.viewProvider.virtualFile
        val type = XfTemplateFiles.templateType(virtualFile) ?: return emptySet()
        val title = XfTemplateFiles.templateTitle(virtualFile)
        val project = file.project
        if (DumbService.isDumb(project)) return emptySet()
        return CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(collect(project, type, title), PsiModificationTracker.getInstance(project))
        }
    }

    fun isKnown(file: PsiFile, name: String): Boolean {
        val project = file.project
        if (DumbService.isDumb(project)) return false
        val bare = name.removePrefix(VARIABLE_MARK).removePrefix(MIXIN_MARK)
        if (bare.startsWith(ICON_PREFIX) && name.startsWith(VARIABLE_MARK)) return true
        if (bare.startsWith(STYLE_PROPERTY_PREFIX)) return isStyleProperty(project, bare.removePrefix(STYLE_PROPERTY_PREFIX).substringBefore("--"))
        return name in definitions(file)
    }

    private fun isStyleProperty(project: Project, property: String): Boolean {
        if (XfSymbols.locations(project, XfSymbolKeys.property(property)).isNotEmpty()) return true
        return XfSymbols.keys(project, XfSymbolKeys.PROPERTY_PREFIX).isEmpty()
    }

    private fun collect(project: Project, type: String, title: String): Set<String> {
        val queue = ArrayDeque(implicitTemplates(type, title) + "$type:$title")
        val visited = HashSet<String>()
        val names = HashSet<String>()
        while (queue.isNotEmpty()) {
            val template = queue.removeFirst()
            if (!visited.add(template)) continue
            val templateType = template.substringBefore(':')
            val templateTitle = template.substringAfter(':')
            XfSymbols.detail(project, XfLessKeys.definitions(templateType, templateTitle))?.split(' ')?.filterTo(names) { it.isNotEmpty() }
            XfSymbols.detail(project, XfLessKeys.includes(templateType, templateTitle))?.split(' ')?.filterTo(queue) { it.isNotEmpty() }
        }
        return names
    }
}

class XfLessInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in TOOLS) return false
        val file = element.containingFile ?: return false
        if (file.viewProvider !is XfFileViewProvider) return false
        return candidates(element.text.trim(), toolId).any { XfLessContext.isKnown(file, it) }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY

    private fun candidates(text: String, toolId: String): List<String> {
        val name = text.removePrefix("@").removePrefix("{").removeSuffix("}").removePrefix(".").substringBefore('(').trim()
        if (name.isEmpty()) return emptyList()
        return when (toolId) {
            VARIABLE_TOOL -> listOf(VARIABLE_MARK + name)
            MIXIN_TOOL -> listOf(MIXIN_MARK + name)
            else -> listOf(VARIABLE_MARK + name, MIXIN_MARK + name)
        }
    }
}
