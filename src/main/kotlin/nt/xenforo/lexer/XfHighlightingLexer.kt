package nt.xenforo.lexer

import com.intellij.lexer.Lexer
import com.intellij.lexer.LookAheadLexer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import nt.xenforo.lang.XfLanguage
import nt.xenforo.psi.XfTypes

private const val COMMENT_OPEN = "<xf:comment>"
private const val COMMENT_CLOSE = "</xf:comment>"

object XfHighlightingTokens {
    @JvmField
    val COMMENT_TAG: IElementType = IElementType("XF_COMMENT_TAG", XfLanguage)
}

class XfHighlightingLexer : LookAheadLexer(XfLexerAdapter()) {
    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType != XfTypes.COMMENT) {
            super.lookAhead(baseLexer)
            return
        }
        val text = baseLexer.bufferSequence
        val start = baseLexer.tokenStart
        val end = baseLexer.tokenEnd
        val token = text.subSequence(start, end)
        val bodyStart = if (StringUtil.startsWithIgnoreCase(token.toString(), COMMENT_OPEN)) start + COMMENT_OPEN.length else start
        val closed = end - COMMENT_CLOSE.length >= bodyStart && StringUtil.endsWithIgnoreCase(token, COMMENT_CLOSE)
        val bodyEnd = if (closed) end - COMMENT_CLOSE.length else end
        if (bodyStart > start) addToken(bodyStart, XfHighlightingTokens.COMMENT_TAG)
        if (bodyEnd > bodyStart) addToken(bodyEnd, XfTypes.COMMENT)
        if (end > bodyEnd) addToken(end, XfHighlightingTokens.COMMENT_TAG)
        baseLexer.advance()
    }
}
