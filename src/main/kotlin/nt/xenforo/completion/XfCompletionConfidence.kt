package nt.xenforo.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.reference.XfNameSlots

class XfCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val host = PsiTreeUtil.getParentOfType(contextElement, XfReferenceHost::class.java, false) ?: return ThreeState.UNSURE
        return if (XfNameSlots.of(host).isNotEmpty()) ThreeState.NO else ThreeState.UNSURE
    }
}
