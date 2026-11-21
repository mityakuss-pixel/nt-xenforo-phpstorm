package nt.xenforo

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

class XfCorpusTest : BasePlatformTestCase() {
    fun testTemplatesHighlightWithoutErrors() {
        val root = System.getenv("XF_TEMPLATE_CORPUS")?.let(Path::of) ?: return
        val templates = Files.walk(root).use { paths -> paths.filter { it.extension in EXTENSIONS }.sorted().toList() }
        val problems = mutableListOf<String>()
        for (path in templates) {
            val type = path.parent.name
            val text = StringUtil.convertLineSeparators(path.readText())
            val file = myFixture.addFileToProject("src/addons/Corpus/_output/templates/$type/${path.name}", text)
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            for (info in myFixture.doHighlighting(HighlightSeverity.ERROR)) {
                val line = StringUtil.offsetToLineNumber(text, info.startOffset) + 1
                val fragment = text.substring(info.startOffset, minOf(info.endOffset, info.startOffset + 60)).replace('\n', ' ')
                if ("$type/${path.name}:$line" in UPSTREAM_SYNTAX_ERRORS) continue
                problems += "$type/${path.name}:$line: ${info.description} [$fragment]"
            }
        }
        System.getenv("XF_TEMPLATE_CORPUS_REPORT")?.let { Files.writeString(Path.of(it), problems.joinToString("\n")) }
        assertTrue("${problems.size} problems in ${templates.size} templates:\n" + problems.take(50).joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        val EXTENSIONS = setOf("html", "less", "css")
        val UPSTREAM_SYNTAX_ERRORS = setOf("public/core_filter.less:48")
    }
}
