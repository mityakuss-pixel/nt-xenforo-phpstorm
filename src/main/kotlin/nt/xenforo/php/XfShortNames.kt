package nt.xenforo.php

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

private const val SEPARATOR = '\\'
private val SHORT_NAME = Regex("""^[A-Za-z][\w\\/]*:(?:[A-Za-z]\w*:)?[A-Za-z][\w\\]*$""")
private val TYPING = Regex("""^[A-Za-z][\w\\/]*(?::[\w\\]*){0,2}$""")

enum class XfShortKind(val namespace: String, val suffix: String?, val base: String?, val sectioned: Boolean = false) {
    ENTITY("Entity", null, "\\XF\\Mvc\\Entity\\Entity"),
    FINDER("Finder", "Finder", "\\XF\\Mvc\\Entity\\Finder"),
    REPOSITORY("Repository", "Repository", "\\XF\\Mvc\\Entity\\Repository"),
    SERVICE("Service", "Service", "\\XF\\Service\\AbstractService"),
    HELPER("Helper", null, null),
    PLUGIN("ControllerPlugin", "Plugin", "\\XF\\ControllerPlugin\\AbstractPlugin", true),
    CRITERIA("Criteria", "Criteria", "\\XF\\Criteria\\AbstractCriteria"),
    JOB("Job", null, "\\XF\\Job\\AbstractJob"),
    BEHAVIOR("Behavior", null, "\\XF\\Mvc\\Entity\\Behavior"),
    DATA("Data", null, null),
    VALIDATOR("Validator", null, "\\XF\\Validator\\AbstractValidator"),
    VIEW("View", null, "\\XF\\Mvc\\View", true),
    CONTROLLER("Controller", "Controller", "\\XF\\Mvc\\Controller", true),
    WIDGET("Widget", null, "\\XF\\Widget\\AbstractWidget"),
    THREAD_TYPE("ThreadType", "Handler", "\\XF\\ThreadType\\AbstractHandler"),
    FORUM_TYPE("ForumType", "Handler", "\\XF\\ForumType\\AbstractHandler"),
}

data class XfShortContext(val kinds: List<XfShortKind>, val section: String, val acceptsClass: Boolean = true)

object XfShortNames {
    private val CALLS: Map<String, Pair<Int, List<XfShortKind>>> = mapOf(
        "finder" to (0 to listOf(XfShortKind.FINDER, XfShortKind.ENTITY)),
        "getfinder" to (0 to listOf(XfShortKind.FINDER, XfShortKind.ENTITY)),
        "create" to (0 to listOf(XfShortKind.ENTITY)),
        "find" to (0 to listOf(XfShortKind.ENTITY)),
        "findone" to (0 to listOf(XfShortKind.ENTITY)),
        "findcached" to (0 to listOf(XfShortKind.ENTITY)),
        "findbyids" to (0 to listOf(XfShortKind.ENTITY)),
        "getentitystructure" to (0 to listOf(XfShortKind.ENTITY)),
        "instantiateentity" to (0 to listOf(XfShortKind.ENTITY)),
        "assertrecordexists" to (0 to listOf(XfShortKind.ENTITY)),
        "repository" to (0 to listOf(XfShortKind.REPOSITORY)),
        "getrepository" to (0 to listOf(XfShortKind.REPOSITORY)),
        "service" to (0 to listOf(XfShortKind.SERVICE)),
        "helper" to (0 to listOf(XfShortKind.HELPER)),
        "plugin" to (0 to listOf(XfShortKind.PLUGIN)),
        "criteria" to (0 to listOf(XfShortKind.CRITERIA)),
        "job" to (0 to listOf(XfShortKind.JOB)),
        "enqueue" to (0 to listOf(XfShortKind.JOB)),
        "enqueueunique" to (1 to listOf(XfShortKind.JOB)),
        "enqueuelater" to (2 to listOf(XfShortKind.JOB)),
        "view" to (0 to listOf(XfShortKind.VIEW)),
        "data" to (0 to listOf(XfShortKind.DATA)),
        "validator" to (0 to listOf(XfShortKind.VALIDATOR)),
        "reroutecontroller" to (0 to listOf(XfShortKind.CONTROLLER)),
        "insertthreadtype" to (1 to listOf(XfShortKind.THREAD_TYPE)),
        "insertforumtype" to (1 to listOf(XfShortKind.FORUM_TYPE)),
    )
    private const val ENTITY_KEY = "entity"
    private const val BEHAVIORS = "behaviors"
    private const val SHORT_NAME_FIELD = "shortName"

