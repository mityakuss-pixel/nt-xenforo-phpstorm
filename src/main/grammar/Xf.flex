package nt.xenforo.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayDeque;
import nt.xenforo.meta.XfTags;

import static nt.xenforo.psi.XfTypes.*;

%%

%class _XfLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
  private final ArrayDeque<Integer> stateStack = new ArrayDeque<>();
  private String tagName = "";
  private String attributeName = "";
  private boolean valueExpected;
  private boolean variableNameExpected;
  private boolean plainBlockOpening;

  public _XfLexer() {
    this((java.io.Reader) null);
  }

  public void resetContext() {
    stateStack.clear();
    tagName = "";
    attributeName = "";
    valueExpected = false;
    variableNameExpected = false;
    plainBlockOpening = false;
  }

  private void pushState(int state) {
    stateStack.push(yystate());
    yybegin(state);
  }

  private void popState() {
    Integer previous = stateStack.poll();
    yybegin(previous == null ? YYINITIAL : previous);
  }

  private void resetToInitial() {
    stateStack.clear();
    yybegin(YYINITIAL);
  }

  private static boolean isContextState(int state) {
    return state == YYINITIAL || state == IN_TAG || state == IN_RAW || state == IN_ATTR_STRING
      || state == IN_ATTR_EXPR || state == IN_DQ || state == IN_EXPR || state == IN_VAR;
  }

  private int contextRoot() {
    if (isContextState(yystate())) return yystate();
    for (int state : stateStack) {
      if (isContextState(state)) return state;
    }
    return YYINITIAL;
  }

  private boolean insideAttribute() {
    if (yystate() == IN_ATTR_EXPR || yystate() == IN_ATTR_STRING) return true;
    for (int state : stateStack) {
      if (state == IN_ATTR_EXPR || state == IN_ATTR_STRING) return true;
    }
    return false;
  }

  private boolean closesOuter(char c) {
    for (int state : stateStack) {
      if (c == ')' && state == IN_PAREN) return true;
      if (c == ']' && state == IN_ARRAY) return true;
      if (c == '}' && (state == IN_HASH || state == IN_EXPR || state == IN_VAR)) return true;
      if (state == IN_ATTR_EXPR || state == IN_ATTR_STRING || state == IN_DQ || state == IN_TAG) return false;
    }
    return false;
  }

  private IElementType closeAttributeExpression() {
    while (yystate() != IN_ATTR_EXPR && !stateStack.isEmpty()) popState();
    popState();
    return ATTR_QUOTE;
  }

  private IElementType startTag(IElementType type) {
    pushState(IN_TAG_NAME);
    tagName = "";
    attributeName = "";
    valueExpected = false;
    return type;
  }

  private IElementType startVariable() {
    pushState(IN_VAR);
    variableNameExpected = true;
    return VAR_START;
  }

  private IElementType startExpression() {
    pushState(IN_EXPR);
    return EXPR_START;
  }

  private boolean followedBy(String text) {
    int start = zzMarkedPos;
    if (start + text.length() > zzEndRead) return false;
    for (int i = 0; i < text.length(); i++) {
      if (zzBuffer.charAt(start + i) != text.charAt(i)) return false;
    }
    return true;
  }

  private boolean endsWithPrefix(CharSequence text, String terminator, int length) {
    int offset = text.length() - length;
    for (int i = 0; i < length; i++) {
      if (text.charAt(offset + i) != terminator.charAt(i)) return false;
    }
    return true;
  }

  private void pushBackPartial(String... terminators) {
    CharSequence text = yytext();
    for (int length = Math.min(text.length() - 1, 10); length >= 1; length--) {
      for (String terminator : terminators) {
        if (length < terminator.length() && endsWithPrefix(text, terminator, length) && followedBy(terminator.substring(length))) {
          yypushback(length);
          return;
        }
      }
    }
  }

  private IElementType content() {
    CharSequence text = yytext();
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) return CONTENT;
    }
    return TokenType.WHITE_SPACE;
  }

  private IElementType singleQuoted() {
    if (contextRoot() == IN_ATTR_EXPR) {
      CharSequence text = yytext();
      for (int i = 1; i < text.length(); i++) {
        if (text.charAt(i) == '"') {
          yypushback(text.length() - i);
          break;
        }
      }
    }
    return STRING;
  }
