package nt.xenforo

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.jetbrains.php.lang.psi.elements.Method
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.psi.XfTagElement

class XfTemplateExtrasTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        addOutputFile("widget_positions/demo_sidebar.json", "{\n    \"active\": true\n}")
        addOutputFile("permissions/forum-demoFeature.json", "{\n    \"permission_type\": \"flag\"\n}")
        addOutputFile("admin_permissions/demoAdmin.json", "{\n    \"display_order\": 10\n}")
        addDataFile("permissions.xml", "<permissions>\n  <permission permission_group_id=\"general\" permission_id=\"view\" permission_type=\"flag\"/>\n</permissions>")
        addOutputFile("templates/public/demo_macros.html", "<xf:macro id=\"base\" arg-item=\"!\">x</xf:macro>")
        addOutputFile("templates/public/demo_base.html", "<div>\n<xf:extension id=\"content\">x</xf:extension>\n</div>")
        myFixture.addFileToProject("src/addons/Demo/Test/Callback.php", "<?php\nnamespace Demo\\Test;\nclass Callback\n{\n\tpublic static function getStats() {}\n}\n")
    }

    fun testWidgetPosition() {
        configureTemplate("<xf:widgetpos id=\"demo_si<caret>debar\" position=\"sidebar\" />")
        assertEquals("demo_sidebar.json", (resolve() as PsiFile).name)
        configureTemplate("<xf:widgetpos id=\"<caret>\" />", "second.html")
        assertContainsElements(myFixture.completeBasic()?.map { it.lookupString }.orEmpty(), "demo_sidebar")
    }

    fun testMacroExtends() {
        configureTemplate("<xf:macro id=\"child\" extends=\"demo_macros::ba<caret>se\">y</xf:macro>")
        assertEquals("macro", (resolve() as XfTagElement).tagName)
    }

    fun testExtensionInParentAndSameFile() {
        configureTemplate("<xf:extends template=\"demo_base\" />\n<xf:extension id=\"con<caret>tent\">y</xf:extension>")
        val target = resolve() as XfSymbolTarget
        assertEquals("demo_base.html", target.containingFileName())
        configureTemplate("<xf:extension id=\"head\">a</xf:extension>\n<xf:extensionvalue id=\"he<caret>ad\" />", "second.html")
        assertEquals(0, (resolve() as XfSymbolTarget).textOffset)
    }

    fun testCallbackMethod() {
        configureTemplate("<xf:callback class=\"Demo\\Test\\Callback\" method=\"getSt<caret>ats\"></xf:callback>")
        assertEquals("getStats", (resolve() as Method).name)
    }

    fun testPermissionsInTemplateAndPhp() {
        configureTemplate("{{ \$xf.visitor.hasPermission('forum', 'demoFe<caret>ature') ? 1 : 0 }}")
        assertEquals("forum-demoFeature.json", (resolve() as PsiFile).name)
        configureTemplate("{{ \$xf.visitor.hasPermission('general', 'vi<caret>ew') ? 1 : 0 }}", "second.html")
        assertNotNull(resolve())
        val file = myFixture.addFileToProject(
            "src/addons/Demo/Test/Check.php",
            "<?php\n\\XF::visitor()->hasNodePermission(\$id, 'demoFeature');\n\\XF::visitor()->hasAdminPermission('demoAdmin');\n",
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("demoFeature") + 2)
        assertEquals("forum-demoFeature.json", (resolve() as PsiFile).name)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("demoAdmin") + 2)
        assertEquals("demoAdmin.json", (resolve() as PsiFile).name)
    }

    fun testFarTargetOpensAtLineStart() {
        val head = "  <template type=\"public\" title=\"wide_macros\" version_id=\"1\" version_string=\"1.0.0 Release Candidate 1 with a long tail\"><![CDATA["
        addDataFile("templates.xml", "<templates>\n$head<xf:macro id=\"row\">x</xf:macro>]]></template>\n</templates>")
        configureTemplate("<xf:macro id=\"wide_macros::r<caret>ow\" />", "third.html")
        val target = resolve() as XfSymbolTarget
        assertEquals("<templates>\n".length + 2, target.visibleOffset())
        assertTrue(target.textOffset > target.visibleOffset() + 80)
    }

    private fun XfSymbolTarget.containingFileName(): String = (navigationElement as? XfSymbolTarget)?.locationString?.substringAfterLast('/') ?: locationString.substringAfterLast('/')

    private fun resolve(): PsiElement? {
        val found = myFixture.file.findReferenceAt(myFixture.caretOffset)
        val references = if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)
        for (reference in references) {
            val target = if (reference is PsiPolyVariantReference) reference.multiResolve(false).firstOrNull()?.element else reference.resolve()
            if (target != null) return target
        }
        return null
    }
}
