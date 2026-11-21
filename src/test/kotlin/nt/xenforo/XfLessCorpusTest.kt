package nt.xenforo

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.less.inspections.LessResolvedByNameOnlyInspection
import org.jetbrains.plugins.less.inspections.LessUnresolvedMixinInspection
import org.jetbrains.plugins.less.inspections.LessUnresolvedVariableInspection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

class XfLessCorpusTest : BasePlatformTestCase() {
    fun testStylesheetsResolveImplicitTemplates() {
        val root = System.getenv("XF_LESS_CORPUS")?.let(Path::of) ?: return
        val files = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.sorted().toList() }
        val stylesheets = ArrayList<Pair<String, String>>()
        for (path in files) {
            val relative = root.relativize(path).invariantSeparatorsPathString
            val text = StringUtil.convertLineSeparators(path.readText())
            myFixture.addFileToProject(relative, text)
            if (path.extension == "less") stylesheets += relative to text
        }
        myFixture.enableInspections(LessUnresolvedVariableInspection(), LessUnresolvedMixinInspection(), LessResolvedByNameOnlyInspection())
        val problems = mutableListOf<String>()
        for ((relative, text) in stylesheets) {
            myFixture.configureFromTempProjectFile(relative)
            for (info in myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)) {
                val line = StringUtil.offsetToLineNumber(text, info.startOffset) + 1
                problems += "$relative:$line: ${info.severity} ${info.description}"
            }
        }
        System.getenv("XF_LESS_CORPUS_REPORT")?.let { Files.writeString(Path.of(it), problems.joinToString("\n")) }
        assertTrue("${problems.size} problems in ${stylesheets.size} stylesheets:\n" + problems.take(60).joinToString("\n"), problems.isEmpty())
    }
}
