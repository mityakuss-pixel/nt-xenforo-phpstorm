package nt.xenforo

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.usageView.UsageViewTypeLocation
import nt.xenforo.documentation.XfDocumentationTarget
import nt.xenforo.documentation.XfDocumentationTargetProvider
import nt.xenforo.index.XfJsHandlers
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.index.XfSymbols
import nt.xenforo.reference.XfHtmlHandlerReference
import nt.xenforo.reference.XfNameReference

class XfJsHandlerTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("js/xf/core_handlers.js", HANDLERS)
        myFixture.addFileToProject("js/xf/core_handlers.min.js", "XF.Element.register('minified', 'XF.MinifiedElement')")
    }

    fun testInitAttributeResolvesToClassDefinition() {
        configureTemplate("<div data-xf-init=\"tool<caret>tip\"></div>")
        val target = resolveAtCaret() as XfSymbolTarget
        assertEquals("XF.TooltipElement", target.name)
        assertEquals(HANDLERS.indexOf("XF.TooltipElement = "), target.textOffset)
        assertEquals("XF JavaScript handler", ElementDescriptionUtil.getElementDescription(target, UsageViewTypeLocation.INSTANCE))
    }

    fun testSecondHandlerInInitAttribute() {
        configureTemplate("<div data-xf-init=\"tooltip la<caret>zy\"></div>")
        val target = resolveAtCaret() as XfSymbolTarget
        assertEquals("lazy", target.name)
        assertEquals(HANDLERS.indexOf("XF.Element.register('lazy'"), target.textOffset)
    }

    fun testEventAttributeInXfTag() {
        configureTemplate("<xf:button data-xf-click=\"over<caret>lay\">x</xf:button>")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        val target = reference.multiResolve(false).single().element as XfSymbolTarget
        assertEquals("XF.OverlayClick", target.name)
    }

    fun testEventAttributeInHtml() {
        configureTemplate("<a href=\"#\" data-xf-click=\"over<caret>lay\">x</a>")
        assertEquals("XF.OverlayClick", (resolveAtCaret() as XfSymbolTarget).name)
    }

    fun testCompletionInHtmlAttribute() {
        configureTemplate("<div data-xf-init=\"<caret>\"></div>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "tooltip", "lazy")
        assertDoesntContain(variants, "minified", "overlay")
    }

    fun testCompletionForEventsInXfTag() {
        configureTemplate("<xf:button data-xf-click=\"<caret>\">x</xf:button>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "overlay")
        assertDoesntContain(variants, "tooltip", "preview", "drop-zone")
    }

    fun testAnyRegisteredEventInHtml() {
        configureTemplate("<div data-xf-mouseover=\"pre<caret>view\"></div>")
        val target = resolveAtCaret() as XfSymbolTarget
        assertEquals("Demo.PreviewHover", target.name)
        assertEquals(HANDLERS.indexOf("Demo.PreviewHover = "), target.textOffset)
    }

    fun testEventWithHyphenInXfTag() {
        configureTemplate("<xf:button data-xf-drag-end=\"drop-<caret>zone\">x</xf:button>")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        val target = reference.multiResolve(false).single().element as XfSymbolTarget
        assertEquals(HANDLERS.indexOf("XF.Event.register('drag-end'"), target.textOffset)
    }

    fun testCompletionForOtherEvent() {
        configureTemplate("<div data-xf-mouseover=\"<caret>\"></div>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "preview")
        assertDoesntContain(variants, "overlay", "tooltip", "drop-zone")
    }

    fun testDocumentationNamesClass() {
        configureTemplate("<div data-xf-init=\"tool<caret>tip\"></div>")
        val targets = XfDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset)
        val html = (targets.single() as XfDocumentationTarget).html()
        assertTrue(html, html.contains("XF.TooltipElement"))
        assertTrue(html, html.contains("core_handlers.js"))
    }

    fun testDynamicValuesAndKeysAreIgnored() {
        configureTemplate("<div data-xf-init=\"{{ \$init }}\" data-xf-key=\"s\"></div>")
        val values = listOf(myFixture.file.text.indexOf("init }}"), myFixture.file.text.indexOf("\"s\"") + 1)
        for (offset in values) assertNull(XfHtmlHandlerReference.at(myFixture.file, offset))
        assertNull(XfJsHandlers.prefixFor("data-xf-key"))
    }

    fun testMinifiedFilesAreSkipped() {
        assertDoesntContain(XfSymbols.keys(project, "je:"), "je:minified")
    }

    private fun resolveAtCaret(): Any? {
        val reference = TargetElementUtil.findReference(myFixture.editor, myFixture.caretOffset) as PsiPolyVariantReference
        assertInstanceOf(reference, XfHtmlHandlerReference::class.java)
        return reference.multiResolve(false).single().element
    }

    private companion object {
        val HANDLERS = """
            XF.TooltipElement = XF.create({
            	init: function () {}
            })
            XF.OverlayClick = XF.Event.newHandler({
            	eventNameSpace: 'XFOverlayClick'
            })
            XF.Element.register('tooltip', 'XF.TooltipElement')
            XF.Event.register('click', 'overlay', 'XF.OverlayClick')
            XF.Element.register('lazy', 'XF.LazyElement')
            Demo.PreviewHover = XF.Event.newHandler({
            	eventNameSpace: 'DemoPreviewHover'
            })
            XF.Event.register('mouseOver', 'preview', 'Demo.PreviewHover')
            XF.Event.register('drag-end', 'drop-zone', 'Demo.DropZone')
        """.trimIndent()
    }
}
