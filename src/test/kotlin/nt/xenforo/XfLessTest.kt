package nt.xenforo

import com.intellij.psi.PsiElement
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.lang.XfLanguage
import nt.xenforo.less.XfLessGotoDeclarationHandler
import org.jetbrains.plugins.less.psi.LESSMixin
import org.jetbrains.plugins.less.psi.LESSVariableDeclaration
import org.jetbrains.plugins.less.inspections.LessResolvedByNameOnlyInspection
import org.jetbrains.plugins.less.inspections.LessUnresolvedMixinInspection
import org.jetbrains.plugins.less.inspections.LessUnresolvedVariableInspection

class XfLessTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LessUnresolvedVariableInspection(), LessUnresolvedMixinInspection(), LessResolvedByNameOnlyInspection())
    }

    fun testLessTemplateIsXenForoTemplate() {
        val file = configureTemplate("<xf:if is=\"\$xf.visitor.user_id\">\n.a { color: red; }\n</xf:if>", name = "demo_styles.less")
        assertEquals(XfLanguage, file.viewProvider.baseLanguage)
        assertEquals("LESS", file.viewProvider.languages.first { it != XfLanguage }.id)
    }

    fun testTemplateSyntaxInLessHasNoErrors() {
        configureTemplate(
            """
            <xf:foreach loop="${'$'}styles" key="${'$'}userId" value="${'$'}style">
            	<xf:if is="${'$'}style.username_css">
            		.username .demo-username-{${'$'}userId}
            		{
            			{${'$'}style.username_css|raw}
            		}
            	</xf:if>
            </xf:foreach>
            """.trimIndent(),
            name = "demo_custom_styles.less",
        )
        myFixture.checkHighlighting()
    }

    fun testImplicitAndIncludedTemplatesAreKnown() {
        addDataFile("style_properties.xml", "<style_properties>\n  <style_property property_name=\"textColor\" group_name=\"basic\" title=\"Text\"/>\n</style_properties>")
        addDataFile("templates.xml", TEMPLATES)
        addOutputFile("templates/public/demo_prepend.less", "@demo-gap: 4px;\n")
        addOutputFile("templates/public/demo_mixins.less", ".m-demoCard()\n{\n\tpadding: 0;\n}\n")
        configureTemplate(
            """
            {{ include('demo_mixins.less') }}
            .demo-block
            {
            	color: @xf-textColor;
            	width: @avatar-s;
            	font-weight: @faWeight-solid;
            	margin: @demo-gap;
            	.m-clearFix();
            	.m-demoCard();
            	height: <warning descr="Cannot find variable 'demo-missing'">@demo-missing</warning>;
            }
            """.trimIndent(),
            name = "demo_block.less",
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testMixinParametersAndLocalsAreKnown() {
        configureTemplate(
            """
            .m-demoBite(@cut; @y: 2px)
            {
            	mask: radial-gradient(circle @cut at 50% ~"calc(100% - @{y})", transparent ~"calc(@{cut} - 1px)", #000 @cut);
            }
            .demo-block
            {
            	@gap: 4px;
            	margin: @gap;
            	.m-demoBite(1px);
            	padding: <warning descr="Cannot find variable 'cut'">@cut</warning>;
            	<warning descr="Cannot find mixin 'm-demoMissing'">.m-demoMissing()</warning>;
            }
            """.trimIndent(),
            name = "demo_bite.less",
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testJumpToIncludedAndImplicitDeclarations() {
        addDataFile("style_properties.xml", "<style_properties>\n  <style_property property_name=\"textColor\" group_name=\"basic\" title=\"Text\"/>\n</style_properties>")
        addDataFile("templates.xml", TEMPLATES)
        addOutputFile("templates/public/demo_mixins.less", ".m-demoCard()\n{\n\tpadding: 0;\n}\n@demo-radius: 3px;\n")
        configureTemplate(
            """
            {{ include('demo_mixins.less') }}
            .demo-block
            {
            	@gap: 4px;
            	color: @xf-textColor;
            	width: @avatar-s;
            	border-radius: @demo-radius;
            	margin: @gap;
            	.m-demoCard();
            }
            """.trimIndent(),
            name = "demo_jump.less",
        )
        assertEquals("@avatar-s", jump("@avatar-s").single().let { it as XfSymbolTarget }.name)
        assertEquals("textColor", jump("@xf-textColor").single().let { it as XfSymbolTarget }.name)
        assertEquals("demo-radius", (jump("@demo-radius").single() as LESSVariableDeclaration).name)
        assertEquals("demo_mixins.less", jump("@demo-radius").single().containingFile.name)
        assertEquals("m-demoCard", (jump(".m-demoCard").single() as LESSMixin).name)
        assertEquals("gap", (jump("@gap;").single() as LESSVariableDeclaration).name)
    }

    private fun jump(text: String): List<PsiElement> {
        val offset = myFixture.file.text.lastIndexOf(text) + 2
        val source = myFixture.file.findElementAt(offset)
        return XfLessGotoDeclarationHandler().getGotoDeclarationTargets(source, offset, myFixture.editor).orEmpty().toList()
    }

    fun testCssTemplateIsXenForoTemplate() {
        val file = configureTemplate("<xf:if is=\"\$x\">.a { color: red; }</xf:if>", name = "demo_plain.css")
        assertEquals("CSS", file.viewProvider.languages.first { it != XfLanguage }.id)
        myFixture.checkHighlighting()
    }

    private companion object {
        val TEMPLATES = """
            <templates>
              <template type="public" title="setup.less" version_id="1"><![CDATA[@avatar-s: 48px;
            .m-clearFix()
            {
            	clear: both;
            }
            {{ include('setup_fa.less') }}]]></template>
              <template type="public" title="setup_fa.less" version_id="1"><![CDATA[@faWeight-solid: 900;]]></template>
            </templates>
        """.trimIndent()
    }
}
