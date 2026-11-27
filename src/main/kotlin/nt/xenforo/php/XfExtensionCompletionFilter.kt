package nt.xenforo.php

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ExtendsList
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.commands.XfCommandSettings

private const val PROXY_PREFIX = "XFCP_"

object XfExtensionClassFilter {
    fun isProxy(phpClass: PhpClass): Boolean = phpClass.name.startsWith(PROXY_PREFIX)

    fun isExtension(phpClass: PhpClass): Boolean = phpClass.superName?.startsWith(PROXY_PREFIX) == true

    fun isHidden(project: Project, phpClass: PhpClass, inExtends: Boolean = false): Boolean {
        if (!XfCommandSettings.getInstance(project).hideExtensionClasses) return false
        if (isProxy(phpClass)) return !inExtends
        return isExtension(phpClass)
    }
}

class XfExtensionCompletionFilter : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val project = position.project
        if (!XfCommandSettings.getInstance(project).hideExtensionClasses) return
        val inExtends = PsiTreeUtil.getParentOfType(position, ExtendsList::class.java) != null
        result.runRemainingContributors(parameters) { completion ->
            val phpClass = completion.lookupElement.psiElement as? PhpClass
            if (phpClass == null || !XfExtensionClassFilter.isHidden(project, phpClass, inExtends)) result.passResult(completion)
        }
    }
}
