package nt.xenforo.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

private const val JSON_FILE = "addon.json"
private const val ADD_ONS_FOLDER = "addons"
private const val SOURCE_FOLDER = "src"
private const val RELEASES_FOLDER = "_releases"
private const val ID_SEPARATOR = '/'

private val VERSION_ID = Regex(""""version_id"\s*:\s*(\d+)""")
private val VERSION_STRING = Regex(""""version_string"\s*:\s*"((?:[^"\\]|\\.)*)"""")

data class XfAddOn(val id: String, val directory: VirtualFile, val root: VirtualFile) {
    val json: VirtualFile?
        get() = directory.findChild(JSON_FILE)

    val releasePrefix: String
        get() = id.replace(ID_SEPARATOR, '-') + "-"

    val releases: VirtualFile?
        get() = directory.findChild(RELEASES_FOLDER)

    val script: VirtualFile?
        get() = root.findChild(XfAddOns.SCRIPT)

    fun version(): XfAddOnVersion? {
        val text = json?.let { VfsUtilCore.loadText(it) } ?: return null
        val id = VERSION_ID.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val string = VERSION_STRING.find(text)?.groupValues?.get(1).orEmpty()
        return XfAddOnVersion(id, string)
    }
}

data class XfAddOnVersion(val id: Int, val string: String)

object XfAddOns {
    const val SCRIPT = "cmd.php"

    fun find(project: Project, file: VirtualFile?): XfAddOn? = find(file, XfCommandSettings.getInstance(project).rootDirectory())

    fun find(file: VirtualFile?, configured: VirtualFile?): XfAddOn? {
        val start = file?.let { if (it.isDirectory) it else it.parent } ?: return null
        val directory = generateSequence(start) { it.parent }.firstOrNull { it.findChild(JSON_FILE)?.isDirectory == false } ?: return null
        val addOns = generateSequence(directory.parent) { it.parent }.firstOrNull { it.name == ADD_ONS_FOLDER && it.parent?.name == SOURCE_FOLDER }
        val id = addOns?.let { VfsUtilCore.getRelativePath(directory, it, ID_SEPARATOR) }
        if (configured == null) {
            val root = addOns?.parent?.parent ?: return null
            return id?.let { XfAddOn(it, directory, root) }
        }
        val resolved = id?.takeIf { isAddOn(configured, it) } ?: candidates(directory).firstOrNull { isAddOn(configured, it) } ?: return null
        return XfAddOn(resolved, directory, configured)
    }

    private fun candidates(directory: VirtualFile): List<String> =
        listOfNotNull(directory.parent?.let { it.name + ID_SEPARATOR + directory.name }, directory.name)

    private fun isAddOn(root: VirtualFile, id: String): Boolean =
        root.findFileByRelativePath("$SOURCE_FOLDER/$ADD_ONS_FOLDER/$id/$JSON_FILE") != null
}
