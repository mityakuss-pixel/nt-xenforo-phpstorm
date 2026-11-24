package nt.xenforo.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import nt.xenforo.XfBundle
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.modification.XfModifications
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfTagElement
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameReference
import nt.xenforo.reference.XfNameSlot
import nt.xenforo.reference.XfNameSlots

private const val MACRO_TAG = "macro"
private const val MACRO_KEY_PREFIX = "m:"
private val DEFINITION_ATTRIBUTES = setOf("id", "name")

object XfUnknownMacros {
    fun isDefinition(host: XfReferenceHost): Boolean {
        val attribute = host.parent as? XfAttribute ?: return false
        if (attribute.attributeName.lowercase() !in DEFINITION_ATTRIBUTES) return false
        val header = attribute.header ?: return false
        if (!header.tagName.equals(MACRO_TAG, ignoreCase = true) || header.findAttribute("template") != null) return false
        val tag = header.parent as? XfTagElement ?: return false
        return tag.isBlock && !host.value.contains("::")
    }

    fun reportedTemplate(project: Project, host: XfReferenceHost, slot: XfNameSlot, name: String): String? {
        val (type, title) = template(slot.keyPrefix) ?: return null
        val current = XfNameSlots.isCurrentTemplate(host, slot)
        if (!current && XfSymbols.locations(project, XfSymbolKeys.template(type, title)).isEmpty()) return null
        val definition = Regex("""<xf:macro\b[^>]*\b(?:id|name)\s*=\s*"${Regex.escape(name)}"""")
        val added = XfModifications.forTemplate(project, type, title).any { it.action == XfModifications.CALLBACK || definition.containsMatchIn(it.replace) }
        return if (added) null else "$type:$title"
    }

    private fun template(keyPrefix: String): Pair<String, String>? {
        val body = keyPrefix.removePrefix(MACRO_KEY_PREFIX).removeSuffix(":")
        val separator = body.indexOf(':')
        if (separator <= 0 || separator == body.length - 1) return null
        return body.substring(0, separator) to body.substring(separator + 1)
    }
}

class XfUnknownMacroInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        if (DumbService.isDumb(project)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is XfReferenceHost || XfUnknownMacros.isDefinition(element)) return
                for (reference in element.references) {
                    if (reference !is XfNameReference || reference.slot.kind != XfNameKind.MACRO) continue
                    val name = reference.slot.name(element)
                    if (name.isEmpty() || reference.multiResolve(false).isNotEmpty()) continue
                    val template = XfUnknownMacros.reportedTemplate(project, element, reference.slot, name) ?: continue
                    holder.registerProblem(element, XfBundle.message("inspection.unknown.macro.message", name, template), ProblemHighlightType.WEAK_WARNING, reference.rangeInElement)
                }
            }
        }
    }
}
