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
import com.intellij.openapi.util.text.StringUtil
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
        assertEquals(listOf("\$name", "\$params = []"), signature.parameters)
        assertEquals(1, current)
    }

    fun testParameterInfoForCustomFunction() {
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_badge(<caret>) }}")
        val (signature, current) = parameterInfo()
        assertEquals(listOf("\$user", "\$size = 's'"), signature.parameters)
        assertEquals(0, current)
    }

    fun testCustomFunctionDocumentationUsesPhpDoc() {
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\n    \"title\": \"Demo\"\n}")
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_to<caret>tal(\$items) }}")
        val html = documentation()
        val text = text(html)
        assertTrue(text, text.contains("demo_total(\n    array \$items,\n    float|int \$rate = 1,\n    array \$options = []\n): Phrase"))
        assertTrue(text, text.contains("Sums item prices."))
        assertTrue(text, text.contains("array \$items Items with a price key"))
        assertFalse(text, text.contains("\$escape"))
        assertTrue(text, text.contains("Phrase Formatted total"))
        assertTrue(html, html.contains("Demo/Test"))
    }

    fun testParametersAreListedWithoutPhpDoc() {
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_ba<caret>dge(\$user) }}")
        val html = documentation()
        val text = text(html)
        assertTrue(text, text.contains("demo_badge(\n    \$user,\n    \$size = 's'\n)"))
        assertTrue(text, text.contains("\$user\n\$size"))
    }

    fun testBuiltinParametersAreListed() {
        configureTemplate("{{ ph<caret>rase('x') }}")
        val html = documentation()
        assertTrue(html, text(html).contains("\$name\n\$params"))
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
        assertTrue(text(documentation()).contains("phrase(\n    \$name,\n    \$params = []\n)"))
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

    private fun text(html: String): String =
        StringUtil.unescapeXmlEntities(html.replace("<br>", "\n").replace(Regex("<[^>]+>"), ""))

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
                    ${'$'}templater->addFunction('demo_total', [self::class, 'fnTotal']);
                }

                /**
                 * Sums item prices.
                 *
                 * @param \XF\Template\Templater ${'$'}templater
                 * @param bool ${'$'}escape
                 * @param array ${'$'}items Items with a price key
                 * @param float|int ${'$'}rate Rate to apply
                 * @return \XF\Phrase Formatted total
                 */
                public static function fnTotal(${'$'}templater, &${'$'}escape, array ${'$'}items, ${'$'}rate = 1, array ${'$'}options = []) {}
            }
        """.trimIndent()
    }
}
