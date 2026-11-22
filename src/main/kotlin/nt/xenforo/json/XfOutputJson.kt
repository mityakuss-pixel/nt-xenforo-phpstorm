package nt.xenforo.json

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.php.XfShortContext
import nt.xenforo.php.XfShortKind
import nt.xenforo.php.XfShortNames

private const val OUTPUT_FOLDER = "_output"
private const val ADD_ON_FILE = "addon.json"
private const val BUILD_FILE = "build.json"
private const val SEPARATOR = '\\'

private sealed interface XfJsonTarget {
    data object PhpClassName : XfJsonTarget
    data class Method(val classProperty: String) : XfJsonTarget
    data class Short(val kind: XfShortKind) : XfJsonTarget
}

private val TARGETS: Map<String, Map<String, XfJsonTarget>> = mapOf(
    "class_extensions" to mapOf("from_class" to XfJsonTarget.PhpClassName, "to_class" to XfJsonTarget.PhpClassName),
    "code_event_listeners" to mapOf(
        "callback_class" to XfJsonTarget.PhpClassName,
        "callback_method" to XfJsonTarget.Method("callback_class"),
        "hint" to XfJsonTarget.PhpClassName,
    ),
    "content_type_fields" to mapOf("field_value" to XfJsonTarget.PhpClassName),
    "routes" to mapOf(
        "controller" to XfJsonTarget.Short(XfShortKind.CONTROLLER),
        "build_class" to XfJsonTarget.PhpClassName,
        "build_method" to XfJsonTarget.Method("build_class"),
    ),
    "cron" to mapOf("cron_class" to XfJsonTarget.PhpClassName, "cron_method" to XfJsonTarget.Method("cron_class")),
    "widget_definitions" to mapOf("definition_class" to XfJsonTarget.Short(XfShortKind.WIDGET)),
    "options" to mapOf("validation_class" to XfJsonTarget.PhpClassName, "validation_method" to XfJsonTarget.Method("validation_class")),
)

private val ROUTE_SECTIONS = mapOf("public" to "Pub", "admin" to "Admin", "api" to "Api")

class XfOutputJsonReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JsonStringLiteral::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
                val property = literal.parent as? JsonProperty ?: return PsiReference.EMPTY_ARRAY
                if (property.value != literal || literal.value.isBlank()) return PsiReference.EMPTY_ARRAY
                val type = outputType(literal.containingFile?.originalFile?.virtualFile) ?: return PsiReference.EMPTY_ARRAY
                val target = TARGETS[type]?.get(property.name) ?: return PsiReference.EMPTY_ARRAY
                return arrayOf(XfOutputJsonReference(literal, target))
            }
        })
    }

    private fun outputType(file: VirtualFile?): String? {
        val parent = file?.parent ?: return null
        return parent.name.takeIf { parent.parent?.name == OUTPUT_FOLDER }
    }
}

private class XfOutputJsonReference(literal: JsonStringLiteral, private val target: XfJsonTarget) :
    PsiReferenceBase<JsonStringLiteral>(literal, TextRange(1, literal.textLength - 1), true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        if (DumbService.isDumb(project)) return null
        val value = element.value.trim()
        return when (target) {
            XfJsonTarget.PhpClassName -> phpClass(project, value) ?: entity(project, value)
            is XfJsonTarget.Method -> phpClass(project, sibling(target.classProperty).orEmpty())?.findMethodByName(value)
            is XfJsonTarget.Short -> {
                val section = ROUTE_SECTIONS[sibling("route_type")] ?: "Pub"
                if (value.contains(':')) XfShortNames.resolve(project, value, XfShortContext(listOf(target.kind), section)) else phpClass(project, value)
            }
        }
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun sibling(name: String): String? =
        ((element.parent?.parent as? JsonObject)?.findProperty(name)?.value as? JsonStringLiteral)?.value

    private fun phpClass(project: Project, name: String): PhpClass? {
        if (name.isEmpty() || name.contains(':')) return null
        return PhpIndex.getInstance(project).getClassesByFQN(SEPARATOR + name.trimStart(SEPARATOR)).firstOrNull()
    }

    private fun entity(project: Project, name: String): PhpClass? {
        if (!name.contains(':')) return null
        return XfShortNames.resolve(project, name, XfShortContext(listOf(XfShortKind.ENTITY), "Pub"))
    }
}

class XfJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(
        XfSchemaProvider(ADD_ON_FILE, "XenForo addon.json", "/schemas/xenforo-addon.json"),
        XfSchemaProvider(BUILD_FILE, "XenForo build.json", "/schemas/xenforo-build.json"),
    )
}

private class XfSchemaProvider(private val fileName: String, private val title: String, private val resource: String) : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean {
        if (file.name != fileName) return false
        val directory = file.parent ?: return false
        if (fileName == BUILD_FILE && directory.findChild(ADD_ON_FILE) == null) return false
        return generateSequence(directory.parent) { it.parent }.any { it.name == "addons" && it.parent?.name == "src" }
    }

    override fun getName(): String = title

    override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(XfJsonSchemaProviderFactory::class.java, resource)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
