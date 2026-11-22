package nt.xenforo

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import nt.xenforo.lang.XfLanguage

class XfEmbeddedBlocksTest : XfTestCase() {
    fun testJsBodyIsJavaScript() {
        configureTemplate("<xf:js>\n\tvar box = 1;\n\tconsole.log(b<caret>ox);\n</xf:js>\n<div>after</div>")
        assertEquals("JavaScript", languageAtCaret())
        val div = PsiTreeUtil.findChildrenOfType(html(), XmlTag::class.java).first { it.name == "div" }
        assertEquals("<div>after</div>", div.text)
    }

    fun testCssBodyIsStyle() {
        configureTemplate("<xf:css>\n.block { co<caret>lor: red; }\n</xf:css>")
        val language = languageAtCaret()
        assertTrue(language, language == "LESS" || language == "CSS")
    }

    fun testSelfClosingTagsStayHtml() {
        configureTemplate("<xf:js src=\"xf/form.js\" />\n<xf:css src=\"public:demo.less\" />\n<div>te<caret>xt</div>")
        assertTrue(languageAtCaret() in setOf("HTML", "XML"))
    }

    fun testUnclosedJsDoesNotSwallowDocument() {
        configureTemplate("<xf:js>\nvar a = 1;\n<div>te<caret>xt</div>")
        assertTrue(languageAtCaret() in setOf("HTML", "XML"))
    }

    fun testTemplateSyntaxInsideScript() {
        configureTemplate("<xf:js>\n\tvar title = '{\$xf.options.boardTitle}';\n\tvar count = {{ \$count }};\n</xf:js>")
        assertEquals(myFixture.file.text, html().text)
        assertEquals(myFixture.file.text, myFixture.file.viewProvider.getPsi(XfLanguage)!!.text)
        assertEmpty(PsiTreeUtil.findChildrenOfType(html(), PsiErrorElement::class.java).map { it.errorDescription })
    }

    fun testJsSourceResolvesToFiles() {
        myFixture.addFileToProject("js/xf/form.js", "")
        myFixture.addFileToProject("js/Demo/Test/vendor/a.min.js", "")
        myFixture.addFileToProject("src/addons/Demo/Test/_files/js/Demo/Test/form.js", "")
        configureTemplate(
            "<xf:js src=\"Demo/Test/form.js\" addon=\"Demo/Test\" min=\"1\" />\n" +
                "<xf:js dev=\"xf/form.js, Demo/Test/vendor/a.min.js\" prod=\"Demo/Test/input-compiled.js\" />",
        )
        val text = myFixture.file.text
        assertTrue(path(resolveAt(text.indexOf("form.js") + 1)).endsWith("/_files/js/Demo/Test/form.js"))
        assertTrue(path(resolveAt(text.indexOf("xf/form.js") + 4)).endsWith("/js/xf/form.js"))
        assertTrue(path(resolveAt(text.indexOf("a.min.js") + 1)).endsWith("/js/Demo/Test/vendor/a.min.js"))
        assertInstanceOf(resolveAt(text.indexOf("vendor/a") + 1), PsiDirectory::class.java)
        assertNull(resolveAt(text.indexOf("input-compiled.js") + 1))
    }

    fun testJsSourceCompletion() {
        myFixture.addFileToProject("js/xf/form.js", "")
        myFixture.addFileToProject("js/xf/tooltip.js", "")
        configureTemplate("<xf:js src=\"xf/<caret>\" />")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "form.js", "tooltip.js")
    }

    private fun html(): PsiFile = myFixture.file.viewProvider.getPsi(HTMLLanguage.INSTANCE)!!

    private fun languageAtCaret(): String {
        val offset = myFixture.caretOffset
        InjectedLanguageManager.getInstance(project).findInjectedElementAt(myFixture.file, offset)?.let { return it.language.id }
        val provider = myFixture.file.viewProvider
        val element = provider.languages.filter { it !== XfLanguage }.firstNotNullOf { provider.findElementAt(offset, it) }
        return element.language.id
    }

    private fun resolveAt(offset: Int): PsiElement? {
        val reference = myFixture.file.findReferenceAt(offset) ?: return null
        return if (reference is PsiPolyVariantReference) reference.multiResolve(false).firstOrNull()?.element else reference.resolve()
    }

    private fun path(element: PsiElement?): String = (element as PsiFile).virtualFile.path
}
