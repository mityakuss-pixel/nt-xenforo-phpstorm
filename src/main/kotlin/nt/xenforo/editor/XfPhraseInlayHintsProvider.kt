package nt.xenforo.editor

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfStringExpr
import nt.xenforo.reference.XfPhraseNames

private const val PHRASE_FUNCTION = "phrase"
private const val HINT_LIMIT = 40

class XfPhraseInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector(file.project)

    private class Collector(private val project: Project) : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val call = element as? XfFunctionCallExpr ?: return
            if (!call.callName.equals(PHRASE_FUNCTION, ignoreCase = true)) return
            val value = (call.arguments?.exprList?.firstOrNull() as? XfStringExpr)?.value ?: return
            val name = XfPhraseNames.effective(value).takeIf { it.isNotEmpty() } ?: return
            val text = XfSymbols.detail(project, XfSymbolKeys.phrase(name))?.takeIf { it.isNotBlank() } ?: return
            sink.addPresentation(InlineInlayPosition(call.textRange.endOffset, true), tooltip = text, hintFormat = HintFormat.default) {
                text(shorten(text))
            }
        }

        private fun shorten(text: String): String = if (text.length > HINT_LIMIT) text.take(HINT_LIMIT).trimEnd() + "…" else text
    }
}
