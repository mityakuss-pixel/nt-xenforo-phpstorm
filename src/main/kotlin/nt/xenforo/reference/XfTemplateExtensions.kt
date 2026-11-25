package nt.xenforo.reference

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.php.XfClassRename
import nt.xenforo.XfBundle
import nt.xenforo.index.XfSymbolTarget
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.modification.XfModifications
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfFile
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfTagElement
import nt.xenforo.psi.XfTypes

private const val EXTENSION_TAG = "extension"
private const val EXTENSION_TYPE = "symbol.type.extension.template"
private const val PARENT_DEPTH = 5
private val EXTENSION_DEFINITION = Regex("""<xf:extension\b[^>]*?\bid="([\w-]+)"""")
private val EXTENDS = Regex("""<xf:extends\b[^>]*?\btemplate="([^"{$]+)"""")

object XfTemplateExtensions {
    fun targets(host: XfReferenceHost, name: String): List<PsiElement> {
        val project = host.project
        val file = (host.containingFile.originalFile as? XfFile) ?: host.containingFile as? XfFile ?: return emptyList()
        val virtualFile = file.virtualFile ?: return emptyList()
        val tag = PsiTreeUtil.getParentOfType(host, XfTagElement::class.java)
        val text = file.viewProvider.contents
        if (tag?.tagName?.equals(EXTENSION_TAG, ignoreCase = true) != true) {
            definition(text, name, tag?.textRange?.startOffset)?.let { return listOf(XfSymbolTarget(project, virtualFile, it, name, XfBundle.message(EXTENSION_TYPE))) }
        }
        if (DumbService.isDumb(project)) return emptyList()
        for ((parentFile, range) in parents(file, text)) {
            definition(XfModifications.text(parentFile), name, null, range)?.let { return listOf(XfSymbolTarget(project, parentFile, it, name, XfBundle.message(EXTENSION_TYPE))) }
        }
        return emptyList()
    }

    fun names(host: XfReferenceHost): List<String> {
        val file = host.containingFile.originalFile as? XfFile ?: host.containingFile as? XfFile ?: return emptyList()
        val result = LinkedHashSet<String>()
        val text = file.viewProvider.contents
        EXTENSION_DEFINITION.findAll(text).forEach { result += it.groupValues[1] }
        if (!DumbService.isDumb(file.project)) {
            for ((parentFile, range) in parents(file, text)) {
                EXTENSION_DEFINITION.findAll(XfModifications.text(parentFile).subSequence(range.startOffset, range.endOffset)).forEach { result += it.groupValues[1] }
            }
        }
        return result.toList()
    }

    private fun definition(text: CharSequence, name: String, excluded: Int?, range: TextRange = TextRange(0, text.length)): Int? {
        val body = text.subSequence(range.startOffset, range.endOffset)
        return EXTENSION_DEFINITION.findAll(body)
            .firstOrNull { it.groupValues[1] == name && range.startOffset + it.range.first != excluded }
            ?.let { range.startOffset + it.range.first }
    }

    private fun parents(file: XfFile, text: CharSequence): List<Pair<VirtualFile, TextRange>> {
        val result = ArrayList<Pair<VirtualFile, TextRange>>()
        var type = file.templateType
        var current = EXTENDS.find(text)?.groupValues?.get(1)
        var depth = 0
        while (current != null && depth++ < PARENT_DEPTH) {
            val colon = current.indexOf(':')
            if (colon > 0 && current.substring(0, colon) in XfTemplateFiles.TEMPLATE_TYPES) {
                type = current.substring(0, colon)
                current = current.substring(colon + 1)
            }
            val source = XfModifications.templateSource(file.project, type, current) ?: break
            result += source.file to source.range
            current = EXTENDS.find(source.body)?.groupValues?.get(1)
        }
        return result
    }
}

class XfCallbackReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val host = element as? XfAttributeValue ?: return PsiReference.EMPTY_ARRAY
        if (!host.node.getChildren(null).all { it.elementType == XfTypes.ATTR_QUOTE || it.elementType == XfTypes.ATTR_TEXT }) return PsiReference.EMPTY_ARRAY
        val attribute = host.parent as? XfAttribute ?: return PsiReference.EMPTY_ARRAY
        val header = attribute.header ?: return PsiReference.EMPTY_ARRAY
        if (!header.tagName.equals("callback", ignoreCase = true) || host.value.isBlank()) return PsiReference.EMPTY_ARRAY
        val name = attribute.attributeName.lowercase()
        if (name != "class" && name != "method") return PsiReference.EMPTY_ARRAY
        val classValue = header.findAttribute("class")?.attributeValue?.value.orEmpty()
        return arrayOf(XfCallbackReference(host, host.valueRange, classValue, if (name == "method") host.value else null))
    }
}

class XfCallbackReference(host: XfAttributeValue, range: TextRange, private val className: String, private val method: String?) :
    PsiReferenceBase<XfAttributeValue>(host, range, true) {
    override fun resolve(): PsiElement? {
        if (DumbService.isDumb(element.project) || className.isBlank()) return null
        val phpClass = PhpIndex.getInstance(element.project).getClassesByFQN("\\" + className.trimStart('\\')).firstOrNull() ?: return null
        return if (method == null) phpClass else phpClass.findMethodByName(method)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement =
        super.handleElementRename(if (method == null) XfClassRename.renameLastSegment(value, newElementName) else newElementName)

    override fun bindToElement(element: PsiElement): PsiElement {
        if (method != null || element !is PhpClass) return super.bindToElement(element)
        return super.handleElementRename(element.fqn.trimStart('\\'))
    }
}
