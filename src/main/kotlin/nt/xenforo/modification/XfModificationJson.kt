package nt.xenforo.modification

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import nt.xenforo.XfBundle.message
import nt.xenforo.commands.XfAddOns
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbolScanner
import nt.xenforo.index.XfSymbols

private const val TEMPLATE_PROPERTY = "template"
private const val FIND_PROPERTY = "find"
private const val ACTION_PROPERTY = "action"
private const val CORE_TEMPLATES_XML = "src/addons/XF/_data/templates.xml"
private const val CORE_TEMPLATES_OUTPUT = "src/addons/XF/_output/templates"

private fun modificationType(file: VirtualFile?): String? =
    file?.takeIf { XfSymbolScanner.isModificationJson(it) }?.parent?.name

private fun templateValue(literal: JsonStringLiteral): String? {
    val property = literal.parent as? JsonProperty ?: return null
    if (property.name != TEMPLATE_PROPERTY || property.value != literal || property.parent?.parent !is JsonFile) return null
    return literal.value
}

class XfModificationJsonReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JsonStringLiteral::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
                val type = modificationType(literal.containingFile?.originalFile?.virtualFile) ?: return PsiReference.EMPTY_ARRAY
                val title = templateValue(literal)?.takeIf { it.isNotEmpty() } ?: return PsiReference.EMPTY_ARRAY
                return arrayOf(XfModificationTemplateReference(literal, type, title))
            }
        })
    }
}

class XfModificationTemplateReference(literal: JsonStringLiteral, private val type: String, private val title: String) :
    PsiPolyVariantReferenceBase<JsonStringLiteral>(literal, TextRange(1, literal.textLength - 1), true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (DumbService.isDumb(element.project)) return ResolveResult.EMPTY_ARRAY
        return PsiElementResolveResult.createResults(XfSymbols.targets(element.project, XfSymbolKeys.template(type, title), title))
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

class XfModificationJsonInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        val file = holder.file.originalFile.virtualFile
        val type = modificationType(file)
        if (type == null || DumbService.isDumb(project) || !hasCoreTemplates(project, file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is JsonObject || element.parent !is JsonFile) return
                check(holder, element, type)
            }
        }
    }

    private fun check(holder: ProblemsHolder, root: JsonObject, type: String) {
        val project = holder.project
        val template = root.findProperty(TEMPLATE_PROPERTY)?.value as? JsonStringLiteral ?: return
        val find = root.findProperty(FIND_PROPERTY)?.value as? JsonStringLiteral ?: return
        val action = (root.findProperty(ACTION_PROPERTY)?.value as? JsonStringLiteral)?.value ?: XfModifications.STR_REPLACE
        val source = XfModifications.templateSource(project, type, template.value)
        if (source == null) {
            holder.registerProblem(template, message("modification.problem.template", "$type:${template.value}"), ProblemHighlightType.WARNING)
            return
        }
        val matches = XfModifications.matches(action, find.value, source.body)
        when {
            matches == null -> holder.registerProblem(find, message("modification.error.pattern"), ProblemHighlightType.WARNING)
            matches.isEmpty() -> holder.registerProblem(find, message("modification.problem.find", "$type:${template.value}"), ProblemHighlightType.WARNING)
        }
    }

    private fun hasCoreTemplates(project: Project, file: VirtualFile?): Boolean {
        val addOn = XfAddOns.find(project, file) ?: return false
        return addOn.root.findFileByRelativePath(CORE_TEMPLATES_XML) != null || addOn.root.findFileByRelativePath(CORE_TEMPLATES_OUTPUT) != null
    }
}
