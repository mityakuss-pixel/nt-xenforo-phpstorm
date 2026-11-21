package nt.xenforo

import nt.xenforo.inspection.XfUnknownTagInspection

class XfHighlightingTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(XfUnknownTagInspection())
    }

    fun testValidTemplateHasNoErrors() {
        configureTemplate(
            """
            <xf:title>{{ phrase('threads') }}</xf:title>
            <div class="block" data-xf-init="{{ ${'$'}init ? 'tooltip' : '' }}"{{ widget_data(${'$'}widget) }}>
            	<xf:if is="${'$'}threads is not empty">
            		<a href="{{ link('threads', ${'$'}thread) }}" class="row<xf:if is="${'$'}thread.sticky"> is-sticky</xf:if>">{${'$'}thread.title}</a>
            		<div class="block-row">
            	<xf:else />
            		<div class="block-row block-row--empty">
            	</xf:if>
            		{{ phrase('there_are_no_threads_to_display') }}
            	</div>
            </div>
            <script>
            	var count = {{ ${'$'}threads.count() }};
            	var settings = {'<!--XF:JS:JSON-->'};
            </script>
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testCompilerErrorsAreReported() {
        configureTemplate(
            """
            <div><xf:<error descr="<xf:else /> can only be used directly inside <xf:if> or <xf:foreach>">else</error> /></div>
            <xf:<warning descr="Unknown XenForo tag <xf:fi>">fi</warning> is="${'$'}a" />
            <xf:<error descr="Missing closing tag </xf:foreach>">foreach</error> loop="${'$'}items" value="${'$'}item">
            	<xf:if is="${'$'}a">
            		one
            	<xf:else />
            		two
            	<xf:<error descr="<xf:elseif /> cannot follow <xf:else />">elseif</error> is="${'$'}b" />
            		three
            	</xf:if>
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, false)
    }
}