%}

WHITE_SPACE=[ \t\n\r\f\u000B]+
LITERAL=[a-zA-Z0-9_]([a-zA-Z0-9_-]*[a-zA-Z0-9_])?
NUMBER=-?([0-9]+(\.[0-9]+)?|\.[0-9]+)
NON_WORD=[^a-zA-Z0-9_]
SINGLE_QUOTED='([^'\\]|\\[^])*'?
TEMPLATE_TEXT=!([^]*("{$"|"{{"|"<xf:"|"</xf:")[^]*)
RAW_TEXT=!([^]*"</xf:plain>"[^]*)
COMMENT_BLOCK="<xf:comment>"!([^]*"</xf:comment>"[^]*)("</xf:comment>")?

%xstate IN_TAG_NAME, IN_TAG, IN_ATTR_STRING, IN_ATTR_EXPR, IN_DQ, IN_EXPR, IN_VAR, IN_SIMPLE_VAR, IN_PAREN, IN_ARRAY, IN_HASH, IN_RAW

%%

<YYINITIAL> {
  {COMMENT_BLOCK}           { return COMMENT; }
  "<xf:plain>"              { yypushback(6); plainBlockOpening = true; return startTag(TAG_OPEN); }
  "{$"                      { return startVariable(); }
  "{{"                      { return startExpression(); }
  "</xf:"                   { return startTag(TAG_CLOSE_OPEN); }
  "<xf:"                    { return startTag(TAG_OPEN); }
  {TEMPLATE_TEXT}           { pushBackPartial("{$", "{{", "<xf:", "</xf:"); return content(); }
}

<IN_TAG_NAME> {
  {LITERAL}                 { tagName = yytext().toString(); yybegin(IN_TAG); return TAG_NAME; }
  [^]                       { yypushback(1); yybegin(IN_TAG); }
}

<IN_TAG> {
  {WHITE_SPACE}             { return TokenType.WHITE_SPACE; }
  "/>"                      { plainBlockOpening = false; popState(); return TAG_SELF_CLOSE; }
  ">"                       {
                              if (plainBlockOpening) {
                                plainBlockOpening = false;
                                yybegin(IN_RAW);
                              } else {
                                popState();
                              }
                              return TAG_END;
                            }
  "="                       { valueExpected = true; return ATTR_EQ; }
  "\""                      {
                              boolean expression = valueExpected && XfTags.INSTANCE.isExpressionAttribute(tagName, attributeName);
                              valueExpected = false;
                              pushState(expression ? IN_ATTR_EXPR : IN_ATTR_STRING);
                              return ATTR_QUOTE;
                            }
  {LITERAL}                 { attributeName = yytext().toString(); valueExpected = false; return ATTR_NAME; }
  "<"                       { yypushback(1); plainBlockOpening = false; popState(); }
  [^]                       { return TokenType.BAD_CHARACTER; }
}

<IN_RAW> {
  "</xf:plain>"             { yypushback(yylength()); popState(); }
  {RAW_TEXT}                { pushBackPartial("</xf:plain>"); return content(); }
}

<IN_ATTR_STRING> {
  "\""                      { popState(); return ATTR_QUOTE; }
  "{$"                      { return startVariable(); }
  "{{"                      { return startExpression(); }
  \\[^a-zA-Z0-9]            { return ATTR_TEXT; }
  [^\"\\{]+                 { return ATTR_TEXT; }
  [^]                       { return ATTR_TEXT; }
}

<IN_DQ> {
  "\""                      { popState(); return DQ; }
  "{$"                      { return startVariable(); }
  "{{"                      { return startExpression(); }
  \\[^a-zA-Z0-9]            { return STRING_TEXT; }
  [^\"\\{]+                 { return STRING_TEXT; }
  [^]                       { return STRING_TEXT; }
}

<IN_EXPR> {
  "}}"                      { popState(); return EXPR_END; }
}

<IN_PAREN> {
  ")"                       { popState(); return RPAREN; }
  ","                       { return COMMA; }
}

<IN_ARRAY> {
  "]"                       { popState(); return RBRACKET; }
  ","                       { return COMMA; }
}

<IN_HASH> {
  "}"                       { popState(); return RBRACE; }
  ","                       { return COMMA; }
}

<IN_PAREN, IN_ARRAY, IN_HASH> {
  [)\]}]                    {
                              if (closesOuter(yycharat(0))) {
                                yypushback(1);
                                popState();
                              } else {
                                return TokenType.BAD_CHARACTER;
                              }
                            }
}

