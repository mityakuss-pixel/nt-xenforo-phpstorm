package nt.xenforo.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import nt.xenforo.lang.XfIcons
import nt.xenforo.meta.XfBuiltins
import nt.xenforo.meta.XfGlobals
import nt.xenforo.meta.XfTagInfo
import nt.xenforo.meta.XfTags
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfEndTagElement
import nt.xenforo.psi.XfStringExpr
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.psi.XfTestExpr
import nt.xenforo.psi.XfTypes

class XfCompletionContributor : CompletionContributor(), DumbAware {
    init {
        extend(CompletionType.BASIC, psiElement(XfTypes.TAG_NAME), TagNameProvider())
        extend(CompletionType.BASIC, psiElement(XfTypes.ATTR_NAME), AttributeNameProvider())
        extend(CompletionType.BASIC, psiElement(XfTypes.IDENTIFIER), IdentifierProvider())
        extend(CompletionType.BASIC, psiElement(XfTypes.VARIABLE), VariableProvider())
        extend(CompletionType.BASIC, psiElement(XfTypes.ATTR_TEXT).withParent(XfAttributeValue::class.java), XfNameCompletionProvider())
        extend(CompletionType.BASIC, psiElement(XfTypes.STRING).withParent(XfStringExpr::class.java), XfNameCompletionProvider())
    }
}

private class TagNameProvider : CompletionProvider<CompletionParameters>(), DumbAware {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.position
        val endTag = position.parent as? XfEndTagElement
        if (endTag != null) {
            val owner = endTag.parent as? XfTagElement
            val names = generateSequence(owner) { PsiTreeUtil.getParentOfType(it, XfTagElement::class.java) }
                .filter { it.isBlock }
                .map { it.tagName }
                .filter { it.isNotEmpty() }
                .distinct()
            for (name in names) {
                result.addElement(LookupElementBuilder.create(name).withIcon(XfIcons.TAG).withInsertHandler(ClosingTagInsertHandler))
            }
            return
        }
        for (info in XfTags.ALL) {
            result.addElement(tagLookup(info))
        }
    }
}

fun tagLookup(info: XfTagInfo, prefix: String = ""): LookupElement =
    LookupElementBuilder.create(info, prefix + info.name)
        .withIcon(XfIcons.TAG)
        .withTailText(info.required.joinToString(" ", prefix = if (info.required.isEmpty()) "" else " ") { "$it=\"\"" }, true)
        .withTypeText(if (info.block) "<xf:${info.name}>…</xf:${info.name}>" else "<xf:${info.name} />", true)
        .withInsertHandler(TagInsertHandler)

private object TagInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val info = item.`object` as? XfTagInfo ?: return
        val document = context.document
        val tail = context.tailOffset
        val next = document.charsSequence.getOrNull(tail)
        if (next != null && next != '\n' && next != '\r' && next != '<') {
            if (next == ' ' && document.charsSequence.getOrNull(tail + 1)?.isLetter() == true) return
            if (next == '>' || next == '/') return
        }
        val attributes = info.required.joinToString("") { " $it=\"\"" }
        val ending = when {
            info.name == "else" || info.name == "elseif" -> " />"
            info.block -> "></xf:${info.name}>"
            else -> " />"
        }
        document.insertString(tail, attributes + ending)
        val caret = when {
            info.required.isNotEmpty() -> tail + info.required.first().length + 3
            info.block -> tail + 1
            else -> tail + ending.length
        }
        context.editor.caretModel.moveToOffset(caret)
    }
}

private object ClosingTagInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val tail = context.tailOffset
        if (document.charsSequence.getOrNull(tail) != '>') {
            document.insertString(tail, ">")
        }
        context.editor.caretModel.moveToOffset(tail + 1)
    }
}

