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
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

private val FINDER_METHODS = setOf("where", "order", "setdefaultorder", "with", "pluckfrom", "keyedby")
private val ENTITY_METHODS = setOf("get", "set", "ischanged", "getvalue", "getexistingvalue", "getpreviousvalue")
private const val FILTER_METHOD = "filter"
private const val WITH_METHOD = "with"
private const val FINDER_FACTORIES = "finder getfinder"
private const val INPUT_FILTERER = "\\XF\\InputFilterer"
private const val ARRAY_PREFIX = "array-"
private const val CHAIN_LIMIT = 10
private val NOT_ARRAY_ELEMENT = setOf("array", "json-array")
private val DEFAULT_FILTER_TYPES = listOf(
    "str", "string", "num", "unum", "int", "integer", "uint", "unsigned", "posint", "positive-integer",
    "float", "bool", "boolean", "array", "json-array", "timeoffset", "datetime",
)

sealed interface XfStringContext {
    data class EntityPath(val entity: String, val relationsOnly: Boolean, val withGetters: Boolean) : XfStringContext
    data object FilterType : XfStringContext
}

object XfEntityStrings {
    fun isCandidate(literal: StringLiteralExpression): Boolean {
        if (!literal.isSingleQuote && literal.text.contains('$')) return false
        val position = position(literal) ?: return false
        val name = position.call.name?.lowercase() ?: return false
        return position.index == 0 && position.arrayKind != ArrayKind.HASH_VALUE && (name in FINDER_METHODS || name in ENTITY_METHODS)
    }

    fun context(literal: StringLiteralExpression): XfStringContext? {
        if (!literal.isSingleQuote && literal.text.contains('$')) return null
        val position = position(literal) ?: return null
        val name = position.call.name?.lowercase() ?: return null
        if (name == FILTER_METHOD) {
            val type = (position.index == 1 && position.arrayKind == ArrayKind.NONE) || (position.index == 0 && position.arrayKind == ArrayKind.HASH_VALUE)
            return if (type) XfStringContext.FilterType else null
        }
        if (position.index != 0) return null
        val project = literal.project
        if (name in FINDER_METHODS && position.arrayKind != ArrayKind.HASH_VALUE) {
            if (position.arrayKind == ArrayKind.ELEMENT && name != WITH_METHOD) return null
            val entity = entityOf(project, position.call.classReference, 0) ?: return null
            return XfStringContext.EntityPath(entity, name == WITH_METHOD, withGetters = false)
        }
        if (name in ENTITY_METHODS && position.arrayKind == ArrayKind.NONE) {
            val entity = entityOf(project, position.call.classReference, 0)?.takeIf { XfEntityStructures.isEntity(project, it) } ?: return null
            return XfStringContext.EntityPath(entity, false, withGetters = true)
        }
        return null
    }

    fun resolve(project: Project, context: XfStringContext.EntityPath, path: List<String>): Pair<String, Map<String, XfEntityField>>? {
        var entity = context.entity
        for (segment in path) {
            val relation = XfEntityStructures.of(project, entity)[segment]?.takeIf { it.kind == XfEntityFieldKind.RELATION } ?: return null
            entity = relation.entity ?: return null
        }
        return entity to XfEntityStructures.of(project, entity)
    }

    fun filterTypes(project: Project): List<String> {
        val phpClass = PhpIndex.getInstance(project).getClassesByFQN(INPUT_FILTERER).firstOrNull()
        val types = phpClass?.ownFields?.filter { it.isConstant }?.mapNotNull { (it.defaultValue as? StringLiteralExpression)?.contents }?.distinct()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_FILTER_TYPES
        return types + types.filter { it !in NOT_ARRAY_ELEMENT }.map { ARRAY_PREFIX + it }
    }

    private fun entityOf(project: Project, expression: PsiElement?, depth: Int): String? {
        if (expression == null || depth > CHAIN_LIMIT) return null
        val types = (expression as? PhpTypedElement)?.type?.global(project)?.types.orEmpty().filter { it.startsWith("\\") && !it.endsWith("[]") }
        for (type in types) {
            if (XfEntityStructures.isEntity(project, type)) return type
            if (XfEntityStructures.isFinder(project, type)) XfEntityStructures.entityForFinder(project, type)?.let { return it }
        }
        return when (expression) {
            is MethodReference -> {
                val name = expression.name?.lowercase().orEmpty()
                if (name.isNotEmpty() && FINDER_FACTORIES.split(' ').contains(name)) {
                    val target = XfEntityStructures.classValue(expression.parameters.firstOrNull()) ?: return null
                    if (XfEntityStructures.isFinder(project, target)) XfEntityStructures.entityForFinder(project, target) else target
                } else {
                    entityOf(project, expression.classReference, depth + 1)
                }
            }
            is Variable -> {
                val function = PsiTreeUtil.getParentOfType(expression, Function::class.java) ?: return null
                val assignment = PsiTreeUtil.findChildrenOfType(function, AssignmentExpression::class.java)
                    .filter { (it.variable as? Variable)?.name == expression.name && it.textRange.endOffset <= expression.textRange.startOffset }
                    .maxByOrNull { it.textRange.startOffset } ?: return null
                entityOf(project, assignment.value, depth + 1)
            }
            else -> null
        }
    }

