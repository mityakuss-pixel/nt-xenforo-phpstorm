package nt.xenforo.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import nt.xenforo.XfBundle
import nt.xenforo.lang.XfIcons
import javax.swing.Icon

private val ATTRIBUTE_DESCRIPTORS: List<Pair<String, TextAttributesKey>> = listOf(
    "color.tag" to XfHighlighterColors.TAG,
    "color.attribute" to XfHighlighterColors.ATTRIBUTE,
    "color.attribute.value" to XfHighlighterColors.ATTRIBUTE_VALUE,
    "color.delimiter" to XfHighlighterColors.DELIMITER,
    "color.variable" to XfHighlighterColors.VARIABLE,
    "color.property" to XfHighlighterColors.PROPERTY,
    "color.method" to XfHighlighterColors.METHOD,
    "color.function" to XfHighlighterColors.FUNCTION,
    "color.filter" to XfHighlighterColors.FILTER,
    "color.keyword" to XfHighlighterColors.KEYWORD,
    "color.string" to XfHighlighterColors.STRING,
    "color.number" to XfHighlighterColors.NUMBER,
    "color.operator" to XfHighlighterColors.OPERATOR,
    "color.parentheses" to XfHighlighterColors.PARENTHESES,
    "color.brackets" to XfHighlighterColors.BRACKETS,
    "color.braces" to XfHighlighterColors.BRACES,
    "color.comma" to XfHighlighterColors.COMMA,
    "color.dot" to XfHighlighterColors.DOT,
    "color.comment" to XfHighlighterColors.COMMENT,
    "color.bad.character" to XfHighlighterColors.BAD_CHARACTER,
)

private val DEMO_TEXT = """<xf:comment>Thread list</xf:comment>
<xf:title>{{ <fn>phrase</fn>('threads') }}</xf:title>
<xf:css src="thread_list.less" />

<xf:if is="${'$'}xf.visitor.<method>canPostThread</method>() && ${'$'}threads is not empty">
	<xf:foreach loop="${'$'}threads" key="${'$'}id" value="${'$'}thread">
		<div class="block-row {{ ${'$'}thread.sticky ? 'is-sticky' : '' }}">
			<a href="{{ <fn>link</fn>('threads', ${'$'}thread) }}">{${'$'}thread.<prop>title</prop>|<filter>for_attr</filter>}</a>
			<xf:username user="${'$'}thread.User" rich="true" />
			<xf:date time="${'$'}thread.post_date" />
		</div>
	</xf:foreach>
<xf:else />
	<div class="blockMessage">{{ <fn>phrase</fn>('there_are_no_threads_to_display') }}</div>
</xf:if>

<xf:macro id="thread_counter" arg-count="{{ 0 }}">
	<span>{{ <fn>number</fn>(${'$'}count) }}</span>
</xf:macro>
"""

class XfColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = XfBundle.message("settings.display.name")

    override fun getIcon(): Icon = XfIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = XfSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> =
        ATTRIBUTE_DESCRIPTORS.map { (key, attributes) -> AttributesDescriptor(XfBundle.message(key), attributes) }.toTypedArray()

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "fn" to XfHighlighterColors.FUNCTION,
        "filter" to XfHighlighterColors.FILTER,
        "prop" to XfHighlighterColors.PROPERTY,
        "method" to XfHighlighterColors.METHOD,
    )

    override fun getDemoText(): String = DEMO_TEXT
}
