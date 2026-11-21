package nt.xenforo.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import nt.xenforo.lexer.XfLexerAdapter
import nt.xenforo.psi.XfFile
import nt.xenforo.psi.XfTokenSets
import nt.xenforo.psi.XfTypes

class XfParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = XfLexerAdapter()

    override fun createParser(project: Project?): PsiParser = XfParser()

    override fun getFileNodeType(): IFileElementType = XfTokenSets.FILE

    override fun getCommentTokens(): TokenSet = XfTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = XfTokenSets.STRING_LITERALS

    override fun createElement(node: ASTNode): PsiElement = XfTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = XfFile(viewProvider)
}
