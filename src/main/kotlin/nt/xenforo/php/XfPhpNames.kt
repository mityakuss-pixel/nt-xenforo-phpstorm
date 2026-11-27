package nt.xenforo.php

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayIndex
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.documentation.XfDocumentation
import nt.xenforo.documentation.XfDocumentationTarget
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfIcons
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfPhraseNames
import javax.swing.Icon

private const val PUBLIC = "public"
private const val ADMIN = "admin"
private const val EMAIL = "email"
private const val ADMIN_NAMESPACE = "\\Admin\\"
private const val HINT_LIMIT = 40
private const val NODE_PERMISSION_GROUP = "forum"
private const val CONTAINER_METHOD = "container"
private const val FACTORY_METHOD = "create"
private const val CONTAINER_CLASS = "\\XF\\Container"
private const val CORE_NAMESPACE = "\\XF\\"
private const val APP_CLASS_SUFFIX = "\\App"
private val ROUTE_MODIFIER = Regex("""^[a-z_]+:""")

data class XfPhpSlot(val kind: XfNameKind, val range: TextRange, val keyPrefix: String) {
    fun name(literal: StringLiteralExpression): String = range.substring(literal.text)

    fun keys(literal: StringLiteralExpression): List<String> {
        val name = name(literal)
        if (kind != XfNameKind.ROUTE) return listOf(keyPrefix + name)
        val parts = name.split('/')
        return if (parts.size > 1) listOf(keyPrefix + parts[0] + "/" + parts[1], keyPrefix + parts[0]) else listOf(keyPrefix + name)
    }
}

object XfPhpNames {
    fun slot(literal: StringLiteralExpression): XfPhpSlot? {
        if (literal.parent is ArrayIndex) return containerAccess(literal)
        val parameters = literal.parent as? ParameterList ?: return null
        val call = parameters.parent as? FunctionReference ?: return null
        val callName = call.name?.lowercase() ?: return null
        if (!literal.isSingleQuote && literal.text.contains('$')) return null
        val index = parameters.parameters.indexOf(literal)
        val value = literal.contents
        val start = literal.valueRange.startOffset
        return when (callName to index) {
            "phrase" to 0, "phrasedeferred" to 0 -> {
                val effective = XfPhraseNames.effectiveRange(value)
                XfPhpSlot(XfNameKind.PHRASE, TextRange(start + effective.first, start + effective.last + 1), XfSymbolKeys.PHRASE_PREFIX)
            }
            "view" to 1 -> if (call is MethodReference) template(value, start, sectionType(literal)) else null
            "rendertemplate" to 0, "rendermacro" to 0 -> template(value, start, PUBLIC)
            "rendermacro" to 1 -> {
                val template = (parameters.parameters.firstOrNull() as? StringLiteralExpression)?.contents ?: return null
                val (type, nameStart) = splitType(template, PUBLIC)
                XfPhpSlot(XfNameKind.MACRO, TextRange(start, start + value.length), XfSymbolKeys.macroPrefix(type, template.substring(nameStart)))
            }
            "settemplate" to 0 -> if (call is MethodReference) template(value, start, EMAIL) else null
            "buildlink" to 0 -> if (call is MethodReference) route(value, start, routeType(call, literal)) else null
            "haspermission" to 1 -> (parameters.parameters.firstOrNull() as? StringLiteralExpression)?.contents?.takeIf { it.isNotEmpty() }
                ?.let { XfPhpSlot(XfNameKind.PERMISSION, TextRange(start, start + value.length), XfSymbolKeys.permissionPrefix(it)) }
            "hasnodepermission" to 1, "hascontentpermission" to 1 ->
                XfPhpSlot(XfNameKind.PERMISSION, TextRange(start, start + value.length), XfSymbolKeys.permissionPrefix(NODE_PERMISSION_GROUP))
            "hasadminpermission" to 0 -> XfPhpSlot(XfNameKind.PERMISSION, TextRange(start, start + value.length), XfSymbolKeys.ADMIN_PERMISSION_PREFIX)
            CONTAINER_METHOD to 0 -> if (call is MethodReference) containerSlot(literal) else null
            FACTORY_METHOD to 0 -> if (call is MethodReference) factorySlot(literal, parameters) else null
            else -> null
        }
    }