    fun context(literal: StringLiteralExpression, typing: Boolean = false): XfShortContext? {
        if (!literal.isSingleQuote && literal.text.contains('$')) return null
        if (!typing && !SHORT_NAME.matches(literal.contents)) return null
        val parent = literal.parent
        if (parent is ParameterList) {
            val call = parent.parent as? FunctionReference ?: return null
            val (index, kinds) = CALLS[call.name?.lowercase()] ?: return null
            if (parent.parameters.indexOf(literal) != index) return null
            return XfShortContext(kinds, section(literal), acceptsClass = XfShortKind.VIEW !in kinds)
        }
        val hash = parent as? ArrayHashElement ?: parent?.parent as? ArrayHashElement
        if (hash != null) {
            if (hash.value == literal && (hash.key as? StringLiteralExpression)?.contents == ENTITY_KEY) return XfShortContext(listOf(XfShortKind.ENTITY), section(literal))
            if (hash.key == literal && isAssignedTo(hash.parent, BEHAVIORS)) return XfShortContext(listOf(XfShortKind.BEHAVIOR), section(literal), acceptsClass = false)
            return null
        }
        val index = PsiTreeUtil.getParentOfType(literal, ArrayAccessExpression::class.java)
        if (index != null && index.index?.value == literal && (index.value as? FieldReference)?.name == BEHAVIORS) return XfShortContext(listOf(XfShortKind.BEHAVIOR), section(literal), acceptsClass = false)
        val assignment = parent as? AssignmentExpression
        if (assignment != null && assignment.value == literal && (assignment.variable as? FieldReference)?.name == SHORT_NAME_FIELD) {
            return XfShortContext(listOf(XfShortKind.ENTITY), section(literal), acceptsClass = false)
        }
        return null
    }

    fun candidates(name: String, kind: XfShortKind, section: String): List<String> {
        val parts = name.split(':')
        if (parts.size < 2) return listOf(SEPARATOR + name.trimStart(SEPARATOR))
        val prefix = parts[0].replace('/', SEPARATOR)
        val rest = parts.last()
        val infix = if (parts.size == 3) parts[1] else section
        val bases = buildList {
            if (kind.sectioned) add("$SEPARATOR$prefix$SEPARATOR$infix$SEPARATOR${kind.namespace}$SEPARATOR$rest")
            if (!kind.sectioned || kind == XfShortKind.PLUGIN) add("$SEPARATOR$prefix$SEPARATOR${kind.namespace}$SEPARATOR$rest")
        }
        return bases.flatMap { base -> listOfNotNull(base, kind.suffix?.takeIf { !base.endsWith(it) }?.let { base + it }) }
    }

    fun resolve(project: Project, name: String, context: XfShortContext): PhpClass? {
        val index = PhpIndex.getInstance(project)
        for (kind in context.kinds) {
            for (candidate in candidates(name, kind, context.section)) {
                index.getClassesByFQN(candidate).firstOrNull()?.let { return it }
            }
        }
        return null
    }

