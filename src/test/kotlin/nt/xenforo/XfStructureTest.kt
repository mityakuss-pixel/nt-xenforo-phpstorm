package nt.xenforo

import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.util.Disposer
import nt.xenforo.structure.XfStructureViewFactory

class XfStructureTest : XfTestCase() {
    fun testTagsAndMacrosAreShown() {
        configureTemplate(
            """
            <xf:macro id="thread_meta" arg-thread="!">
            	<xf:if is="${'$'}thread.sticky">
            		<span>{{ phrase('sticky') }}</span>
            	<xf:else />
            		<xf:include template="demo_badge" />
            	</xf:if>
            </xf:macro>
            <div class="block">
            	<xf:foreach loop="${'$'}threads" value="${'$'}thread">
            		<xf:macro id="thread_meta" arg-thread="{${'$'}thread}" />
            	</xf:foreach>
            </div>
            """.trimIndent(),
        )
        val expected = """
            xf:macro#thread_meta
              xf:if [${'$'}thread.sticky]
                xf:else
                xf:include [demo_badge]
            xf:foreach [${'$'}threads]
              xf:macro [thread_meta]
            span
            div.block
        """.trimIndent()
        assertEquals(expected, structure())
    }

    private fun structure(): String {
        val builder = XfStructureViewFactory().getStructureViewBuilder(myFixture.file) as TreeBasedStructureViewBuilder
        val model = builder.createStructureViewModel(myFixture.editor)
        try {
            val lines = ArrayList<String>()
            for (child in model.root.children) render(child, 0, lines)
            return lines.joinToString("\n")
        } finally {
            Disposer.dispose(model)
        }
    }

    private fun render(element: TreeElement, depth: Int, lines: MutableList<String>) {
        val presentation = element.presentation
        val location = presentation.locationString?.let { " [$it]" }.orEmpty()
        lines += "  ".repeat(depth) + presentation.presentableText + location
        if (presentation.presentableText?.startsWith("xf:") == true) {
            for (child in element.children) render(child, depth + 1, lines)
        }
    }
}
