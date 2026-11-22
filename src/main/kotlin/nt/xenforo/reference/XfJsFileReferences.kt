package nt.xenforo.reference

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import nt.xenforo.commands.XfCommandSettings
import nt.xenforo.psi.XfAttribute
import nt.xenforo.psi.XfAttributeValue
import nt.xenforo.psi.XfTypes

private const val JS_TAG = "js"
private const val ADD_ON_ATTRIBUTE = "addon"
private const val JS_FOLDER = "js"
private const val SOURCE_FOLDER = "src"

private val FILE_ATTRIBUTES = setOf("src", "dev", "prod")
private val PATH = Regex("""[^,\s]+""")

object XfJsFiles {
    fun directories(host: PsiElement, addOn: String?): List<VirtualFile> {
        val file = host.containingFile?.originalFile?.virtualFile ?: return emptyList()
        val root = XfCommandSettings.getInstance(host.project).rootDirectory() ?: generateSequence(file.parent) { it.parent }
            .firstOrNull { it.findChild(JS_FOLDER)?.isDirectory == true && it.findChild(SOURCE_FOLDER)?.isDirectory == true }
            ?: return emptyList()
        val directories = ArrayList<VirtualFile>()
        if (!addOn.isNullOrBlank() && !isDynamic(addOn)) {
            root.findFileByRelativePath("$SOURCE_FOLDER/addons/$addOn/_files/$JS_FOLDER")?.takeIf { it.isDirectory }?.let(directories::add)
        }
        root.findChild(JS_FOLDER)?.takeIf { it.isDirectory }?.let(directories::add)
        return directories
    }

    fun isDynamic(value: String): Boolean = value.contains('{') || value.contains('$')
}

class XfJsFileReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val host = element as? XfAttributeValue ?: return PsiReference.EMPTY_ARRAY
        if (!host.node.getChildren(null).all { it.elementType == XfTypes.ATTR_QUOTE || it.elementType == XfTypes.ATTR_TEXT }) return PsiReference.EMPTY_ARRAY
        val attribute = host.parent as? XfAttribute ?: return PsiReference.EMPTY_ARRAY
        val header = attribute.header ?: return PsiReference.EMPTY_ARRAY
        if (!header.tagName.equals(JS_TAG, ignoreCase = true) || attribute.attributeName.lowercase() !in FILE_ATTRIBUTES) return PsiReference.EMPTY_ARRAY
        val value = host.value
        if (XfJsFiles.isDynamic(value)) return PsiReference.EMPTY_ARRAY
        val directories = XfJsFiles.directories(host, header.findAttribute(ADD_ON_ATTRIBUTE)?.attributeValue?.value)
        if (directories.isEmpty()) return PsiReference.EMPTY_ARRAY
        val start = host.valueRange.startOffset
        val references = ArrayList<PsiReference>()
        for (match in PATH.findAll(value)) {
            references += XfJsFileReferenceSet(match.value, host, start + match.range.first, directories).allReferences
        }
        return references.toTypedArray()
    }
}

private class XfJsFileReferenceSet(path: String, host: PsiElement, start: Int, private val directories: List<VirtualFile>) :
    FileReferenceSet(path, host, start, null, true) {

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
        val manager = PsiManager.getInstance(element.project)
        return directories.mapNotNull { manager.findDirectory(it) }
    }

    override fun isAbsolutePathReference(): Boolean = false

    override fun isSoft(): Boolean = true
}
