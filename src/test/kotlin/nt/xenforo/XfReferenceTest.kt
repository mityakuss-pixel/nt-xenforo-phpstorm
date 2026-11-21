package nt.xenforo

import com.intellij.psi.PsiFile
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.psi.XfTagElement
import nt.xenforo.reference.XfNameReference

class XfReferenceTest : XfTestCase() {
    fun testIncludeResolvesToTemplateFile() {
        addOutputFile("templates/public/demo_widget.html", "<div>widget</div>")
        configureTemplate("<xf:include template=\"demo_wid<caret>get\" />")
        val target = resolve()
        assertInstanceOf(target, PsiFile::class.java)
        assertEquals("demo_widget.html", (target as PsiFile).name)
    }

    fun testMacroResolvesToDefinition() {
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\" arg-user=\"!\">x</xf:macro>")
        configureTemplate("<xf:macro id=\"demo_widget::ca<caret>rd\" arg-user=\"\$user\" />")
        val target = resolve()
        assertInstanceOf(target, XfTagElement::class.java)
        assertEquals("macro", (target as XfTagElement).tagName)
    }

    fun testLocalMacro() {
        configureTemplate("<xf:macro id=\"row\">x</xf:macro>\n<xf:macro id=\"r<caret>ow\" />", name = "demo_page.html")
        assertInstanceOf(resolve(), XfTagElement::class.java)
    }

    fun testPhraseFromDataFileResolvesToOffset() {
        addDataFile("phrases.xml", "<phrases>\n  <phrase title=\"demo_goodbye\" version_id=\"1\"><![CDATA[Goodbye]]></phrase>\n</phrases>")
        configureTemplate("{{ phrase('demo_good<caret>bye') }}")
        val target = resolve()
        assertInstanceOf(target, XfSymbolTarget::class.java)
        assertEquals("demo_goodbye", (target as XfSymbolTarget).name)
    }

    fun testOutputWinsOverData() {
        addOutputFile("phrases/demo_greeting.txt", "Hello")
        addDataFile("phrases.xml", "<phrases>\n  <phrase title=\"demo_greeting\"><![CDATA[Old]]></phrase>\n</phrases>")
        configureTemplate("{{ phrase('demo_gree<caret>ting') }}")
        assertEquals("demo_greeting.txt", (resolve() as PsiFile).name)
    }

    fun testUnknownNamesAreSoft() {
        configureTemplate("{{ phrase('missing_ph<caret>rase') }}")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        assertTrue(reference.isSoft)
        assertNull(reference.resolve())
    }

    fun testRenameTemplateUpdatesInclude() {
        addOutputFile("templates/public/demo_widget.html", "<div>widget</div>")
        configureTemplate("<xf:include template=\"demo_wid<caret>get\" />")
        val target = resolve() as PsiFile
        myFixture.renameElement(target, "demo_block.html")
        assertEquals("<xf:include template=\"demo_block\" />", myFixture.editor.document.text)
    }

    private fun resolve() = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference).multiResolve(false).firstOrNull()?.element
}
