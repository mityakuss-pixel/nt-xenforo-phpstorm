package nt.xenforo.lexer

import com.intellij.lexer.FlexAdapter

class XfLexerAdapter : FlexAdapter(_XfLexer()) {
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        (flex as _XfLexer).resetContext()
        super.start(buffer, startOffset, endOffset, initialState)
    }
}
