package nt.xenforo.editor

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nt.xenforo.index.XfCallableKind
import nt.xenforo.lang.XfLanguage
import nt.xenforo.model.XfAccess
import nt.xenforo.model.XfCallables
import nt.xenforo.psi.XfAccessExpression
import nt.xenforo.psi.XfArguments
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfTypes

class XfSignature(val parameters: List<String>)

class XfParameterInfoHandler : ParameterInfoHandler<XfArguments, XfSignature> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): XfArguments? {
        val arguments = argumentsAt(context.file, context.offset) ?: return null
        val signature = signatureOf(arguments) ?: return null
        context.itemsToShow = arrayOf(signature)
        return arguments
    }

    override fun showParameterInfo(element: XfArguments, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): XfArguments? =
        argumentsAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: XfArguments, context: UpdateParameterInfoContext) {
        context.setCurrentParameter(ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, XfTypes.COMMA))
    }

    override fun updateUI(signature: XfSignature, context: ParameterInfoUIContext) {
        val parameters = signature.parameters
        if (parameters.isEmpty()) {
            context.setupUIComponentPresentation(
                CodeInsightBundle.message("parameter.info.no.parameters"), -1, -1, false, false, false, context.defaultParameterColor,
            )
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

    private fun argumentsAt(file: PsiFile, offset: Int): XfArguments? {
        val element = file.viewProvider.findElementAt(offset, XfLanguage) ?: return null
        var arguments = PsiTreeUtil.getParentOfType(element, XfArguments::class.java, false)
        while (arguments != null) {
            val range = arguments.textRange
            val closed = arguments.lastChild?.node?.elementType == XfTypes.RPAREN
            if (offset > range.startOffset && (offset < range.endOffset || !closed)) return arguments
            arguments = PsiTreeUtil.getParentOfType(arguments, XfArguments::class.java, true)
        }
        return null
    }

    private fun signatureOf(arguments: XfArguments): XfSignature? {
        val project = arguments.project
        val parameters = when (val owner = arguments.parent) {
            is XfFunctionCallExpr -> XfCallables.parameters(project, XfCallableKind.FUNCTION, owner.callName)
            is XfFilter -> XfCallables.parameters(project, XfCallableKind.FILTER, owner.callName)
            is XfAccessExpression -> {
                val name = PsiTreeUtil.skipWhitespacesBackward(arguments) ?: return null
                val index = XfAccess.segmentIndex(owner, name)
                if (index < 0) return null
                XfAccess.member(project, owner, index)?.parameters?.let(XfCallables::splitParameters)
            }
            else -> null
        } ?: return null
        return XfSignature(parameters)
    }
}
