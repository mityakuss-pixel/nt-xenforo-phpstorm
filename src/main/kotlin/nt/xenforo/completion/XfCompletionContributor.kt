package nt.xenforo.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import javax.swing.Icon
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.reference.XfLocalMacros
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameSlots
import nt.xenforo.editor.XfBranchIndent
import nt.xenforo.lang.XfIcons
import nt.xenforo.meta.XfGlobals
import nt.xenforo.meta.XfTagInfo
import nt.xenforo.index.XfCallableKind
import nt.xenforo.model.XfAccess
import nt.xenforo.php.XfTemplateParams
import com.intellij.openapi.project.DumbService
import nt.xenforo.model.XfCallables
import nt.xenforo.model.XfMember
import nt.xenforo.model.XfMemberKind
import nt.xenforo.model.XfMembers
import nt.xenforo.model.XfTagCatalog
import nt.xenforo.psi.XfAccessExpression
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
        for (info in XfTagCatalog.all(position.project)) {
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
        if (info.name == "else" || info.name == "elseif") XfBranchIndent.adjust(context.project, context.editor, context.file)
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
        val info = XfTagCatalog.get(header.project, header.tagName) ?: return
        val existing = header.getAttributeList().map { it.attributeName.lowercase() }.toSet()
        for (name in info.attributes) {
            if (name in existing) continue
            val lookup = LookupElementBuilder.create(name)
                .withInsertHandler(AttributeInsertHandler)
                .withBoldness(name in info.required)
            result.addElement(lookup)
        }
        if (header.tagName.equals("macro", ignoreCase = true)) {
            for (argument in XfMacroArguments.ofCall(header)) {
                val name = "arg-$argument"
                if (name.lowercase() in existing) continue
                val lookup = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Parameter).withInsertHandler(AttributeInsertHandler)
                result.addElement(PrioritizedLookupElement.withPriority(lookup, MACRO_ARGUMENT_PRIORITY))
            }
            result.addElement(LookupElementBuilder.create("arg-").withInsertHandler(null).withTypeText("arg-name=\"\"", true))
        }
    }
}

private const val MACRO_ARGUMENT_PRIORITY = 10.0

object XfMacroArguments {
    private const val ID_ATTRIBUTE = "id"
    private const val NAME_ATTRIBUTE = "name"
    private const val SEPARATOR = ", "

    fun ofCall(header: XfTagHeader): List<String> {
        val tag = header.parent as? XfTagElement
        if (tag != null && tag.isBlock && tag.header == header) return emptyList()
        val host = (header.findAttribute(ID_ATTRIBUTE) ?: header.findAttribute(NAME_ATTRIBUTE))?.attributeValue as? XfReferenceHost ?: return emptyList()
        val slot = XfNameSlots.of(host).lastOrNull { it.kind == XfNameKind.MACRO } ?: return emptyList()
        val name = slot.name(host).takeIf { it.isNotEmpty() } ?: return emptyList()
        val arguments = if (XfNameSlots.isCurrentTemplate(host, slot)) {
            XfLocalMacros.definitions(host.containingFile)[name]?.let(XfLocalMacros::arguments)
        } else {
            if (DumbService.isDumb(host.project)) return emptyList()
            slot.keys(host).firstNotNullOfOrNull { XfSymbols.detail(host.project, it) }
        }
        return arguments?.split(SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
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
            is XfFilter -> addCallables(position, result, XfCallableKind.FILTER, AllIcons.Nodes.Method, requiredOnly = true)
            is XfTestExpr -> addCallables(position, result, XfCallableKind.TEST, AllIcons.Nodes.Method, requiredOnly = true)
            is XfFunctionCallExpr -> if (parent.callNameElement == position) addCallables(position, result, XfCallableKind.FUNCTION, AllIcons.Nodes.Function, requiredOnly = false)
            else -> addAccessors(position, result)
        }
    }

