package nt.xenforo.php

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

private const val CLASS_CONSTANT = "class"
private const val NAMESPACE_SEPARATOR = '\\'
private const val CORE_CLASS = "\\XF"
private const val CORE_NAMESPACE = "\\XF\\"
private val MODIFIERS = Regex("""^(?:(?:public|protected|private|readonly)\s+)+""")

private enum class XfFactoryCall(val method: String, val kind: XfShortKind, val injected: Int) {
    SERVICE("service", XfShortKind.SERVICE, 1),
    HELPER("helper", XfShortKind.HELPER, 0),
}

class XfConstructorSignature {
    var className: String? = null
        private set
    var parameters: List<String> = emptyList()
        private set
    var shift: Int = 0
        private set

    internal fun refresh(className: String?, parameters: List<String>, shift: Int) {
        this.className = className
        this.parameters = parameters
        this.shift = shift
    }
}

class XfServiceParameterInfoHandler : ParameterInfoHandler<ParameterList, XfConstructorSignature> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ParameterList? {
        val arguments = argumentsAt(context.file, context.offset) ?: return null
        val signature = XfConstructorSignature()
        if (!refresh(signature, arguments)) return null
        context.itemsToShow = arrayOf(signature)
        return arguments
    }

    override fun showParameterInfo(element: ParameterList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterList? =
        argumentsAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: ParameterList, context: UpdateParameterInfoContext) {
        val signature = context.objectsToView.firstOrNull() as? XfConstructorSignature
        if (signature != null) refresh(signature, parameterOwner)
        val index = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, PhpTokenTypes.opCOMMA)
        context.setCurrentParameter(index - (signature?.shift ?: 1))
    }

    override fun updateUI(signature: XfConstructorSignature, context: ParameterInfoUIContext) {
        val parameters = signature.parameters
        if (parameters.isEmpty()) {
            context.setupUIComponentPresentation(CodeInsightBundle.message("parameter.info.no.parameters"), -1, -1, false, false, false, context.defaultParameterColor)
            return
        }
        val current = context.currentParameterIndex.let { index ->
            if (index >= parameters.size && parameters.last().contains("...")) parameters.lastIndex else index
        }
        val text = StringBuilder()
        var start = -1
        var end = -1
        parameters.forEachIndexed { index, parameter ->
            if (index > 0) text.append(", ")
            if (index == current) start = text.length
            text.append(parameter)
            if (index == current) end = text.length
        }
        context.setupUIComponentPresentation(text.toString(), start, end, false, false, false, context.defaultParameterColor)
    }

    private fun refresh(signature: XfConstructorSignature, arguments: ParameterList): Boolean {
        if (DumbService.isDumb(arguments.project)) return false
        val call = factoryCall(arguments) ?: return false
        val phpClass = targetClass(arguments, call)
        if (phpClass != null) {
            signature.refresh(phpClass.fqn, phpClass.constructor?.parameters.orEmpty().drop(call.injected).map(::presentable), 1)
            return true
        }
        val method = coreMethod(arguments) ?: return false
        signature.refresh(null, method.parameters.map(::presentable), 0)
        return true
    }

    private fun presentable(parameter: Parameter): String = MODIFIERS.replace(parameter.text.trim(), "")

    private fun argumentsAt(file: PsiFile, offset: Int): ParameterList? {
        var call = PsiTreeUtil.getParentOfType(file.findElementAt(offset), FunctionReference::class.java, false)
        while (call != null) {
            val arguments = call.parameterList
            val open = call.node.findChildByType(PhpTokenTypes.chLPAREN)?.textRange?.endOffset
            val close = call.node.findChildByType(PhpTokenTypes.chRPAREN)?.textRange?.startOffset ?: Int.MAX_VALUE
            if (arguments != null && open != null && offset in open..close && factoryCall(arguments) != null) return arguments
            call = PsiTreeUtil.getParentOfType(call, FunctionReference::class.java, true)
        }
        return null
    }

    private fun factoryCall(arguments: ParameterList): XfFactoryCall? {
        val name = (arguments.parent as? FunctionReference)?.name ?: return null
        return XfFactoryCall.entries.firstOrNull { it.method.equals(name, ignoreCase = true) }
    }

    private fun coreMethod(arguments: ParameterList): Method? {
        val method = (arguments.parent as? MethodReference)?.resolve() as? Method ?: return null
        val owner = method.containingClass?.fqn ?: return null
        return method.takeIf { owner == CORE_CLASS || owner.startsWith(CORE_NAMESPACE) }
    }

    private fun targetClass(arguments: ParameterList, call: XfFactoryCall): PhpClass? {
        val project = arguments.project
        return when (val first = arguments.parameters.firstOrNull()) {
            is StringLiteralExpression -> {
                val name = first.contents
                if (name.isEmpty()) {
                    null
                } else if (name.contains(':')) {
                    XfShortNames.resolve(project, name, XfShortContext(listOf(call.kind), "Pub"))
                } else {
                    PhpIndex.getInstance(project).getClassesByFQN(NAMESPACE_SEPARATOR + name.trimStart(NAMESPACE_SEPARATOR)).firstOrNull()
                }
            }
            is ClassConstantReference -> {
                if (first.name != CLASS_CONSTANT) return null
                (first.classReference as? ClassReference)?.resolve() as? PhpClass
            }
            else -> null
        }
    }
}
