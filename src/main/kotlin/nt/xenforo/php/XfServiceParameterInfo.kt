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
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

private const val CLASS_CONSTANT = "class"
private const val NAMESPACE_SEPARATOR = '\\'
private val MODIFIERS = Regex("""^(?:(?:public|protected|private|readonly)\s+)+""")

private enum class XfFactoryCall(val method: String, val kind: XfShortKind, val injected: Int) {
    SERVICE("service", XfShortKind.SERVICE, 1),
    HELPER("helper", XfShortKind.HELPER, 0),
}

class XfConstructorSignature(val className: String, val parameters: List<String>)

class XfServiceParameterInfoHandler : ParameterInfoHandler<ParameterList, XfConstructorSignature> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ParameterList? {
        val arguments = argumentsAt(context.file, context.offset) ?: return null
        val signature = signatureOf(arguments) ?: return null
        context.itemsToShow = arrayOf(signature)
        return arguments
    }

    override fun showParameterInfo(element: ParameterList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterList? =
        argumentsAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: ParameterList, context: UpdateParameterInfoContext) {
        context.setCurrentParameter(ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, PhpTokenTypes.opCOMMA) - 1)
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

    private fun signatureOf(arguments: ParameterList): XfConstructorSignature? {
        if (DumbService.isDumb(arguments.project)) return null
        val call = factoryCall(arguments) ?: return null
        val phpClass = targetClass(arguments, call) ?: return null
        val parameters = phpClass.constructor?.parameters.orEmpty().drop(call.injected).map { MODIFIERS.replace(it.text.trim(), "") }
        return XfConstructorSignature(phpClass.fqn, parameters)
    }

    private fun targetClass(arguments: ParameterList, call: XfFactoryCall): PhpClass? {
        val project = arguments.project
        return when (val first = arguments.parameters.firstOrNull()) {
            is StringLiteralExpression -> {
                val name = first.contents
                if (name.contains(':')) {
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
