package nt.xenforo.php

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.XfBundle.message

class XfShortNameClassIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = message("intention.short.name.family")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val reference = classReference(project, element) ?: return false
        text = message("intention.short.name.text", reference)
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val literal = literal(element) ?: return
        val reference = classReference(project, element) ?: return
        literal.replace(PhpPsiElementFactory.createPhpPsiFromText(project, ClassConstantReference::class.java, reference))
    }

    private fun classReference(project: Project, element: PsiElement): String? {
        if (DumbService.isDumb(project)) return null
        val literal = literal(element) ?: return null
        val context = XfShortNames.context(literal)?.takeIf { it.acceptsClass } ?: return null
        val phpClass = XfShortNames.resolve(project, literal.contents, context) ?: return null
        return "${phpClass.fqn}::class"
    }

    private fun literal(element: PsiElement): StringLiteralExpression? =
        element as? StringLiteralExpression ?: element.parent as? StringLiteralExpression
}
