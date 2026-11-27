package nt.xenforo

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import nt.xenforo.reference.XfNameReference
import nt.xenforo.commands.XfAddOns
import nt.xenforo.documentation.XfDocumentationTarget
import nt.xenforo.documentation.XfDocumentationTargetProvider
import nt.xenforo.phrase.XfPhrases
import nt.xenforo.phrase.XfUnknownPhraseInspection
import nt.xenforo.phrase.XfUnknownPhrasePhpInspection
import nt.xenforo.php.XfPhpNameReference

class XfPhraseToolsTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        addOutputFile("phrases/demo_title.txt", "Demo title")
        addOutputFile("templates/public/demo_page.html", "<xf:macro id=\"row\" arg-item=\"!\"></xf:macro>")
        addOutputFile("templates/admin/demo_list.html", "<div></div>")
        addOutputFile("routes/demo.json", "{\n    \"route_type\": \"public\",\n    \"route_prefix\": \"demo\",\n    \"sub_name\": \"\",\n    \"controller\": \"Demo\\\\Test:Demo\"\n}")
    }

    fun testPhraseInPhpResolvesAndShowsText() {
        configurePhp("\\XF::phrase('demo_ti<caret>tle');")
        assertEquals("demo_title.txt", (resolveAtCaret() as PsiFile).name)
        val target = XfDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single() as XfDocumentationTarget
        assertTrue(target.html(), target.html().contains("Demo title"))
    }

    fun testPhraseCompletionInPhp() {
        configurePhp("\\XF::phraseDeferred('demo_<caret>');")
        val variants = myFixture.completeBasic()?.map { it.lookupString }
        if (variants == null) assertTrue(myFixture.editor.document.text.contains("'demo_title'")) else assertContainsElements(variants, "demo_title")
    }

    fun testViewTemplateDependsOnControllerSection() {
        configurePhp("\$this->view('Demo:View', 'demo_pa<caret>ge', []);", "Demo\\Test\\Pub\\Controller")
        assertEquals("demo_page.html", (resolveAtCaret() as PsiFile).name)
        configurePhp("\$this->view('Demo:View', 'demo_li<caret>st', []);", "Demo\\Test\\Admin\\Controller")
        assertEquals("demo_list.html", (resolveAtCaret() as PsiFile).name)
    }

    fun testRenderMacroAndBuildLink() {
        configurePhp("\$templater->renderMacro('public:demo_page', 'r<caret>ow', []);")
        assertNotNull(resolveAtCaret())
        configurePhp("return XF::app()->templater()->renderTemplate('public:demo_pa<caret>ge', []);")
        assertEquals("demo_page.html", (resolveAtCaret() as PsiFile).name)
        configurePhp("\$this->buildLink('demo/vi<caret>ew', \$item);")
        assertEquals("demo.json", (resolveAtCaret() as PsiFile).name)
        configurePhp("\\XF::app()->router('public')->buildLink('canonical:de<caret>mo');")
        assertEquals("demo.json", (resolveAtCaret() as PsiFile).name)
    }

    fun testUnknownPhraseInTemplateAndPhp() {
        addDataFile("phrases.xml", "<phrases><phrase title=\"save\" addon_id=\"XF\"><![CDATA[Save]]></phrase></phrases>")
        myFixture.enableInspections(XfUnknownPhraseInspection(), XfUnknownPhrasePhpInspection())
        configureTemplate("{{ phrase('save') }} {{ phrase('demo_title') }} {{ phrase('demo_missing') }}")
        val templateWarnings = myFixture.doHighlighting().filter { it.description?.contains("is not found") == true }
        assertEquals(listOf("demo_missing"), templateWarnings.map { it.text })
        configurePhp("\\XF::phrase('demo_title'); \\XF::phrase('demo_absent');")
        val phpWarnings = myFixture.doHighlighting().filter { it.description?.contains("is not found") == true }
        assertEquals(listOf("demo_absent"), phpWarnings.map { it.text })
        assertNotEmpty(myFixture.getAllQuickFixes().filter { it.text == "Create phrase 'demo_absent'…" })
    }

    fun testEmptyResultCachedBeforeIndexingIsNotReported() {
        addDataFile("phrases.xml", "<phrases><phrase title=\"save\" addon_id=\"XF\"><![CDATA[Save]]></phrase></phrases>")
        myFixture.enableInspections(XfUnknownPhraseInspection(), XfUnknownPhrasePhpInspection())
        configureTemplate("{{ phrase('demo_ti<caret>tle') }}")
        val templateReference = myFixture.file.findReferenceAt(myFixture.caretOffset).let { found ->
            (if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)).filterIsInstance<XfNameReference>().single()
        }
        ResolveCache.getInstance(project).resolveWithCaching(templateReference, EMPTY_RESOLVER, false, false)
        assertEmpty(myFixture.doHighlighting().filter { it.description?.contains("is not found") == true })
        assertNotEmpty(templateReference.multiResolve(false).toList())
        configurePhp("\\XF::phrase('demo_ti<caret>tle');")
        val phpReference = myFixture.file.findReferenceAt(myFixture.caretOffset).let { found ->
            (if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)).filterIsInstance<XfPhpNameReference>().single()
        }
        ResolveCache.getInstance(project).resolveWithCaching(phpReference, EMPTY_RESOLVER, false, false)
        assertEmpty(myFixture.doHighlighting().filter { it.description?.contains("is not found") == true })
        assertNotEmpty(phpReference.multiResolve(false).toList())
    }

    fun testUnknownPhraseNeedsCoreData() {
        myFixture.enableInspections(XfUnknownPhraseInspection())
        configureTemplate("{{ phrase('demo_missing') }}")
        assertEmpty(myFixture.doHighlighting().filter { it.description?.contains("is not found") == true })
    }

    fun testExtractPhraseAvailability() {
        configureTemplate("<div class=\"block\">Hello <caret>world</div>")
        assertNotEmpty(myFixture.filterAvailableIntentions("Extract text to phrase"))
        configureTemplate("<div>{{ \$us<caret>er.username }}</div>", "second.html")
        assertEmpty(myFixture.filterAvailableIntentions("Extract text to phrase"))
        configureTemplate("<a title=\"Open <caret>menu\">x</a>", "third.html")
        assertNotEmpty(myFixture.filterAvailableIntentions("Extract text to phrase"))
    }

    fun testSuggestedTitle() {
        assertEquals("nt_profile_save_changes", XfPhrases.suggestTitle("NT/Profile", "Save changes!"))
        assertEquals("nt_profile_sohranit_nastroyki", XfPhrases.suggestTitle("NT/Profile", "Сохранить настройки"))
        assertEquals("phrase", XfPhrases.suggestTitle(null, "!!!"))
    }

    fun testCreateWithoutMakeCommandWritesPhraseFile() {
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        XfPhrases.create(project, addOn, "demo_created", "Created text")
        assertEquals("Created text", VfsUtilCore.loadText(myFixture.findFileInTempDir("src/addons/Demo/Test/_output/phrases/demo_created.txt")))
    }

    private fun configurePhp(statement: String, namespace: String = "Demo\\Test\\Pub\\Controller") {
        val caret = statement.indexOf("<caret>")
        val prefix = "<?php\n\nnamespace $namespace;\n\nclass Demo\n{\n\tpublic function actionIndex()\n\t{\n\t\t"
        val text = prefix + statement.replace("<caret>", "") + "\n\t}\n}\n"
        files++
        val path = "src/addons/Demo/Test/" + namespace.removePrefix("Demo\\Test\\").replace('\\', '/') + "/Demo$files.php"
        val file = myFixture.addFileToProject(path, text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(prefix.length + caret)
    }

    private fun resolveAtCaret(): Any? {
        val found = myFixture.file.findReferenceAt(myFixture.caretOffset)
        val references = if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)
        val reference = references.filterIsInstance<XfPhpNameReference>().single()
        return reference.multiResolve(false).firstOrNull()?.element
    }

    private var files = 0
}

private val EMPTY_RESOLVER = ResolveCache.PolyVariantResolver<PsiPolyVariantReference> { _, _ -> ResolveResult.EMPTY_ARRAY }