    private fun containerAccess(literal: StringLiteralExpression): XfPhpSlot? {
        val access = literal.parent?.parent as? ArrayAccessExpression ?: return null
        if (access.index?.value != literal || !literal.isSingleQuote && literal.text.contains('$')) return null
        val project = literal.project
        if (DumbService.isDumb(project)) return null
        if (XfSymbolKeys.container(literal.contents) !in XfDataTracker.keys(project, XfSymbolKeys.CONTAINER_PREFIX)) return null
        val holder = access.value as? PhpTypedElement ?: return null
        if (holder.globalType.types.none(::isContainerType)) return null
        return containerSlot(literal)
    }

    private fun containerSlot(literal: StringLiteralExpression): XfPhpSlot {
        val start = literal.valueRange.startOffset
        return XfPhpSlot(XfNameKind.CONTAINER, TextRange(start, start + literal.contents.length), XfSymbolKeys.CONTAINER_PREFIX)
    }

    private fun factorySlot(literal: StringLiteralExpression, parameters: ParameterList): XfPhpSlot? {
        val value = literal.contents
        if (parameters.parameters.size < 2 || value.contains(':') || DumbService.isDumb(literal.project)) return null
        val typing = value.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
        if (!typing && XfSymbolKeys.containerFactory(value) !in XfDataTracker.keys(literal.project, XfSymbolKeys.CONTAINER_FACTORY_PREFIX)) return null
        val start = literal.valueRange.startOffset
        return XfPhpSlot(XfNameKind.CONTAINER, TextRange(start, start + value.length), XfSymbolKeys.CONTAINER_FACTORY_PREFIX)
    }

    private fun isContainerType(type: String): Boolean =
        type == CONTAINER_CLASS || type.startsWith(CORE_NAMESPACE) && type.endsWith(APP_CLASS_SUFFIX)

    fun documentation(file: PsiFile, offset: Int): XfDocumentationTarget? {
        if (file !is PhpFile) return null
        val project = file.project
        if (DumbService.isDumb(project)) return null
        for (candidate in listOf(offset, offset - 1)) {
            if (candidate < 0) continue
            val literal = PsiTreeUtil.getParentOfType(file.findElementAt(candidate), StringLiteralExpression::class.java, false) ?: continue
            val slot = slot(literal) ?: continue
            val inLiteral = candidate - literal.textRange.startOffset
            if (inLiteral < slot.range.startOffset || inLiteral > slot.range.endOffset) continue
            val name = slot.name(literal).takeIf { it.isNotEmpty() } ?: continue
            val key = slot.keys(literal).firstOrNull { XfSymbols.locations(project, it).isNotEmpty() } ?: continue
            return XfDocumentation.symbol(project, slot.kind, slot.keyPrefix, key, name)
        }
        return null
    }

    private fun template(value: String, start: Int, defaultType: String): XfPhpSlot {
        val (type, nameStart) = splitType(value, defaultType)
        return XfPhpSlot(XfNameKind.TEMPLATE, TextRange(start + nameStart, start + value.length), XfSymbolKeys.templatePrefix(type))
    }

    private fun route(value: String, start: Int, type: String): XfPhpSlot {
        val modifier = ROUTE_MODIFIER.find(value)?.value?.length ?: 0
        return XfPhpSlot(XfNameKind.ROUTE, TextRange(start + modifier, start + value.length), XfSymbolKeys.routePrefix(type))
    }

    private fun splitType(value: String, defaultType: String): Pair<String, Int> {
        val colon = value.indexOf(':')
        if (colon > 0 && value.substring(0, colon) in XfTemplateFiles.TEMPLATE_TYPES) return value.substring(0, colon) to colon + 1
        return defaultType to 0
    }

