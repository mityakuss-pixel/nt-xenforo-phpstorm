package nt.xenforo.model

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import nt.xenforo.index.XfSymbolLocation
import nt.xenforo.index.XfTagRegistrations
import nt.xenforo.meta.XfTagInfo
import nt.xenforo.meta.XfTags

private const val COMPILER_CLASS = "\\XF\\Template\\Compiler"
private const val TAG_NAMESPACE = "\\XF\\Template\\Compiler\\Tag\\"
private const val ABSTRACT_TAG = "\\XF\\Template\\Compiler\\Tag\\AbstractTag"
private const val DEFAULT_TAGS_FIELD = "defaultTags"
private const val ROW_OPTIONS_FIELD = "defaultRowOptions"
private const val ROW_SUFFIX = "row"
private const val ASSERT_ATTRIBUTE = "assertAttribute"
private const val ATTRIBUTES = "attributes"
private const val CHILDREN = "children"
private const val TAG_VARIABLE = "tag"

class XfTagDefinition(val name: String, val classFqn: String, val registration: XfSymbolLocation?)

object XfTagCatalog {
    fun all(project: Project): Collection<XfTagInfo> {
        val live = live(project) ?: return XfTags.ALL
        val result = LinkedHashMap<String, XfTagInfo>()
        for (definition in live.values) {
            result[definition.name] = XfTags.get(definition.name) ?: derived(project, definition)
        }
        for (info in XfTags.ALL) {
            if (info.name !in result && XfTags.isStructural(info.name)) result[info.name] = info
        }
        return result.values
    }

    fun get(project: Project, name: String): XfTagInfo? {
        val key = name.lowercase()
        val live = live(project) ?: return XfTags.get(key)
        val definition = live[key] ?: return XfTags.get(key)?.takeIf { XfTags.isStructural(key) }
        val known = XfTags.get(key)
        val derived = derived(project, definition)
        if (known == null) return derived
        val attributes = (known.attributes + derived.attributes.filter { it !in known.attributes }).distinct()
        return XfTagInfo(known.name, attributes, known.required, known.block, known.nested)
    }

    fun isKnown(project: Project, name: String): Boolean = get(project, name) != null

    fun definition(project: Project, name: String): XfTagDefinition? = live(project)?.get(name.lowercase())

    fun tagClass(project: Project, name: String): PhpClass? {
        val definition = definition(project, name) ?: return null
        return PhpIndex.getInstance(project).getClassesByFQN(definition.classFqn).firstOrNull()
    }