    fun names(project: Project, context: XfShortContext): Map<String, String> {
        val index = PhpIndex.getInstance(project)
        val result = LinkedHashMap<String, String>()
        for (kind in context.kinds) {
            val base = kind.base ?: continue
            index.processAllSubclasses(base) { phpClass ->
                if (!phpClass.isAbstract && !XfExtensionClassFilter.isProxy(phpClass) && !XfExtensionClassFilter.isHidden(project, phpClass)) {
                    shortName(phpClass.fqn, kind, context.section)?.let { result.putIfAbsent(it, phpClass.fqn.trimStart(SEPARATOR)) }
                }
                true
            }
        }
        return result
    }

    fun shortName(fqn: String, kind: XfShortKind, section: String): String? {
        val clean = fqn.trimStart(SEPARATOR)
        val markers = buildList {
            if (kind.sectioned) add("$SEPARATOR$section$SEPARATOR${kind.namespace}$SEPARATOR")
            if (!kind.sectioned || kind == XfShortKind.PLUGIN) add("$SEPARATOR${kind.namespace}$SEPARATOR")
        }
        for (marker in markers) {
            val position = clean.indexOf(marker)
            if (position <= 0) continue
            val prefix = clean.substring(0, position)
            var rest = clean.substring(position + marker.length)
            kind.suffix?.let { if (rest.endsWith(it) && rest.length > it.length) rest = rest.removeSuffix(it) }
            if (rest.isEmpty()) continue
            return "$prefix:$rest"
        }
        return null
    }

    private fun isAssignedTo(array: PsiElement?, field: String): Boolean {
        val creation = array as? ArrayCreationExpression ?: return false
        val assignment = creation.parent as? AssignmentExpression ?: return false
        return (assignment.variable as? FieldReference)?.name == field
    }

    private fun section(element: PsiElement): String {
        val fqn = PsiTreeUtil.getParentOfType(element, PhpClass::class.java)?.fqn.orEmpty()
        return when {
            fqn.contains("\\Admin\\") -> "Admin"
            fqn.contains("\\Api\\") -> "Api"
            else -> "Pub"
        }
    }
}

class XfShortNameReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                val shortContext = XfShortNames.context(literal) ?: return PsiReference.EMPTY_ARRAY
                return arrayOf(XfShortNameReference(literal, literal.valueRange, shortContext))
            }
        })
    }
}

class XfShortNameReference(literal: StringLiteralExpression, range: TextRange, private val shortContext: XfShortContext) :
    PsiPolyVariantReferenceBase<StringLiteralExpression>(literal, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (DumbService.isDumb(element.project)) return ResolveResult.EMPTY_ARRAY
        val target = XfShortNames.resolve(element.project, element.contents, shortContext) ?: return ResolveResult.EMPTY_ARRAY
        return PsiElementResolveResult.createResults(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement =
        super.handleElementRename(XfClassRename.renameLastSegment(value, newElementName, shortContext.kinds.mapNotNull { it.suffix }))

    override fun bindToElement(element: PsiElement): PsiElement {
        val phpClass = element as? PhpClass ?: return super.bindToElement(element)
        return super.handleElementRename(XfClassRename.shortName(phpClass.fqn, shortContext))
    }
}

class XfShortNameCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(StringLiteralExpression::class.java), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val literal = parameters.position.parent as? StringLiteralExpression ?: return
                val project = literal.project
                if (DumbService.isDumb(project)) return
                val caret = parameters.offset - literal.textRange.startOffset
                val start = literal.valueRange.startOffset
                if (caret < start) return
                val typed = literal.text.substring(start, caret)
                val shortContext = shortContext(literal, typed) ?: return
                val names = result.withPrefixMatcher(typed)
                for ((name, fqn) in XfShortNames.names(project, shortContext)) {
                    names.addElement(LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Class).withTypeText(fqn, true))
                }
            }
        })
    }

    private fun shortContext(literal: StringLiteralExpression, typed: String): XfShortContext? {
        if (typed.isNotEmpty() && !TYPING.matches(typed)) return null
        return XfShortNames.context(literal, typing = true)
    }
}
