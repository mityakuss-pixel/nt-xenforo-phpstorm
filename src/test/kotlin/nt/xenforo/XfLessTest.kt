package nt.xenforo

import nt.xenforo.lang.XfLanguage
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
