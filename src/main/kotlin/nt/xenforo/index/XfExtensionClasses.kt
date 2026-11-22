package nt.xenforo.index

import com.intellij.openapi.vfs.VirtualFile

private const val PHP_EXTENSION = "php"
private const val ADD_ONS_FOLDER = "addons"
private const val SOURCE_FOLDER = "src"
private const val CORE_ADD_ON = "XF"
private const val PROXY_MARKER = "XFCP_"

private val NAMESPACE = Regex("""(?m)^\s*namespace\s+([\w\\]+)\s*[;{]""")
private val EXTENSION_CLASS = Regex("""\bclass\s+(\w+)\s+extends\s+XFCP_(\w+)\b""")
private const val VIEW_MARKER = "->view("
private const val ADMIN_PATH = "/Admin/"
private const val ADMIN_TYPE = "admin"
private const val PUBLIC_TYPE = "public"
private val VIEW_CALL = Regex("""->view\(\s*(?:'[^']*'|"[^"]*"|[\w\\]+::class|\$\w+|null)?\s*,\s*['"]((?:public:|admin:|email:)?[\w.-]+)['"]""")

object XfExtensionClasses {
    fun isIndexed(file: VirtualFile): Boolean {
        if (!file.extension.equals(PHP_EXTENSION, ignoreCase = true)) return false
        var child = file
        var current = file.parent
        while (current != null) {
            if (current.name == ADD_ONS_FOLDER && current.parent?.name == SOURCE_FOLDER) return child != file && child.name != CORE_ADD_ON
            if (current.name == SOURCE_FOLDER && child.name == CORE_ADD_ON && child != file) return true
            child = current
            current = current.parent
        }
        return false
    }

    fun scanViews(file: VirtualFile, text: CharSequence, result: MutableMap<String, XfSymbol>) {
        if (!text.contains(VIEW_MARKER)) return
        val defaultType = if (file.path.contains(ADMIN_PATH)) ADMIN_TYPE else PUBLIC_TYPE
        for (match in VIEW_CALL.findAll(text)) {
            val name = match.groupValues[1]
            val colon = name.indexOf(':')
            val type = if (colon > 0) name.substring(0, colon) else defaultType
            val title = if (colon > 0) name.substring(colon + 1) else name
            result.putIfAbsent(XfSymbolKeys.view(type, title), XfSymbol(match.range.first, ""))
        }
    }

    fun scan(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        if (!text.contains(PROXY_MARKER)) return
        val namespace = NAMESPACE.find(text)?.groupValues?.get(1).orEmpty()
        for (match in EXTENSION_CLASS.findAll(text)) {
            val name = match.groupValues[1]
            if (name != match.groupValues[2]) continue
            val className = if (namespace.isEmpty()) name else "$namespace\\$name"
            result.putIfAbsent(XfSymbolKeys.extension(className), XfSymbol(match.range.first, ""))
        }
    }
}
