package nt.xenforo.index

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

data class XfSymbol(val offset: Int, val detail: String)

internal object XfSymbolExternalizer : DataExternalizer<XfSymbol> {
    override fun save(out: DataOutput, value: XfSymbol) {
        out.writeInt(value.offset)
        IOUtil.writeUTF(out, value.detail)
    }

    override fun read(input: DataInput): XfSymbol = XfSymbol(input.readInt(), IOUtil.readUTF(input))
}

class XfSymbolIndex : FileBasedIndexExtension<String, XfSymbol>() {
    override fun getName(): ID<String, XfSymbol> = XF_SYMBOL_INDEX

    override fun getIndexer(): DataIndexer<String, XfSymbol, FileContent> =
        DataIndexer { content -> XfSymbolScanner.scan(content.file, content.contentAsText) }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<XfSymbol> = XfSymbolExternalizer

    override fun getVersion(): Int = 11

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { XfSymbolScanner.isIndexed(it) }

    override fun dependsOnFileContent(): Boolean = true

    override fun getFileTypesWithSizeLimitNotApplicable(): Collection<FileType> = listOf(XmlFileType.INSTANCE)

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true
}

val XF_SYMBOL_INDEX: ID<String, XfSymbol> = ID.create("nt.xenforo.index.XfSymbolIndex")

object XfSymbolKeys {
    fun template(type: String, title: String): String = "t:$type:$title"
    fun templatePrefix(type: String): String = "t:$type:"
    fun macro(type: String, title: String, macro: String): String = "m:$type:$title:$macro"
    fun macroPrefix(type: String, title: String): String = "m:$type:$title:"
    fun phrase(title: String): String = "p:$title"
    const val PHRASE_PREFIX: String = "p:"
    fun route(type: String, route: String): String = "r:$type:$route"
    fun routePrefix(type: String): String = "r:$type:"
    fun property(name: String): String = "s:$name"
    const val PROPERTY_PREFIX: String = "s:"
    fun option(id: String): String = "o:$id"
    const val OPTION_PREFIX: String = "o:"
    fun addOn(id: String): String = "a:$id"
    const val ADD_ON_PREFIX: String = "a:"
    fun extension(toClass: String): String = "x:$toClass"
    const val EXTENSION_PREFIX: String = "x:"
    fun modification(type: String, template: String, key: String): String = "tm:$type:$template:$key"
    fun modificationPrefix(type: String, template: String): String = "tm:$type:$template:"
    const val MODIFICATION_PREFIX: String = "tm:"
    fun view(type: String, title: String): String = "v:$type:$title"
    fun widgetPosition(id: String): String = "wp:$id"
    const val WIDGET_POSITION_PREFIX: String = "wp:"
    fun permission(group: String, id: String): String = "pm:$group:$id"
    fun permissionPrefix(group: String): String = "pm:$group:"
    fun adminPermission(id: String): String = "ap:$id"
    const val ADMIN_PERMISSION_PREFIX: String = "ap:"
    fun structureListener(entity: String, callback: String): String = "es:$entity:$callback"
    fun structureListenerPrefix(entity: String): String = "es:$entity:"
    fun container(key: String): String = "ck:$key"
    const val CONTAINER_PREFIX: String = "ck:"
}

internal fun VirtualFile.hasAncestorChain(vararg names: String): Boolean {
    var current: VirtualFile? = parent
    for (name in names) {
        if (current == null || current.name != name) return false
        current = current.parent
    }
    return true
}
