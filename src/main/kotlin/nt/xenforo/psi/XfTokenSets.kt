package nt.xenforo.psi

import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet
import nt.xenforo.lang.XfLanguage

object XfTokenSets {
    @JvmField
    val FILE: IFileElementType = IFileElementType("XF_FILE", XfLanguage)

    @JvmField
    val OUTER: OuterLanguageElementType = OuterLanguageElementType("XF_FRAGMENT", XfLanguage)

    @JvmField
    val COMMENTS: TokenSet = TokenSet.create(XfTypes.COMMENT)

    @JvmField
    val STRING_LITERALS: TokenSet = TokenSet.create(XfTypes.STRING, XfTypes.STRING_TEXT)

    @JvmField
    val TAG_PARTS: TokenSet = TokenSet.create(
        XfTypes.TAG_OPEN, XfTypes.TAG_CLOSE_OPEN, XfTypes.TAG_NAME, XfTypes.TAG_END, XfTypes.TAG_SELF_CLOSE,
    )

    @JvmField
    val DELIMITERS: TokenSet = TokenSet.create(XfTypes.EXPR_START, XfTypes.EXPR_END, XfTypes.VAR_START, XfTypes.VAR_END)

    @JvmField
    val KEYWORDS: TokenSet = TokenSet.create(
        XfTypes.TRUE, XfTypes.FALSE, XfTypes.NULL, XfTypes.AND, XfTypes.OR, XfTypes.IS, XfTypes.IS_NOT, XfTypes.INSTANCEOF,
    )

    @JvmField
    val OPERATORS: TokenSet = TokenSet.create(
        XfTypes.PLUS, XfTypes.MINUS, XfTypes.STAR, XfTypes.SLASH, XfTypes.PERCENT, XfTypes.CONCAT, XfTypes.EQEQ,
        XfTypes.NOT_EQ, XfTypes.IDENTICAL, XfTypes.NOT_IDENTICAL, XfTypes.GT, XfTypes.GE, XfTypes.LT, XfTypes.LE,
        XfTypes.NOT, XfTypes.QUESTION, XfTypes.ELVIS, XfTypes.COALESCE, XfTypes.COLON, XfTypes.PIPE, XfTypes.ARROW,
    )

    @JvmField
    val TEMPLATE_EDGES: TokenSet = TokenSet.create(
        XfTypes.TAG_OPEN, XfTypes.TAG_CLOSE_OPEN, XfTypes.TAG_END, XfTypes.TAG_SELF_CLOSE,
        XfTypes.EXPR_START, XfTypes.EXPR_END, XfTypes.VAR_START, XfTypes.VAR_END, XfTypes.COMMENT,
    )

    @JvmField
    val INSERTION_STARTS: TokenSet = TokenSet.create(XfTypes.EXPR_START, XfTypes.VAR_START)
}