<IN_EXPR, IN_PAREN, IN_ARRAY, IN_HASH, IN_ATTR_EXPR> {
  {WHITE_SPACE}             { return TokenType.WHITE_SPACE; }
  "\""                      {
                              if (contextRoot() == IN_ATTR_EXPR) return closeAttributeExpression();
                              pushState(IN_DQ);
                              return DQ;
                            }
  "<xf:" | "</xf:"          {
                              if (insideAttribute()) {
                                yypushback(yylength() - 1);
                                return LT;
                              }
                              yypushback(yylength());
                              resetToInitial();
                            }
  "{$"                      { return startVariable(); }
  "$" {LITERAL}?            { pushState(IN_SIMPLE_VAR); variableNameExpected = false; return VARIABLE; }
  "{{"                      { return startExpression(); }
  "("                       { pushState(IN_PAREN); return LPAREN; }
  "["                       { pushState(IN_ARRAY); return LBRACKET; }
  "{"                       { pushState(IN_HASH); return LBRACE; }
  {SINGLE_QUOTED}           { return singleQuoted(); }
  {NUMBER}                  { return NUMBER; }
  [iI][nN][sS][tT][aA][nN][cC][eE][oO][fF] / {NON_WORD} { return INSTANCEOF; }
  "*"                       { return STAR; }
  "/"                       { return SLASH; }
  "%"                       { return PERCENT; }
  "-"                       { return MINUS; }
  "+"                       { return PLUS; }
  "."                       { return CONCAT; }
  "??"                      { return COALESCE; }
  "?:"                      { return ELVIS; }
  "?"                       { return QUESTION; }
  ":"                       { return COLON; }
  ">="                      { return GE; }
  "<="                      { return LE; }
  ">"                       { return GT; }
  "<"                       { return LT; }
  "==="                     { return IDENTICAL; }
  "=="                      { return EQEQ; }
  "!=="                     { return NOT_IDENTICAL; }
  "!="                      { return NOT_EQ; }
  "!"                       { return NOT; }
  "&&"                      { return AND; }
  "||"                      { return OR; }
  [aA][nN][dD] / {NON_WORD} { return AND; }
  [oO][rR] / {NON_WORD}     { return OR; }
  [iI][sS]" "[nN][oO][tT] / {NON_WORD} { return IS_NOT; }
  [iI][sS] / {NON_WORD}     { return IS; }
  [tT][rR][uU][eE] / {NON_WORD} { return TRUE; }
  [fF][aA][lL][sS][eE] / {NON_WORD} { return FALSE; }
  [nN][uU][lL][lL] / {NON_WORD} { return NULL; }
  {LITERAL}                 { return IDENTIFIER; }
  "|"                       { return PIPE; }
  [^]                       { return TokenType.BAD_CHARACTER; }
}

<IN_VAR, IN_SIMPLE_VAR> {
  "."                       { variableNameExpected = false; return DOT; }
  "->"                      { variableNameExpected = false; return ARROW; }
  "{$"                      { return startVariable(); }
  "{{"                      { return startExpression(); }
  "|"                       { variableNameExpected = false; return PIPE; }
  "("                       { variableNameExpected = false; pushState(IN_PAREN); return LPAREN; }
  {LITERAL}                 {
                              IElementType type = variableNameExpected ? VARIABLE : IDENTIFIER;
                              variableNameExpected = false;
                              return type;
                            }
}

<IN_VAR> {
  "}"                       { variableNameExpected = false; popState(); return VAR_END; }
  [^]                       { variableNameExpected = false; yypushback(1); popState(); }
}

<IN_SIMPLE_VAR> {
  [^]                       { yypushback(1); popState(); }
}
