package nt.xenforo

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.lang.annotation.HighlightSeverity
import nt.xenforo.inspection.XfUnknownMacroInspection
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameReference

class XfMacroTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\">public card</xf:macro>")
        addOutputFile("templates/admin/demo_widget.html", "<xf:macro name=\"card\">admin card</xf:macro>")
    }

    fun testAllMacroForms() {
        val cases = listOf(
            "<xf:macro id=\"public:demo_widget::ca<caret>rd\" />" to "public card",
            "<xf:macro id=\"demo_widget::ca<caret>rd\" />" to "admin card",
            "<xf:macro id=\"lo<caret>cal\" />" to "local",
            "<xf:macro name=\"public:demo_widget::ca<caret>rd\" />" to "public card",
            "<xf:macro name=\"demo_widget::ca<caret>rd\" />" to "admin card",
            "<xf:macro template=\"public:demo_widget\" name=\"ca<caret>rd\" />" to "public card",
            "<xf:macro template=\"demo_widget\" name=\"ca<caret>rd\" />" to "admin card",
            "<xf:macro name=\"lo<caret>cal\" />" to "local",
        )
        for ((index, case) in cases.withIndex()) {
            val (source, expected) = case
            configureTemplate("<xf:macro name=\"local\">local</xf:macro>\n$source", name = "demo_page_$index.html", type = "admin")
            val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
            assertEquals(source, XfNameKind.MACRO, reference.slot.kind)
            val target = reference.multiResolve(false).firstOrNull()?.element as? XfTagElement
            assertNotNull(source, target)
            assertEquals(source, expected, target!!.text.substringAfter('>').substringBefore('<'))
        }
    }

    fun testTemplatePartOfTypedTemplateAttribute() {
        configureTemplate("<xf:macro template=\"public:demo_wid<caret>get\" name=\"card\" />", type = "admin")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        assertEquals(XfNameKind.TEMPLATE, reference.slot.kind)
        assertEquals("public", reference.multiResolve(false).first().element!!.containingFile.virtualFile.parent.name)
    }

    fun testNewMacroInAnotherFileWithoutSaving() {
        configureTemplate("<xf:macro id=\"demo_widget::fre<caret>sh\" />")
        val widget = myFixture.findFileInTempDir("src/addons/Demo/Test/_output/templates/public/demo_widget.html")
        val document = PsiDocumentManager.getInstance(project).getDocument(PsiManager.getInstance(project).findFile(widget)!!)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n<xf:macro id=\"fresh\">fresh</xf:macro>") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        assertNotNull(reference.multiResolve(false).firstOrNull()?.element)
    }

    fun testNewMacroIsCompleted() {
        configureTemplate("<xf:macro name=\"demo_widget::<caret>\" />")
        val widget = myFixture.findFileInTempDir("src/addons/Demo/Test/_output/templates/public/demo_widget.html")
        val document = PsiDocumentManager.getInstance(project).getDocument(PsiManager.getInstance(project).findFile(widget)!!)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n<xf:macro id=\"fresh\">fresh</xf:macro>") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "card", "fresh")
    }

    fun testNewMacroTypedInCurrentFile() {
        configureTemplate("<xf:macro id=\"fre<caret>sh\" />\n")
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.type("<xf:macro id=\"fresh\">")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("fresh") + 2)
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as XfNameReference
        val target = reference.multiResolve(false).firstOrNull()?.element
        assertInstanceOf(target, XfTagElement::class.java)
        assertEquals("macro", ((target as XfTagElement).header as XfTagHeader).tagName)
    }

    fun testGotoExistingDefinitionAfterUsage() {
        configureTemplate("<xf:macro name=\"te<caret>st\" />\n\n<xf:macro name=\"test\">123</xf:macro>")
        myFixture.performEditorAction("GotoDeclaration")
        assertEquals(myFixture.file.text.lastIndexOf("<xf:macro"), myFixture.caretOffset)
    }

    fun testGotoTypedDefinitionAfterUsage() {
        configureTemplate("<xf:macro name=\"te<caret>st\" />\n\n")
        val usage = myFixture.caretOffset
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.type("<xf:macro name=\"test\">123</xf:macro>")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(usage)
        myFixture.performEditorAction("GotoDeclaration")
        assertEquals(myFixture.file.text.lastIndexOf("<xf:macro"), myFixture.caretOffset)
    }

    fun testGotoTypedIdDefinition() {
        configureTemplate("<xf:macro id=\"te<caret>st\" />\n\n")
        val usage = myFixture.caretOffset
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.type("<xf:macro id=\"test\">123</xf:macro>")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(usage)
        myFixture.performEditorAction("GotoDeclaration")
        assertEquals(myFixture.file.text.lastIndexOf("<xf:macro"), myFixture.caretOffset)
    }

    fun testLocalMacrosAreCompletedFromCurrentFile() {
        configureTemplate("<xf:macro name=\"<caret>\" />\n\n<xf:macro name=\"alpha\" arg-user=\"!\">a</xf:macro>\n<xf:macro id=\"beta\">b</xf:macro>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "alpha", "beta")
        assertDoesntContain(variants, "card")
    }

    fun testUnknownMacroIsWeakWarningOnlyForKnownTemplates() {
        myFixture.enableInspections(XfUnknownMacroInspection::class.java)
        configureTemplate(
            "<xf:macro id=\"local\">local</xf:macro>\n" +
                "<xf:macro id=\"local\" />\n<xf:macro id=\"missing_local\" />\n" +
                "<xf:macro id=\"public:demo_widget::card\" />\n<xf:macro id=\"public:demo_widget::missing_card\" />\n" +
                "<xf:macro template=\"public:demo_widget\" name=\"other_missing\" />\n" +
                "{{ call_macro('public:demo_widget', 'call_missing') }}\n" +
                "<xf:macro id=\"public:not_installed::anything\" />",
            name = "demo_checks.html",
        )
        assertEquals(listOf("missing_local", "missing_card", "other_missing", "call_missing"), unknownMacros())
    }

    fun testMacroAddedByModificationIsNotReported() {
        myFixture.enableInspections(XfUnknownMacroInspection::class.java)
        addOutputFile(
            "template_modifications/public/demo_add_macro.json",
            "{\n    \"template\": \"demo_widget\",\n    \"action\": \"str_replace\",\n    \"find\": \"public card\",\n" +
                "    \"replace\": \"\$0</xf:macro><xf:macro id=\\\"added\\\">added\"\n}",
        )
        configureTemplate("<xf:macro id=\"public:demo_widget::added\" />\n<xf:macro id=\"public:demo_widget::still_missing\" />", name = "demo_mod_checks.html")
        assertEquals(listOf("still_missing"), unknownMacros())
    }

    private fun unknownMacros(): List<String> =
        myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.WEAK_WARNING && it.description?.startsWith("Macro '") == true }
            .sortedBy { it.startOffset }
            .map { it.text }
}
