package nt.xenforo.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.XfBundle
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.php.XfPhpNames
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameSlots

private const val ADMIN_TYPE = "admin"
private val OUTSIDE_ADMIN_TYPES = setOf("public", "email")
private val OUTSIDE_ADMIN_NAMESPACES = listOf("\\Pub\\", "\\Api\\")

object XfAdminTemplates {
    private val ADMIN_PREFIX = XfSymbolKeys.templatePrefix(ADMIN_TYPE)

    fun isOutsideAdminTemplate(type: String?): Boolean = type in OUTSIDE_ADMIN_TYPES

    fun isOutsideAdminClass(fqn: String): Boolean = OUTSIDE_ADMIN_NAMESPACES.any { fqn.contains(it) }

    fun referencesAdmin(host: XfReferenceHost): Boolean = XfNameSlots.of(host).any { it.kind == XfNameKind.TEMPLATE && it.keyPrefix == ADMIN_PREFIX }

    fun referencesAdmin(literal: StringLiteralExpression): Boolean {
        if (!literal.contents.startsWith("$ADMIN_TYPE:")) return false
        val slot = XfPhpNames.slot(literal) ?: return false
        return slot.kind == XfNameKind.TEMPLATE && slot.keyPrefix == ADMIN_PREFIX
    }
}

class XfAdminTemplateInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file.originalFile.virtualFile ?: holder.file.viewProvider.virtualFile
        if (!XfAdminTemplates.isOutsideAdminTemplate(XfTemplateFiles.templateType(file))) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is XfReferenceHost || !XfAdminTemplates.referencesAdmin(element)) return
                holder.registerProblem(element, XfBundle.message("inspection.admin.template.message"), ProblemHighlightType.WARNING, element.valueRange)
            }
        }
    }
}

class XfAdminTemplatePhpInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element !is StringLiteralExpression || !XfAdminTemplates.referencesAdmin(element)) return
            val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java) ?: return
            if (!XfAdminTemplates.isOutsideAdminClass(phpClass.fqn)) return
            holder.registerProblem(element, XfBundle.message("inspection.admin.template.message"), ProblemHighlightType.WARNING, element.valueRange)
        }
    }
}