    private fun live(project: Project): Map<String, XfTagDefinition>? {
        if (DumbService.isDumb(project)) return null
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(computeLive(project), PsiModificationTracker.getInstance(project))
        }
    }

    private fun computeLive(project: Project): Map<String, XfTagDefinition>? {
        val compiler = PhpIndex.getInstance(project).getClassesByFQN(COMPILER_CLASS).firstOrNull() ?: return null
        val defaults = compiler.findFieldByName(DEFAULT_TAGS_FIELD, false)?.defaultValue as? ArrayCreationExpression ?: return null
        val result = LinkedHashMap<String, XfTagDefinition>()
        for (element in defaults.hashElements) {
            val name = (element.key as? StringLiteralExpression)?.contents?.lowercase() ?: continue
            val fqn = classFqn(element.value ?: continue) ?: continue
            result[name] = XfTagDefinition(name, fqn, null)
        }
        for (name in XfTagRegistrations.names(project)) {
            val location = XfTagRegistrations.locations(project, name).firstOrNull() ?: continue
            val fqn = registrationClass(project, location) ?: continue
            result[name.lowercase()] = XfTagDefinition(name.lowercase(), fqn, location)
        }
        return result
    }

    private fun registrationClass(project: Project, location: XfSymbolLocation): String? {
        if (!location.file.isValid) return null
        val file = PsiManager.getInstance(project).findFile(location.file) ?: return null
        var element: PsiElement = file.findElementAt(location.symbol.offset) ?: return null
        while (true) {
            val parent = element.parent ?: break
            if (parent is PsiFile || parent is ParameterList || parent is ArrayHashElement) break
            if (parent.textRange.startOffset != location.symbol.offset) break
            element = parent
        }
        val expression = PsiTreeUtil.findChildOfAnyType(
            element, false, StringLiteralExpression::class.java, ClassConstantReference::class.java, NewExpression::class.java,
        ) ?: return null
        return classFqn(expression)
    }

    private fun classFqn(element: PsiElement): String? = when (element) {
        is StringLiteralExpression -> element.contents.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("\\")) it else TAG_NAMESPACE + it }
        is ClassConstantReference -> (element.classReference as? ClassReference)?.fqn
        is NewExpression -> element.classReference?.fqn
        else -> null
    }

    private fun derived(project: Project, definition: XfTagDefinition): XfTagInfo {
        val phpClass = PhpIndex.getInstance(project).getClassesByFQN(definition.classFqn).firstOrNull()
            ?: return XfTagInfo(definition.name, emptyList(), emptyList(), block = false, nested = false)
        val analysis = CachedValuesManager.getCachedValue(phpClass) {
            CachedValueProvider.Result.create(analyze(phpClass), PsiModificationTracker.getInstance(project))
        }
        val attributes = analysis.required + analysis.attributes + if (definition.name.endsWith(ROW_SUFFIX)) analysis.rowAttributes else emptyList()
        return XfTagInfo(definition.name, attributes.distinct(), analysis.required, analysis.children, nested = false)
    }

    private class XfTagAnalysis(val required: List<String>, val attributes: List<String>, val rowAttributes: List<String>, val children: Boolean)

    private fun analyze(phpClass: PhpClass): XfTagAnalysis {
        val required = LinkedHashSet<String>()
        val attributes = LinkedHashSet<String>()
        val rowAttributes = LinkedHashSet<String>()
        var children = false
        for (current in hierarchy(phpClass)) {
            val rowOptions = current.ownFields.firstOrNull { it.name == ROW_OPTIONS_FIELD }?.defaultValue as? ArrayCreationExpression
            rowOptions?.hashElements?.mapNotNullTo(rowAttributes) { (it.key as? StringLiteralExpression)?.contents }
            PsiTreeUtil.processElements(current) { element ->
                when (element) {
                    is MethodReference -> if (element.name == ASSERT_ATTRIBUTE && rootVariable(element.classReference) == TAG_VARIABLE) {
                        (element.parameters.firstOrNull() as? StringLiteralExpression)?.contents?.let { required += it }
                    }
                    is ArrayAccessExpression -> if (isTagAttributes(element.value) && !isAssignmentTarget(element)) {
                        (element.index?.value as? StringLiteralExpression)?.contents?.let { attributes += it }
                    }
                    is FieldReference -> if (element.name == CHILDREN && rootVariable(element.classReference) == TAG_VARIABLE) children = true
                }
                true
            }
        }
        return XfTagAnalysis(required.toList(), attributes.toList(), rowAttributes.toList(), children)
    }

    private fun isTagAttributes(expression: PsiElement?): Boolean = when (expression) {
        is FieldReference -> expression.name == ATTRIBUTES && rootVariable(expression.classReference) == TAG_VARIABLE
        is Variable -> expression.name == ATTRIBUTES
        else -> false
    }

    private fun isAssignmentTarget(element: PsiElement): Boolean = (element.parent as? AssignmentExpression)?.variable == element

    private fun rootVariable(element: PsiElement?): String? = when (element) {
        is Variable -> element.name
        is MethodReference -> rootVariable(element.classReference)
        is FieldReference -> rootVariable(element.classReference)
        else -> null
    }

    private fun hierarchy(phpClass: PhpClass): List<PhpClass> {
        val result = ArrayList<PhpClass>()
        var current: PhpClass? = phpClass
        while (current != null && current.fqn != ABSTRACT_TAG && current !in result) {
            result += current
            current = current.superClass
        }
        return result
    }
}
