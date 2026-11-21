package nt.xenforo.model

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import nt.xenforo.index.XfCallableKind
import nt.xenforo.index.XfCustomCallables
import nt.xenforo.index.XfSymbolLocation
import nt.xenforo.meta.XfBuiltins

object XfCallables {
    private const val TEMPLATER = "\\XF\\Template\\Templater"
    private const val THIS = "this"

    fun builtins(kind: XfCallableKind): Map<String, String> = when (kind) {
        XfCallableKind.FUNCTION -> XfBuiltins.FUNCTIONS
        XfCallableKind.FILTER -> XfBuiltins.FILTERS
        XfCallableKind.TEST -> XfBuiltins.TESTS
    }

    fun isBuiltin(kind: XfCallableKind, name: String): Boolean = builtins(kind).containsKey(name.lowercase())

    fun customNames(project: Project, kind: XfCallableKind): List<String> =
        XfCustomCallables.names(project, kind).filter { !isBuiltin(kind, it) }

    fun registration(project: Project, kind: XfCallableKind, name: String): XfSymbolLocation? =
        if (isBuiltin(kind, name)) null else XfCustomCallables.locations(project, kind, name).firstOrNull()

    fun isKnown(project: Project, kind: XfCallableKind, name: String): Boolean =
        isBuiltin(kind, name) || registration(project, kind, name) != null

    fun parameters(project: Project, kind: XfCallableKind, name: String): List<String>? {
        builtins(kind)[name.lowercase()]?.let { return splitParameters(it) }
        val location = registration(project, kind, name) ?: return null
        val function = customFunction(project, location) ?: return null
        return function.parameters.drop(kind.skippedParameters).map(XfPhpMembers::parameterText)
    }

    fun target(project: Project, kind: XfCallableKind, name: String): PsiElement? {
        if (DumbService.isDumb(project)) return null
        if (isBuiltin(kind, name)) return templaterMethod(project, kind, name)
        val location = registration(project, kind, name) ?: return null
        return customFunction(project, location) ?: registrationElement(project, location)
    }

    fun splitParameters(signature: String): List<String> {
        if (signature.isBlank()) return emptyList()
        val result = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (char in signature) {
            when {
                quote != null -> {
                    if (char == quote) quote = null
                    current.append(char)
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }
                char == '[' || char == '(' || char == '{' -> {
                    depth++
                    current.append(char)
                }
                char == ']' || char == ')' || char == '}' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    result += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result
    }

    private fun templaterMethod(project: Project, kind: XfCallableKind, name: String): PsiElement? {
        val field = when (kind) {
            XfCallableKind.FUNCTION -> "defaultFunctions"
            XfCallableKind.FILTER -> "defaultFilters"
            XfCallableKind.TEST -> "defaultTests"
        }
        val templater = PhpIndex.getInstance(project).getClassesByFQN(TEMPLATER).firstOrNull() ?: return null
        val array = templater.findFieldByName(field, false)?.defaultValue as? ArrayCreationExpression ?: return null
        val method = array.hashElements
            .firstOrNull { (it.key as? StringLiteralExpression)?.contents.equals(name, ignoreCase = true) }
            ?.value as? StringLiteralExpression ?: return null
        return templater.findMethodByName(method.contents)
    }

    private fun registrationElement(project: Project, location: XfSymbolLocation): PsiElement? {
        val file = psiFile(project, location) ?: return null
        var element = file.findElementAt(location.symbol.offset) ?: return null
        while (true) {
            val parent = element.parent ?: break
            if (parent is PsiFile || parent is ParameterList || parent is ArrayHashElement) break
            if (parent.textRange.startOffset != location.symbol.offset) break
            element = parent
        }
        return element
    }

    private fun customFunction(project: Project, location: XfSymbolLocation): Function? {
        val expression = registrationElement(project, location) ?: return null
        PsiTreeUtil.findChildOfType(expression, Function::class.java, false)?.takeIf { it.isClosure }?.let { return it }
        return when (expression) {
            is ArrayCreationExpression -> arrayCallable(project, expression)
            is StringLiteralExpression -> stringCallable(project, expression)
            else -> null
        }
    }

    private fun arrayCallable(project: Project, array: ArrayCreationExpression): Function? {
        val values = PsiTreeUtil.getChildrenOfTypeAsList(array, PhpPsiElement::class.java).map { it.firstPsiChild ?: it }
        if (values.size != 2) return null
        val method = (values[1] as? StringLiteralExpression)?.contents ?: return null
        return classesOf(project, values[0]).firstNotNullOfOrNull { it.findMethodByName(method) }
    }

    private fun stringCallable(project: Project, literal: StringLiteralExpression): Function? {
        val contents = literal.contents
        if (contents.contains("::")) {
            val className = "\\" + contents.substringBefore("::").trimStart('\\')
            return PhpIndex.getInstance(project).getClassesByFQN(className).firstNotNullOfOrNull { it.findMethodByName(contents.substringAfter("::")) }
        }
        val owner = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java)
        owner?.findMethodByName(contents)?.let { return it }
        return PhpIndex.getInstance(project).getClassesByFQN(TEMPLATER).firstNotNullOfOrNull { it.findMethodByName(contents) }
    }

    private fun classesOf(project: Project, element: PsiElement): List<PhpClass> {
        val index = PhpIndex.getInstance(project)
        return when (element) {
            is ClassConstantReference -> (element.classReference as? ClassReference)?.fqn?.let { index.getClassesByFQN(it).toList() }.orEmpty()
            is StringLiteralExpression -> index.getClassesByFQN("\\" + element.contents.trimStart('\\')).toList()
            is Variable -> if (element.name == THIS) {
                listOfNotNull(PsiTreeUtil.getParentOfType(element, PhpClass::class.java))
            } else {
                typedClasses(project, element)
            }
            is PhpTypedElement -> typedClasses(project, element)
            else -> emptyList()
        }
    }

    private fun typedClasses(project: Project, element: PhpTypedElement): List<PhpClass> {
        val index = PhpIndex.getInstance(project)
        return element.type.global(project).filterPrimitives().types.flatMap { index.getClassesByFQN(it) }
    }

    private fun psiFile(project: Project, location: XfSymbolLocation): PsiFile? =
        if (location.file.isValid) PsiManager.getInstance(project).findFile(location.file) else null
}
