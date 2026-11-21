package nt.xenforo.lang

import com.intellij.openapi.vfs.VirtualFile

object XfTemplateFiles {
    const val DEFAULT_TYPE: String = "public"

    val TEMPLATE_TYPES: List<String> = listOf("public", "admin", "email")

    fun isOutputTemplate(file: VirtualFile): Boolean {
        if (!file.name.endsWith(".html", ignoreCase = true)) return false
        val typeDir = file.parent ?: return false
        if (typeDir.name !in TEMPLATE_TYPES) return false
        val templatesDir = typeDir.parent ?: return false
        return templatesDir.name == "templates" && templatesDir.parent?.name == "_output"
    }

    fun templateType(file: VirtualFile?): String? {
        val dir = file?.parent ?: return null
        return dir.name.takeIf { it in TEMPLATE_TYPES && dir.parent?.name == "templates" }
    }

    fun templateTitle(file: VirtualFile): String =
        if (file.name.endsWith(".html", ignoreCase = true)) file.nameWithoutExtension else file.name
}