    private fun sectionType(element: PsiElement): String {
        val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java) ?: return PUBLIC
        return if (phpClass.fqn.contains(ADMIN_NAMESPACE)) ADMIN else PUBLIC
    }

    private fun routeType(call: MethodReference, literal: StringLiteralExpression): String {
        val router = call.classReference as? MethodReference
        if (router?.name.equals("router", ignoreCase = true)) {
            (router?.parameters?.firstOrNull() as? StringLiteralExpression)?.contents?.takeIf { it.isNotEmpty() }?.let { return it }
            return PUBLIC
        }
        return sectionType(literal)
    }
}

class XfPhpReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression::class.java), XfPhpReferenceProvider())
    }
}

private class XfPhpReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val slot = XfPhpNames.slot(literal) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(XfPhpNameReference(literal, slot))
    }
}

class XfPhpNameReference(literal: StringLiteralExpression, val slot: XfPhpSlot) :
    PsiPolyVariantReferenceBase<StringLiteralExpression>(literal, slot.range, true) {

    val name: String
        get() = slot.name(element)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, PHP_RESOLVER, false, incompleteCode)
            .ifEmpty { resolveTargets() }

    fun resolveTargets(): Array<ResolveResult> {
        val project = element.project
        if (DumbService.isDumb(project)) return ResolveResult.EMPTY_ARRAY
        val name = name
        if (name.isEmpty()) return ResolveResult.EMPTY_ARRAY
        for (key in slot.keys(element)) {
            val targets = XfSymbols.targets(project, key, name)
            if (targets.isNotEmpty()) return PsiElementResolveResult.createResults(targets)
        }
        return ResolveResult.EMPTY_ARRAY
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

class XfPhpCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(StringLiteralExpression::class.java), XfPhpNameCompletion())
    }
}

private class XfPhpNameCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val literal = parameters.position.parent as? StringLiteralExpression ?: return
        val slot = XfPhpNames.slot(literal) ?: return
        val caret = parameters.offset - literal.textRange.startOffset
        if (caret < slot.range.startOffset || caret > slot.range.endOffset) return
        val project = literal.project
        if (DumbService.isDumb(project)) return
        val names = result.withPrefixMatcher(literal.text.substring(slot.range.startOffset, caret))
        val icon = icon(slot.kind)
        for (key in XfSymbols.keys(project, slot.keyPrefix)) {
            val name = key.removePrefix(slot.keyPrefix)
            if (slot.kind == XfNameKind.TEMPLATE && (name.endsWith(".less") || name.endsWith(".css"))) continue
            val lookup = LookupElementBuilder.create(name).withIcon(icon)
            val detailed = slot.kind == XfNameKind.PHRASE || slot.kind == XfNameKind.ROUTE
            names.addElement(if (detailed) lookup.withTypeText(XfSymbols.detail(project, key)?.takeIf { it.isNotEmpty() }, true) else lookup)
        }
    }

    private fun icon(kind: XfNameKind): Icon = when (kind) {
        XfNameKind.TEMPLATE -> XfIcons.FILE
        XfNameKind.MACRO -> AllIcons.Nodes.Method
        XfNameKind.ROUTE -> AllIcons.Nodes.Related
        XfNameKind.CONTAINER -> AllIcons.Nodes.Variable
        else -> AllIcons.Nodes.Tag
    }
}

private val PHP_RESOLVER = ResolveCache.PolyVariantResolver<XfPhpNameReference> { reference, _ -> reference.resolveTargets() }

class XfPhpPhraseInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector(file.project)

    private class Collector(private val project: Project) : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val literal = element as? StringLiteralExpression ?: return
            val slot = XfPhpNames.slot(literal)?.takeIf { it.kind == XfNameKind.PHRASE } ?: return
            val name = slot.name(literal).takeIf { it.isNotEmpty() } ?: return
            val text = XfSymbols.detail(project, XfSymbolKeys.phrase(name))?.takeIf { it.isNotBlank() } ?: return
            val call = literal.parent?.parent ?: return
            sink.addPresentation(InlineInlayPosition(call.textRange.endOffset, true), tooltip = text, hintFormat = HintFormat.default) {
                text(if (text.length > HINT_LIMIT) text.take(HINT_LIMIT).trimEnd() + "…" else text)
            }
        }
    }
}
