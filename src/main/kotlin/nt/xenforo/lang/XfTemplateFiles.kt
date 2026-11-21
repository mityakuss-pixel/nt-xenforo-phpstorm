package nt.xenforo.lang

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile

object XfTemplateFiles {
    const val DEFAULT_TYPE: String = "public"

    val TEMPLATE_TYPES: List<String> = listOf("public", "admin", "email")

    private const val HTML_EXTENSION = "html"
    private const val LESS_EXTENSION = "less"
    private const val CSS_EXTENSION = "css"
    private const val LESS_LANGUAGE = "LESS"
    private const val CSS_LANGUAGE = "CSS"

    private val EXTENSIONS = setOf(HTML_EXTENSION, LESS_EXTENSION, CSS_EXTENSION)

    fun isOutputTemplate(file: VirtualFile): Boolean {
        if (file.extension?.lowercase() !in EXTENSIONS) return false
        if (dataLanguage(file) == null) return false
        val typeDir = file.parent ?: return false
        if (typeDir.name !in TEMPLATE_TYPES) return false
        val templatesDir = typeDir.parent ?: return false
        return templatesDir.name == "templates" && templatesDir.parent?.name == "_output"
    }

    fun isStyleTemplate(file: VirtualFile): Boolean = file.extension?.lowercase().let { it == LESS_EXTENSION || it == CSS_EXTENSION }

    fun dataLanguage(file: VirtualFile): Language? = when (file.extension?.lowercase()) {
        LESS_EXTENSION -> Language.findLanguageByID(LESS_LANGUAGE) ?: Language.findLanguageByID(CSS_LANGUAGE)
        CSS_EXTENSION -> Language.findLanguageByID(CSS_LANGUAGE)
        else -> HTMLLanguage.INSTANCE
    }

    fun templateType(file: VirtualFile?): String? {
        val dir = file?.parent ?: return null
        return dir.name.takeIf { it in TEMPLATE_TYPES && dir.parent?.name == "templates" }
    }

    fun templateTitle(file: VirtualFile): String =
        if (file.name.endsWith(".html", ignoreCase = true)) file.nameWithoutExtension else file.name
}
