package nt.xenforo.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import nt.xenforo.XfBundle
import nt.xenforo.meta.XfTags
import nt.xenforo.psi.XfTypes

object XfParserUtil : GeneratedParserUtilBase() {
    private val BRANCH_NAMES = setOf("else", "elseif")

    @JvmStatic
    fun errorToken(builder: PsiBuilder, level: Int): Boolean {
        if (builder.eof()) return false
        val marker = builder.mark()
        builder.advanceLexer()
        marker.error(XfBundle.message("parser.unexpected.token"))
        return true
    }

    @JvmStatic
    fun afterBlockOpen(builder: PsiBuilder, level: Int): Boolean {
        var step = -1
        while (true) {
            val type = builder.rawLookup(step) ?: return false
            if (isTrivia(type)) {
                step--
                continue
            }
            return type == XfTypes.TAG_END
        }
    }

    @JvmStatic
    fun branchTagAhead(builder: PsiBuilder, level: Int): Boolean {
        if (builder.tokenType != XfTypes.TAG_OPEN || builder.rawLookup(1) != XfTypes.TAG_NAME) return false
        if (rawText(builder, 1).lowercase() !in BRANCH_NAMES) return false
        var step = 2
        while (true) {
            when (builder.rawLookup(step)) {
                null, XfTypes.TAG_END, XfTypes.TAG_OPEN, XfTypes.TAG_CLOSE_OPEN, XfTypes.CONTENT -> return false
                XfTypes.TAG_SELF_CLOSE -> return true
                else -> step++
            }
        }
    }

    @JvmStatic
    fun expressionAttributeAhead(builder: PsiBuilder, level: Int): Boolean {
        var step = -1
        var attributeName: String? = null
        while (true) {
            val type = builder.rawLookup(step) ?: return false
            when {
                isTrivia(type) || type == XfTypes.ATTR_QUOTE || type == XfTypes.ATTR_EQ -> step--
                type == XfTypes.ATTR_NAME && attributeName == null -> {
                    attributeName = rawText(builder, step)
                    step--
                }
                type == XfTypes.TAG_NAME -> return XfTags.isExpressionAttribute(rawText(builder, step), attributeName ?: return false)
                type == XfTypes.TAG_OPEN || type == XfTypes.TAG_CLOSE_OPEN -> return false
                else -> step--
            }
        }
    }

    private fun isTrivia(type: IElementType): Boolean = type == TokenType.WHITE_SPACE || type == XfTypes.COMMENT

    private fun rawText(builder: PsiBuilder, step: Int): String {
        val start = builder.rawTokenTypeStart(step)
        val end = builder.rawTokenTypeStart(step + 1)
        return builder.originalText.subSequence(start, end).toString()
    }
}