private class AttributeNameProvider : CompletionProvider<CompletionParameters>(), DumbAware {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val header = PsiTreeUtil.getParentOfType(parameters.position, XfTagHeader::class.java) ?: return
        val info = XfTags.get(header.tagName) ?: return
        val existing = header.getAttributeList().map { it.attributeName.lowercase() }.toSet()
        for (name in info.attributes) {
            if (name in existing) continue
            val lookup = LookupElementBuilder.create(name)
                .withInsertHandler(AttributeInsertHandler)
                .withBoldness(name in info.required)
            result.addElement(lookup)
        }
        if (header.tagName.equals("macro", ignoreCase = true)) {
            result.addElement(LookupElementBuilder.create("arg-").withInsertHandler(null).withTypeText("arg-name=\"\"", true))
        }
    }
}

private object AttributeInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val tail = context.tailOffset
        if (document.charsSequence.getOrNull(tail) == '=') return
        document.insertString(tail, "=\"\"")
        context.editor.caretModel.moveToOffset(tail + 2)
    }
}

private class IdentifierProvider : CompletionProvider<CompletionParameters>(), DumbAware {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.position
        when (val parent = position.parent) {
            is XfFilter -> addCallables(result, XfBuiltins.FILTERS, AllIcons.Nodes.Method, requiredOnly = true)
            is XfTestExpr -> addCallables(result, XfBuiltins.TESTS, AllIcons.Nodes.Method, requiredOnly = true)
            is XfFunctionCallExpr -> if (parent.callNameElement == position) addCallables(result, XfBuiltins.FUNCTIONS, AllIcons.Nodes.Function, requiredOnly = false)
            else -> addAccessors(position, result)
        }
    }

    private fun addAccessors(position: PsiElement, result: CompletionResultSet) {
        val previous = previousToken(position) ?: return
        if (previous.elementType != XfTypes.DOT && previous.elementType != XfTypes.ARROW) return
        val owner = previousToken(previous) ?: return
        if (owner.elementType != XfTypes.VARIABLE || owner.text.removePrefix("$") != "xf") return
        for (key in XfGlobals.XF_KEYS) {
            result.addElement(LookupElementBuilder.create(key).withIcon(AllIcons.Nodes.Property))
        }
    }

    private fun addCallables(result: CompletionResultSet, callables: Map<String, String>, icon: javax.swing.Icon, requiredOnly: Boolean) {
        for ((name, signature) in callables) {
            val needsParentheses = !requiredOnly || hasRequiredParameter(signature)
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(icon)
                    .withTailText("($signature)", true)
                    .withInsertHandler(if (needsParentheses) ParenthesesInsertHandler(signature.isNotEmpty()) else null),
            )
        }
    }

    private fun hasRequiredParameter(signature: String): Boolean =
        signature.isNotBlank() && !signature.substringBefore(',').contains('=')
}

private class ParenthesesInsertHandler(private val hasParameters: Boolean) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (context.completionChar == '(') context.setAddCompletionChar(false)
        val document = context.document
        val tail = context.tailOffset
        if (document.charsSequence.getOrNull(tail) == '(') {
            context.editor.caretModel.moveToOffset(tail + 1)
            return
        }
        document.insertString(tail, "()")
        context.editor.caretModel.moveToOffset(if (hasParameters) tail + 1 else tail + 2)
    }
}

private class VariableProvider : CompletionProvider<CompletionParameters>(), DumbAware {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val names = LinkedHashSet<String>()
        names += XfGlobals.VARIABLES
        collectVariableNames(parameters.originalFile, names)
        for (name in names) {
            if (name.isEmpty()) continue
            result.addElement(LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable).withPresentableText("$$name"))
        }
    }

    private fun collectVariableNames(file: PsiFile, names: MutableSet<String>) {
        val base = file.viewProvider.getPsi(file.viewProvider.baseLanguage) ?: return
        PsiTreeUtil.processElements(base) { element ->
            if (element.elementType == XfTypes.VARIABLE) {
                names += element.text.removePrefix("$")
            } else if (element.elementType == XfTypes.ATTR_NAME && element.text.startsWith("arg-")) {
                names += element.text.removePrefix("arg-")
            }
            true
        }
    }
}

private fun previousToken(element: PsiElement): PsiElement? {
    var current: PsiElement? = PsiTreeUtil.prevLeaf(element)
    while (current != null && current.elementType == TokenType.WHITE_SPACE) current = PsiTreeUtil.prevLeaf(current)
    return current
}
