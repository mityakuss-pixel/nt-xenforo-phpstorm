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

    fun testMacroWithTypePrefix() {
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\">x</xf:macro>")
        configureTemplate("<xf:macro id=\"public:demo_widget::ca<caret>rd\" />", type = "admin")
        assertInstanceOf(resolve(), XfTagElement::class.java)
    }

    fun testMacroWithTemplateAndNameAttributes() {
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\">x</xf:macro>")
        configureTemplate("<xf:macro template=\"demo_widget\" name=\"ca<caret>rd\" />")
        assertInstanceOf(resolve(), XfTagElement::class.java)
    }

    fun testCallMacroFunction() {
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\">x</xf:macro>")
        configureTemplate("{{ call_macro('demo_widget', 'ca<caret>rd') }}")
        assertInstanceOf(resolve(), XfTagElement::class.java)
    }

    fun testMacroFromDataTemplates() {
        addDataFile("templates.xml", "<templates>\n  <template type=\"public\" title=\"demo_core_macros\" version_id=\"1\"><![CDATA[<xf:macro id=\"button\">b</xf:macro>]]></template>\n</templates>")
        configureTemplate("<xf:macro id=\"demo_core_macros::but<caret>ton\" />")
        val target = resolve()
        assertInstanceOf(target, XfSymbolTarget::class.java)
        assertEquals("button", (target as XfSymbolTarget).name)
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

    fun testPhraseModifiersAreIgnored() {
        addOutputFile("phrases/demo_user_name.txt", "User name")
        for (name in listOf("demo_user_name:", "(demo_user_name)", "demo_user_name...", "demo_user_name,", "(demo_user_name...):")) {
            val caret = name.indexOf("user")
            configureTemplate("{{ phrase('" + name.substring(0, caret) + "<caret>" + name.substring(caret) + "') }}", name = "demo_${name.hashCode().toUInt()}.html")
            val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
            assertEquals(name, "demo_user_name", reference.canonicalText)
            assertEquals(name, "demo_user_name.txt", (reference.multiResolve(false).firstOrNull()?.element as? PsiFile)?.name)
        }
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