    private enum class ArrayKind { NONE, ELEMENT, HASH_VALUE }

    private data class Position(val call: MethodReference, val index: Int, val arrayKind: ArrayKind)

    private fun position(literal: StringLiteralExpression): Position? {
        val parent = literal.parent
        if (parent is ParameterList) {
            val call = parent.parent as? MethodReference ?: return null
            return Position(call, parent.parameters.indexOf(literal), ArrayKind.NONE)
        }
        val hash = parent as? ArrayHashElement ?: parent?.parent as? ArrayHashElement
        val (array, kind) = if (hash != null) {
            if (hash.value != literal) return null
            (hash.parent as? ArrayCreationExpression ?: return null) to ArrayKind.HASH_VALUE
        } else {
            (parent as? ArrayCreationExpression ?: parent?.parent as? ArrayCreationExpression ?: return null) to ArrayKind.ELEMENT
        }
        val parameters = array.parent as? ParameterList ?: return null
        val call = parameters.parent as? MethodReference ?: return null
        return Position(call, parameters.parameters.indexOf(array), kind)
    }
}

class XfEntityStringCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(StringLiteralExpression::class.java), XfEntityStringCompletion())
    }
}

private class XfEntityStringCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val literal = parameters.position.parent as? StringLiteralExpression ?: return
        val project = literal.project
        if (DumbService.isDumb(project)) return
        val stringContext = XfEntityStrings.context(literal) ?: return
        val valueStart = literal.valueRange.startOffset
        val caret = parameters.offset - literal.textRange.startOffset
        if (caret < valueStart) return
        val typed = literal.text.substring(valueStart, caret)
        when (stringContext) {
            XfStringContext.FilterType -> {
                val names = result.withPrefixMatcher(typed.substringAfterLast(','))
                for (type in XfEntityStrings.filterTypes(project)) names.addElement(LookupElementBuilder.create(type).withIcon(AllIcons.Nodes.Type))
            }
            is XfStringContext.EntityPath -> {
                val path = typed.split('.')
                val (entity, fields) = XfEntityStrings.resolve(project, stringContext, path.dropLast(1)) ?: return
                val names = result.withPrefixMatcher(path.last())
                for (field in fields.values) {
                    if (stringContext.relationsOnly && field.kind != XfEntityFieldKind.RELATION) continue
                    if (field.kind == XfEntityFieldKind.GETTER && !stringContext.withGetters) continue
                    val icon = when (field.kind) {
                        XfEntityFieldKind.COLUMN -> AllIcons.Nodes.Field
                        XfEntityFieldKind.GETTER -> AllIcons.Nodes.Method
                        XfEntityFieldKind.RELATION -> AllIcons.Nodes.Related
                    }
                    val typeText = field.type ?: field.entity?.substringAfterLast('\\')
                    names.addElement(LookupElementBuilder.create(field.name).withIcon(icon).withTypeText(typeText, true).withTailText("  " + entity.substringAfterLast('\\'), true))
                }
            }
        }
    }
}

class XfEntityStringReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                if (!XfEntityStrings.isCandidate(literal)) return PsiReference.EMPTY_ARRAY
                val value = literal.contents
                if (value.isEmpty() || value.contains(' ')) return PsiReference.EMPTY_ARRAY
                val start = literal.valueRange.startOffset
                val segments = value.split('.')
                var offset = start
                return segments.mapIndexed { index, segment ->
                    val range = TextRange(offset, offset + segment.length)
                    offset += segment.length + 1
                    XfEntityFieldReference(literal, range, segments.take(index), segment)
                }.toTypedArray()
            }
        })
    }
}

class XfEntityFieldReference(
    literal: StringLiteralExpression,
    range: TextRange,
    private val path: List<String>,
    private val name: String,
) : PsiReferenceBase<StringLiteralExpression>(literal, range, true) {
    override fun resolve(): PsiElement? {
        if (DumbService.isDumb(element.project)) return null
        val stringContext = XfEntityStrings.context(element) as? XfStringContext.EntityPath ?: return null
        return XfEntityStrings.resolve(element.project, stringContext, path)?.second?.get(name)?.element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
