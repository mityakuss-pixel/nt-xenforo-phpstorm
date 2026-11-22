package nt.xenforo.highlight

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import nt.xenforo.lexer.XfHighlightingLexer
import nt.xenforo.lexer.XfHighlightingTokens
import nt.xenforo.psi.XfTokenSets
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.psi.XfTypes

object XfHighlighterColors {
    @JvmField val TAG: TextAttributesKey = createTextAttributesKey("XF_TAG", DefaultLanguageHighlighterColors.KEYWORD)
    @JvmField val ATTRIBUTE: TextAttributesKey = createTextAttributesKey("XF_ATTRIBUTE", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE)
    @JvmField val ATTRIBUTE_VALUE: TextAttributesKey = createTextAttributesKey("XF_ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.STRING)
    @JvmField val DELIMITER: TextAttributesKey = createTextAttributesKey("XF_DELIMITER", DefaultLanguageHighlighterColors.KEYWORD)
    @JvmField val VARIABLE: TextAttributesKey = createTextAttributesKey("XF_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    @JvmField val PROPERTY: TextAttributesKey = createTextAttributesKey("XF_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    @JvmField val METHOD: TextAttributesKey = createTextAttributesKey("XF_METHOD", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
    @JvmField val FUNCTION: TextAttributesKey = createTextAttributesKey("XF_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    @JvmField val FILTER: TextAttributesKey = createTextAttributesKey("XF_FILTER", DefaultLanguageHighlighterColors.STATIC_METHOD)
    @JvmField val KEYWORD: TextAttributesKey = createTextAttributesKey("XF_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    @JvmField val STRING: TextAttributesKey = createTextAttributesKey("XF_STRING", DefaultLanguageHighlighterColors.STRING)
    @JvmField val NUMBER: TextAttributesKey = createTextAttributesKey("XF_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    @JvmField val OPERATOR: TextAttributesKey = createTextAttributesKey("XF_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    @JvmField val PARENTHESES: TextAttributesKey = createTextAttributesKey("XF_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
    @JvmField val BRACKETS: TextAttributesKey = createTextAttributesKey("XF_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    @JvmField val BRACES: TextAttributesKey = createTextAttributesKey("XF_BRACES", DefaultLanguageHighlighterColors.BRACES)
    @JvmField val COMMA: TextAttributesKey = createTextAttributesKey("XF_COMMA", DefaultLanguageHighlighterColors.COMMA)
    @JvmField val DOT: TextAttributesKey = createTextAttributesKey("XF_DOT", DefaultLanguageHighlighterColors.DOT)
    @JvmField val COMMENT: TextAttributesKey = createTextAttributesKey("XF_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    @JvmField val BAD_CHARACTER: TextAttributesKey = createTextAttributesKey("XF_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}

private val TOKEN_KEYS: Map<IElementType, TextAttributesKey> by lazy {
    buildMap {
        for (type in XfTokenSets.TAG_PARTS.types) put(type, XfHighlighterColors.TAG)
        for (type in XfTokenSets.DELIMITERS.types) put(type, XfHighlighterColors.DELIMITER)
        for (type in XfTokenSets.KEYWORDS.types) put(type, XfHighlighterColors.KEYWORD)
        for (type in XfTokenSets.OPERATORS.types) put(type, XfHighlighterColors.OPERATOR)
        put(XfTypes.ATTR_NAME, XfHighlighterColors.ATTRIBUTE)
        put(XfTypes.ATTR_EQ, XfHighlighterColors.ATTRIBUTE)
        put(XfTypes.ATTR_QUOTE, XfHighlighterColors.ATTRIBUTE_VALUE)
        put(XfTypes.ATTR_TEXT, XfHighlighterColors.ATTRIBUTE_VALUE)
        put(XfTypes.VARIABLE, XfHighlighterColors.VARIABLE)
        put(XfTypes.IDENTIFIER, XfHighlighterColors.PROPERTY)
        put(XfTypes.DOT, XfHighlighterColors.DOT)
        put(XfTypes.COMMA, XfHighlighterColors.COMMA)
        put(XfTypes.LPAREN, XfHighlighterColors.PARENTHESES)
        put(XfTypes.RPAREN, XfHighlighterColors.PARENTHESES)
        put(XfTypes.LBRACKET, XfHighlighterColors.BRACKETS)
        put(XfTypes.RBRACKET, XfHighlighterColors.BRACKETS)
        put(XfTypes.LBRACE, XfHighlighterColors.BRACES)
        put(XfTypes.RBRACE, XfHighlighterColors.BRACES)
        put(XfTypes.STRING, XfHighlighterColors.STRING)
        put(XfTypes.DQ, XfHighlighterColors.STRING)
        put(XfTypes.STRING_TEXT, XfHighlighterColors.STRING)
        put(XfTypes.NUMBER, XfHighlighterColors.NUMBER)
        put(XfTypes.COMMENT, XfHighlighterColors.COMMENT)
        put(XfHighlightingTokens.COMMENT_TAG, XfHighlighterColors.COMMENT)
        put(TokenType.BAD_CHARACTER, XfHighlighterColors.BAD_CHARACTER)
    }
}

class XfSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = XfHighlightingLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(TOKEN_KEYS[tokenType])
}

class XfSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = XfSyntaxHighlighter()
}

class XfEditorHighlighter(project: Project?, file: VirtualFile?, colors: EditorColorsScheme) :
    LayeredLexerEditorHighlighter(XfSyntaxHighlighter(), colors) {
    init {
        val language = file?.let(XfTemplateFiles::dataLanguage)
        val data = language?.let { SyntaxHighlighterFactory.getSyntaxHighlighter(it, project, file) }
            ?: SyntaxHighlighterFactory.getSyntaxHighlighter(HtmlFileType.INSTANCE, project, file)
            ?: PlainSyntaxHighlighter()
        registerLayer(XfTypes.CONTENT, LayerDescriptor(data, ""))
    }
}

class XfEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter = XfEditorHighlighter(project, virtualFile, colors)
}
