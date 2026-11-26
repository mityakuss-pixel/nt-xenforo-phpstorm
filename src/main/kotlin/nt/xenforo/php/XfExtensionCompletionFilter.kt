package nt.xenforo.php

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ExtendsList
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.commands.XfCommandSettings

private const val PROXY_PREFIX = "XFCP_"

class XfExtensionCompletionFilter : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (!XfCommandSettings.getInstance(position.project).hideExtensionClasses) return
        val inExtends = PsiTreeUtil.getParentOfType(position, ExtendsList::class.java) != null
        result.runRemainingContributors(parameters) { completion ->
            val phpClass = completion.lookupElement.psiElement as? PhpClass
            if (phpClass == null || !isHidden(phpClass, inExtends)) result.passResult(completion)
        }
    }

    private fun isHidden(phpClass: PhpClass, inExtends: Boolean): Boolean {
        if (phpClass.name.startsWith(PROXY_PREFIX)) return !inExtends
        return phpClass.extendsList.referenceElements.any { it.name?.startsWith(PROXY_PREFIX) == true }
    }
}
