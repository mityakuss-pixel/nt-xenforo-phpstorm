package nt.xenforo.php

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpReturn
import nt.xenforo.XfBundle.message

private const val PARENT = "parent"
private const val ACTION_PREFIX = "action"
private const val REPLY_CLASS = "\\XF\\Mvc\\Reply\\View"
private const val CONDITION_MARKER = "instanceof"

class XfReplyCheckIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = message("intention.reply.family")

    override fun getText(): String = message("intention.reply.text")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean = statement(element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val statement = statement(element) ?: return
        val call = statement.argument as? MethodReference ?: return
        val file = statement.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val start = statement.textRange.startOffset
        val variable = "\$reply"
        val text = "$variable = ${call.text};\nif ($variable $CONDITION_MARKER $REPLY_CLASS)\n{\n}\n\nreturn $variable;"
        document.replaceString(start, statement.textRange.endOffset, text)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        CodeStyleManager.getInstance(project).reformatText(file, start, start + text.length)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        val chars = document.charsSequence
        val condition = chars.indexOf(CONDITION_MARKER, start)
        val brace = if (condition < 0) -1 else chars.indexOf('{', condition)
        if (brace < 0 || editor == null) return
        val line = document.getLineNumber(brace)
        val lineStart = document.getLineStartOffset(line)
        val indent = chars.subSequence(lineStart, lineStart + chars.subSequence(lineStart, brace).takeWhile { it == ' ' || it == '\t' }.length)
        val options = CodeStyle.getIndentOptions(file)
        val unit = if (options.USE_TAB_CHARACTER) "\t" else " ".repeat(options.INDENT_SIZE)
        val body = "\n$indent$unit"
        document.insertString(brace + 1, body)
        editor.caretModel.moveToOffset(brace + 1 + body.length)
    }

    private fun statement(element: PsiElement): PhpReturn? {
        val statement = PsiTreeUtil.getParentOfType(element, PhpReturn::class.java, false) ?: return null
        val call = statement.argument as? MethodReference ?: return null
        if (call.classReference?.text != PARENT) return null
        return statement.takeIf { call.name?.startsWith(ACTION_PREFIX) == true }
    }
}
