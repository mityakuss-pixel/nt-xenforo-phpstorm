package nt.xenforo

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler

class XfTemplateChecksTest : XfTestCase() {
    fun testPhraseNameMustBeLiteral() {
        configureTemplate("{{ phrase(\$name) }}\n{{ phrase('ok') }}\n{{ phrase_dynamic(\$name) }}\n{{ phrase(\"also_ok\") }}\n{{ phrase(\$xf.options.boardTitle) }}")
        assertEquals(listOf("\$name", "\$xf.options.boardTitle"), highlighted(XfBundle.message("annotator.phrase.name.literal")))
    }

    fun testPhraseDynamicFix() {
        configureTemplate("{{ phrase(\$na<caret>me, {'count': 2}) }}")
        val fix = myFixture.findSingleIntention(XfBundle.message("annotator.phrase.dynamic.fix"))
        ShowIntentionActionsHandler.chooseActionAndInvoke(myFixture.file, myFixture.editor, fix, fix.text)
        assertEquals("{{ phrase_dynamic(\$name, {'count': 2}) }}", myFixture.editor.document.text)
    }

    private fun highlighted(description: String): List<String> =
        myFixture.doHighlighting().filter { it.description == description }.sortedBy { it.startOffset }.map { it.text }
}