    private fun addAccessors(position: PsiElement, result: CompletionResultSet) {
        val expression = position.parent as? XfAccessExpression ?: return
        val index = XfAccess.segmentIndex(expression, position)
        if (index < 0) return
        val project = position.project
        val owner = XfAccess.ownerType(project, expression, index) ?: return
        for (member in XfMembers.of(project, owner)) {
            result.addElement(memberLookup(project, member))
        }
    }

    private fun memberLookup(project: Project, member: XfMember): LookupElement {
        val icon = if (member.kind == XfMemberKind.METHOD) AllIcons.Nodes.Method else AllIcons.Nodes.Property
        var builder = LookupElementBuilder.create(member, member.name).withIcon(icon)
        if (member.kind == XfMemberKind.METHOD) {
            builder = builder
                .withTailText("(${member.parameters.orEmpty()})", true)
                .withInsertHandler(ParenthesesInsertHandler(!member.parameters.isNullOrEmpty()))
        }
        return if (member.kind == XfMemberKind.OPTION) {
            builder.withExpensiveRenderer(OptionRenderer(project, icon))
        } else {
            builder.withTypeText(member.typeText, true)
        }
    }

    private fun addCallables(position: PsiElement, result: CompletionResultSet, kind: XfCallableKind, icon: Icon, requiredOnly: Boolean) {
        for ((name, signature) in XfCallables.builtins(kind)) {
            val needsParentheses = !requiredOnly || hasRequiredParameter(signature)
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(icon)
                    .withTailText("($signature)", true)
                    .withInsertHandler(if (needsParentheses) ParenthesesInsertHandler(signature.isNotEmpty()) else null),
            )
        }
        val project = position.project
        for (name in XfCallables.customNames(project, kind)) {
            val location = XfCallables.registration(project, kind, name) ?: continue
            result.addElement(
                LookupElementBuilder.create(location.symbol.detail.ifEmpty { name })
                    .withIcon(icon)
                    .withTypeText(location.addOnId, true)
                    .withInsertHandler(ParenthesesInsertHandler(true).takeIf { !requiredOnly })
                    .withExpensiveRenderer(CallableRenderer(project, kind, icon, location.addOnId)),
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
        } else {
            document.insertString(tail, "()")
            context.editor.caretModel.moveToOffset(if (hasParameters) tail + 1 else tail + 2)
        }
        if (hasParameters) AutoPopupController.getInstance(context.project).autoPopupParameterInfo(context.editor, null)
    }
}

private class CallableRenderer(
    private val project: Project,
    private val kind: XfCallableKind,
    private val icon: Icon,
    private val addOnId: String?,
) : LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        presentation.itemText = element.lookupString
        presentation.icon = icon
        presentation.setTypeText(addOnId)
        val parameters = XfCallables.parameters(project, kind, element.lookupString) ?: return
        presentation.setTailText("(${parameters.joinToString(", ")})", true)
    }
}

private class OptionRenderer(private val project: Project, private val icon: Icon) : LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        presentation.itemText = element.lookupString
        presentation.icon = icon
        val title = XfSymbols.detail(project, XfSymbolKeys.phrase(OPTION_TITLE_PREFIX + element.lookupString))
        presentation.setTypeText(title?.ifEmpty { null } ?: (element.`object` as? XfMember)?.typeText)
    }
}

const val OPTION_TITLE_PREFIX: String = "option."

private class VariableProvider : CompletionProvider<CompletionParameters>(), DumbAware {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val names = LinkedHashSet<String>()
        names += XfGlobals.VARIABLES
        val file = parameters.originalFile
        val params = if (DumbService.isDumb(file.project)) emptyMap() else XfTemplateParams.of(file)
        names += params.keys
        collectVariableNames(file, names)
        for (name in names) {
            if (name.isEmpty()) continue
            val lookup = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable).withPresentableText("$$name")
            result.addElement(if (name in params) lookup.withTypeText(XfTemplateParams.presentableType(file, name), true).bold() else lookup)
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
