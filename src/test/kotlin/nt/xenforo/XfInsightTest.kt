package nt.xenforo

import com.intellij.codeInsight.hints.declarative.CollapsiblePresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.CollapseState
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import nt.xenforo.documentation.XfDocumentationTarget
import nt.xenforo.documentation.XfDocumentationTargetProvider
import nt.xenforo.editor.XfParameterInfoHandler
import nt.xenforo.editor.XfPhraseInlayHintsProvider
import nt.xenforo.editor.XfSignature
import nt.xenforo.lang.XfLanguage

class XfInsightTest : XfTestCase() {
    fun testParameterInfoForBuiltinFunction() {
        configureTemplate("{{ phrase('demo', <caret>) }}")
        val (signature, current) = parameterInfo()
        assertEquals(listOf("name", "params = []"), signature.parameters)
        assertEquals(1, current)
    }

    fun testParameterInfoForCustomFunction() {
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_badge(<caret>) }}")
        val (signature, current) = parameterInfo()
        assertEquals(listOf("\$user", "\$size = 's'"), signature.parameters)
        assertEquals(0, current)
    }

    fun testPhraseDocumentationShowsText() {
        addDataFile("phrases.xml", "<phrases>\n  <phrase title=\"stick_thread\" version_id=\"1\"><![CDATA[Stick thread]]></phrase>\n</phrases>")
        configureTemplate("{{ phrase('stick_th<caret>read') }}")
        val html = documentation()
        assertTrue(html, html.contains("Stick thread"))
        assertTrue(html, html.contains("XF/_data/phrases.xml"))
    }

    fun testTagDocumentationListsAttributes() {
        configureTemplate("<xf:for<caret>each loop=\"\$items\" value=\"\$item\"></xf:foreach>")
        val html = documentation()
        assertTrue(html, html.contains("loop, value"))
    }

    fun testFunctionDocumentationShowsSignature() {
        configureTemplate("{{ ph<caret>rase('x') }}")
        assertTrue(documentation().contains("(name, params = [])"))
    }

    fun testOptionDocumentationShowsTitle() {
        addDataFile("options.xml", "<options>\n  <option option_id=\"boardTitle\" edit_format=\"textbox\" data_type=\"string\" advanced=\"0\"/>\n</options>")
        addOutputFile("phrases/option.boardTitle.txt", "Board title")
        configureTemplate("{{ \$xf.options.board<caret>Title }}")
        val html = documentation()
        assertTrue(html, html.contains("Board title"))
        assertTrue(html, html.contains("textbox"))
    }

    fun testGlobalDocumentation() {
        configureTemplate("{{ \$xf.vis<caret>itor }}")
        assertTrue(documentation().contains("The user viewing the page"))
    }

    fun testPhraseTextHint() {
        addOutputFile("phrases/stick_thread.txt", "Stick thread")
        val file = configureTemplate("<span>{{ phrase('stick_thread') }}</span>")
        val hints = inlays()
        val offset = file.text.indexOf(") }}") + 1
        assertEquals(listOf(offset to "Stick thread"), hints)
    }

    fun testNoHintForUnknownPhrase() {
        configureTemplate("{{ phrase('missing_phrase') }}")
        assertEmpty(inlays())
    }

    private fun parameterInfo(): Pair<XfSignature, Int> {
        val handler = XfParameterInfoHandler()
        val create = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val owner = handler.findElementForParameterInfo(create)
        assertNotNull(owner)
        val signature = create.itemsToShow.orEmpty().single() as XfSignature
        val update = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        handler.updateParameterInfo(owner!!, update)
        return signature to update.currentParameter
    }

    private fun documentation(): String {
        val targets = XfDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset)
        return (targets.single() as XfDocumentationTarget).html()
    }

    private fun inlays(): List<Pair<Int, String>> {
        val base = myFixture.file.viewProvider.getPsi(XfLanguage)
        val collector = XfPhraseInlayHintsProvider().createCollector(myFixture.file, myFixture.editor) as SharedBypassCollector
        val sink = RecordingSink()
        for (element in SyntaxTraverser.psiTraverser(base)) collector.collectFromElement(element, sink)
        return sink.hints
    }

    private class RecordingSink : InlayTreeSink {
        val hints = ArrayList<Pair<Int, String>>()

        override fun addPresentation(
            position: InlayPosition,
            payloads: List<InlayPayload>?,
            tooltip: String?,
            hintFormat: HintFormat,
            builder: PresentationTreeBuilder.() -> Unit,
        ) {
            val text = StringBuilder()
            TextBuilder(text).builder()
            hints += (position as InlineInlayPosition).offset to text.toString()
        }

        override fun whenOptionEnabled(optionId: String, block: () -> Unit) = block()
    }

    private class TextBuilder(private val text: StringBuilder) : PresentationTreeBuilder {
        override fun list(builder: PresentationTreeBuilder.() -> Unit) = builder()

        override fun collapsibleList(
            state: CollapseState,
            expandedState: CollapsiblePresentationTreeBuilder.() -> Unit,
            collapsedState: CollapsiblePresentationTreeBuilder.() -> Unit,
        ) = error("Not expected")

        override fun text(text: String, actionData: InlayActionData?) {
            this.text.append(text)
        }

        override fun clickHandlerScope(actionData: InlayActionData, builder: PresentationTreeBuilder.() -> Unit) = builder()
    }

    private companion object {
        val LISTENER = """
            <?php
            namespace Demo\Test;
            class Listener
            {
                public static function templaterSetup(\XF\Container ${'$'}container, \XF\Template\Templater &${'$'}templater)
                {
                    ${'$'}templater->addFunction('demo_badge', function (\XF\Template\Templater ${'$'}templater, &${'$'}escape, ${'$'}user, ${'$'}size = 's') {
                        return '';
                    });
                }
            }
        """.trimIndent()
    }
}
