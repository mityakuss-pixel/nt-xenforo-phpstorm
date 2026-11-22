package nt.xenforo.commands

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import nt.xenforo.index.XfDataTracker
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbols
import java.nio.charset.StandardCharsets

private const val CORE_ADD_ON = "XF"
private const val OUTPUT_FOLDER = "_output"
private const val MAKE_COMMANDS = "src/XF/Cli/Command/Make"
private const val LAST_ADD_ON_KEY = "nt.xenforo.lastAddOn"

object XfDevAddOns {
    fun list(project: Project): List<XfAddOn> {
        val result = LinkedHashMap<String, XfAddOn>()
        for (key in XfDataTracker.keys(project, XfSymbolKeys.ADD_ON_PREFIX).sorted()) {
            for (location in XfSymbols.locations(project, key)) {
                val addOn = XfAddOns.find(project, location.file) ?: continue
                if (addOn.id == CORE_ADD_ON || addOn.directory.findChild(OUTPUT_FOLDER)?.isDirectory != true) continue
                result.putIfAbsent(addOn.id, addOn)
            }
        }
        return result.values.toList()
    }

    fun preferred(project: Project, addOns: List<XfAddOn>, file: VirtualFile?): XfAddOn? {
        XfAddOns.find(project, file)?.let { current -> addOns.firstOrNull { it.id == current.id }?.let { return it } }
        val last = PropertiesComponent.getInstance(project).getValue(LAST_ADD_ON_KEY)
        return addOns.firstOrNull { it.id == last } ?: addOns.firstOrNull()
    }

    fun remember(project: Project, addOn: XfAddOn) {
        PropertiesComponent.getInstance(project).setValue(LAST_ADD_ON_KEY, addOn.id)
    }
}

object XfMake {
    const val EXTENSION = "Extension"
    const val PHRASE = "Phrase"
    const val TEMPLATE_MODIFICATION = "TemplateModification"

    fun isAvailable(addOn: XfAddOn, command: String): Boolean =
        addOn.root.findFileByRelativePath("$MAKE_COMMANDS/${command}MakeCommand.php") != null
}

object XfOutputFiles {
    fun write(directory: VirtualFile, relativePath: String, text: String): VirtualFile = WriteAction.computeAndWait<VirtualFile, Exception> {
        val parentPath = relativePath.substringBeforeLast('/', "")
        val parent = if (parentPath.isEmpty()) directory else VfsUtil.createDirectoryIfMissing(directory, parentPath)
            ?: error(relativePath)
        val name = relativePath.substringAfterLast('/')
        val file = parent.findChild(name) ?: parent.createChildData(XfOutputFiles, name)
        file.setBinaryContent(text.toByteArray(StandardCharsets.UTF_8))
        file
    }

    fun open(project: Project, file: VirtualFile) {
        OpenFileDescriptor(project, file).navigate(true)
    }

    fun json(fields: List<Pair<String, Any>>): String = fields.joinToString(",\n", "{\n", "\n}") { (name, value) ->
        "    \"$name\": " + if (value is String) jsonString(value) else value.toString()
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char.code < 0x20 || char.code > 0x7e -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> append(char)
            }
        }
        append('"')
    }
}
