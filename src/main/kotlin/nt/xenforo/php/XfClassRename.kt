package nt.xenforo.php

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.PhpClass

class XfShortNameReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val phpClass = parameters.elementToSearch as? PhpClass ?: return
        val name = phpClass.name
        val words = XfShortKind.entries.mapNotNull { it.suffix }.distinct()
            .filter { name.endsWith(it) && name.length > it.length }
            .map { name.removeSuffix(it) }
        for (word in words) {
            parameters.optimizer.searchWord(word, parameters.effectiveSearchScope, (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_STRINGS.toInt()).toShort(), true, phpClass)
        }
    }
}

object XfClassRename {
    fun renameLastSegment(value: String, newName: String, suffixes: Collection<String> = emptyList()): String {
        val cut = maxOf(value.lastIndexOf('\\'), value.lastIndexOf(':')) + 1
        val tail = value.substring(cut)
        val suffix = suffixes.firstOrNull { newName.endsWith(it) && newName.length > it.length && !tail.endsWith(it) }
        return value.substring(0, cut) + (if (suffix == null) newName else newName.removeSuffix(suffix))
    }

    fun shortName(fqn: String, context: XfShortContext): String =
        context.kinds.firstNotNullOfOrNull { XfShortNames.shortName(fqn, it, context.section) } ?: fqn.trimStart('\\')
}
