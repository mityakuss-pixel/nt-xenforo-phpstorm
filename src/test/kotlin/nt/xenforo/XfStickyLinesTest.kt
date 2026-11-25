package nt.xenforo

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.lang.html.HTMLLanguage
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil
import nt.xenforo.lang.XfLanguage
import nt.xenforo.structure.XfBreadcrumbsProvider

class XfStickyLinesTest : XfTestCase() {
    fun testStickyLinesMergeTemplateAndHtmlTags() {
        val text = "<div class=\"a\">\n\t<xf:if is=\"\$x\">\n\t\t<div class=\"b\">\n\t\t\t<xf:foreach loop=\"\$items\" value=\"\$item\">\n" +
            "\t\t\t\t<span>X</span>\n\t\t\t</xf:foreach>\n\t\t</div>\n\t</xf:if>\n\t<xf:comment>\n\t\tnote\n\t</xf:comment>\n</div>\n"
        configureTemplate(text)
        val file = myFixture.file.virtualFile
        val document = myFixture.editor.document
        assertInstanceOf(BreadcrumbsUtil.getInfoProvider(XfLanguage), XfBreadcrumbsProvider::class.java)
        val collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, file)
        val atSpan = collector.computeStickyLineInfos(file, document, text.indexOf("X</span>"))
        assertEquals(listOf(0, 1, 2, 3, 4), atSpan.map { document.getLineNumber(it.textOffset) }.sorted())
        val allLines = (0 until document.lineCount).flatMap { collector.computeStickyLineInfos(file, document, document.getLineEndOffset(it)) }
        assertEquals(listOf(0, 1, 2, 3), allLines.map { document.getLineNumber(it.textOffset) }.distinct().sorted())
        val crumbs = collector.computeCrumbs(file, document, text.indexOf("X</span>"), true).map { it.text }
        assertEquals(listOf("div.a", "xf:if", "div.b", "xf:foreach", "span"), crumbs)
    }

    fun testPlainHtmlKeepsDefaultBreadcrumbs() {
        val text = "<div class=\"a\">\n\t<p>\n\t\t<span>X</span>\n\t</p>\n</div>\n"
        val file = myFixture.addFileToProject("web/page.html", text).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        assertEquals(HTMLLanguage.INSTANCE, myFixture.file.language)
        val collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, file)
        val document = myFixture.editor.document
        assertEquals(listOf("div.a", "p", "span"), collector.computeCrumbs(file, document, text.indexOf("X</span>"), true).map { it.text })
        assertEquals(listOf(0, 1, 2), collector.computeStickyLineInfos(file, document, text.indexOf("X</span>")).map { document.getLineNumber(it.textOffset) }.sorted())
    }
}
