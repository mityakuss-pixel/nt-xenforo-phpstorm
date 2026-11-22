package nt.xenforo.documentation

import com.intellij.icons.AllIcons
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import nt.xenforo.XfBundle
import nt.xenforo.index.XfCallableKind
import nt.xenforo.index.XfJsHandlers
import nt.xenforo.index.XfOptionDetail
import nt.xenforo.index.XfSymbolKeys
import nt.xenforo.index.XfSymbolLocation
import nt.xenforo.index.XfSymbols
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfIcons
import nt.xenforo.lang.XfLanguage
import nt.xenforo.meta.XfGlobals
import nt.xenforo.model.XfTagCatalog
import nt.xenforo.model.XfAccess
import nt.xenforo.model.XfCallables
import nt.xenforo.model.XfMember
import nt.xenforo.model.XfMemberKind
import nt.xenforo.model.XfValueType
import nt.xenforo.psi.XfAccessExpression
import nt.xenforo.psi.XfEndTagElement
import nt.xenforo.psi.XfFilter
import nt.xenforo.psi.XfFunctionCallExpr
import nt.xenforo.psi.XfReferenceHost
import nt.xenforo.psi.XfTagHeader
import nt.xenforo.psi.XfTestExpr
import nt.xenforo.psi.XfTypes
import nt.xenforo.php.XfPhpNames
import nt.xenforo.php.XfTemplateParams
import nt.xenforo.lang.XfTemplateFiles
import nt.xenforo.reference.XfHtmlHandlerReference
import nt.xenforo.reference.XfNameKind
import nt.xenforo.reference.XfNameSlot
import nt.xenforo.reference.XfNameSlots
import javax.swing.Icon

private const val OPTION_TITLE_PREFIX = "option."
private const val OPTION_EXPLAIN_PREFIX = "option_explain."

class XfDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file.viewProvider !is XfFileViewProvider) return listOfNotNull(XfPhpNames.documentation(file, offset))
        XfHtmlHandlerReference.at(file, offset)?.let { return listOf(XfDocumentation.handler(file.project, it.key, it.value)) }
        for (candidate in listOf(offset, offset - 1)) {
            if (candidate < 0) continue
            val element = file.viewProvider.findElementAt(candidate, XfLanguage) ?: continue
            val target = XfDocumentation.target(element, candidate) ?: continue
            return listOf(target)
        }
        return emptyList()
    }
}

class XfDocumentationTarget(
    private val title: String,
    private val icon: Icon?,
    private val definition: String,
    private val content: String? = null,
    private val sections: List<Pair<String, String>> = emptyList(),
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(title).icon(icon).presentation()

    override fun computeDocumentationHint(): String = definition

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html())

    fun html(): String = buildString {
        append(DocumentationMarkup.DEFINITION_START).append(definition).append(DocumentationMarkup.DEFINITION_END)
        if (!content.isNullOrEmpty()) append(DocumentationMarkup.CONTENT_START).append(content).append(DocumentationMarkup.CONTENT_END)
        if (sections.isNotEmpty()) {
            append(DocumentationMarkup.SECTIONS_START)
            for ((label, value) in sections) {
                append(DocumentationMarkup.SECTION_HEADER_START).append(label).append(DocumentationMarkup.SECTION_SEPARATOR)
                append(value).append(DocumentationMarkup.SECTION_END)
            }
            append(DocumentationMarkup.SECTIONS_END)
        }
    }
}

object XfDocumentation {
    fun target(element: PsiElement, offset: Int): XfDocumentationTarget? = when (element.elementType) {
        XfTypes.TAG_NAME -> tag(element)
        XfTypes.IDENTIFIER -> identifier(element)
        XfTypes.VARIABLE -> variable(element)
        XfTypes.STRING, XfTypes.ATTR_TEXT -> name(element, offset)
        else -> null
    }

    private fun tag(element: PsiElement): XfDocumentationTarget? {
        val parent = element.parent
        if (parent !is XfTagHeader && parent !is XfEndTagElement) return null
        val project = element.project
        val info = XfTagCatalog.get(project, element.text) ?: return null
        val definition = XfTagCatalog.definition(project, info.name)
        val required = info.required.joinToString("") { " $it=\"…\"" }
        val code = if (info.block) "<xf:${info.name}$required>…</xf:${info.name}>" else "<xf:${info.name}$required />"
        val sections = buildList {
            if (info.required.isNotEmpty()) add(message("doc.section.required") to codeList(info.required))
            val optional = info.attributes.filter { it !in info.required }
            if (optional.isNotEmpty()) add(message("doc.section.attributes") to codeList(optional))
            definition?.let { add(message("doc.section.implemented.by") to sectionCode(it.classFqn.removePrefix("\\"))) }
            definition?.registration?.let { add(definedIn(listOf(it))) }
        }
        val kind = message(if (info.block) "doc.kind.tag.block" else "doc.kind.tag.single")
        return XfDocumentationTarget("xf:${info.name}", XfIcons.TAG, XfSignatureHtml.code(project, code), kind, sections)
    }

    private fun identifier(element: PsiElement): XfDocumentationTarget? {
        val name = element.text
        return when (val parent = element.parent) {
            is XfFunctionCallExpr -> if (parent.callNameElement == element) callable(element.project, XfCallableKind.FUNCTION, name) else null
            is XfFilter -> callable(element.project, XfCallableKind.FILTER, name)
            is XfTestExpr -> callable(element.project, XfCallableKind.TEST, name)
            is XfAccessExpression -> member(parent, element)
            else -> null
        }
    }

    private fun callable(project: Project, kind: XfCallableKind, name: String): XfDocumentationTarget? {
        if (!XfCallables.isKnown(project, kind, name)) return null
        val doc = XfCallables.documentation(project, kind, name)
        val parameters = doc?.parameters?.map { it.first } ?: XfCallables.parameters(project, kind, name).orEmpty()
        val (prefix, icon, kindKey) = when (kind) {
            XfCallableKind.FUNCTION -> Triple("", AllIcons.Nodes.Function, "doc.kind.function")
            XfCallableKind.FILTER -> Triple("value|", AllIcons.Nodes.Method, "doc.kind.filter")
            XfCallableKind.TEST -> Triple("value is ", AllIcons.Nodes.Method, "doc.kind.test")
        }
        val shownParameters = parameters.takeIf { kind == XfCallableKind.FUNCTION || it.isNotEmpty() }
        val definition = XfSignatureHtml.callable(prefix, name, shownParameters, null, doc?.returnType)
        val registration = XfCallables.registration(project, kind, name)
        val origin = if (registration == null) message(kindKey) else XfBundle.message("$kindKey.custom", registration.addOnId ?: registration.relativePath)
        val content = listOfNotNull(doc?.description?.let(::multiline), escape(origin)).joinToString("<p>")
        val sections = buildList {
            val documented = doc?.parameters ?: parameters.map { it to null }
            if (documented.isNotEmpty()) {
                add(message("doc.section.parameters") to documented.joinToString("<br>") { (parameter, text) -> parameterLine(parameter, text) })
            }
            if (doc?.returnType != null || doc?.returns != null) {
                val type = doc.returnType?.let { "<code>${XfSignatureHtml.type(it)}</code>" }
                add(message("doc.section.returns") to listOfNotNull(type, doc.returns?.let(::escape)).joinToString(" "))
            }
            registration?.let { add(definedIn(listOf(it))) }
        }
        return XfDocumentationTarget(name, icon, definition, content, sections)
    }

    private fun parameterLine(parameter: String, description: String?): String {
        val parts = XfSignatureHtml.parse(parameter)
        val type = parts.type?.let { "<code>${XfSignatureHtml.type(it)}</code> " }.orEmpty()
        val name = "<code>${XfSignatureHtml.parameter(XfParameterParts(null, parts.prefix, parts.name, null), withDefault = false)}</code>"
        return type + name + description?.let { " " + escape(it) }.orEmpty()
    }

    private fun variable(element: PsiElement): XfDocumentationTarget? {
        val name = element.text.removePrefix("$")
        if (name != XfGlobals.ROOT) return parameter(element, name)
        return XfDocumentationTarget("\$$name", AllIcons.Nodes.Variable, bold("\$$name"), message("doc.global.root"))
    }

    private fun parameter(element: PsiElement, name: String): XfDocumentationTarget? {
        val file = element.containingFile ?: return null
        if (DumbService.isDumb(file.project) || XfTemplateParams.of(file)[name] == null) return null
        val virtualFile = file.originalFile.virtualFile ?: return null
        val type = XfTemplateFiles.templateType(virtualFile) ?: return null
        val sources = XfTemplateParams.calls(file.project, type, XfTemplateFiles.templateTitle(virtualFile)).map(XfTemplateParams::location).distinct()
        val sections = listOf(message("doc.section.passed.by") to sources.joinToString("<br>") { sectionCode(it) })
        return XfDocumentationTarget("\$$name", AllIcons.Nodes.Variable, bold("\$$name") + typeSuffix(XfTemplateParams.presentableType(file, name)), message("doc.kind.view.param"), sections)
    }

    private fun member(expression: XfAccessExpression, element: PsiElement): XfDocumentationTarget? {
        val project = element.project
        val index = XfAccess.segmentIndex(expression, element)
        if (index < 0) return null
        val owner = XfAccess.ownerType(project, expression, index) ?: return null
        val member = XfAccess.member(project, expression, index) ?: return null
        val path = buildString {
            append('$').append(expression.variableName)
            for (segment in expression.segments.take(index)) append('.').append(segment.name)
            append('.')
        }
        return when (member.kind) {
            XfMemberKind.GLOBAL -> global(path, member)
            XfMemberKind.OPTION -> option(project, path, member)
            XfMemberKind.ADD_ON -> addOn(project, path, member)
            XfMemberKind.KEY -> if (owner is XfValueType.Keys) XfDocumentationTarget(member.name, AllIcons.Nodes.Property, plain(path) + bold(member.name)) else null
            XfMemberKind.PROPERTY, XfMemberKind.METHOD -> null
        }
    }

    private fun global(path: String, member: XfMember): XfDocumentationTarget {
        val definition = plain(path) + bold(member.name) + typeSuffix(member.typeText)
        return XfDocumentationTarget(member.name, AllIcons.Nodes.Property, definition, message("global.${member.name}"))
    }

    private fun option(project: Project, path: String, member: XfMember): XfDocumentationTarget {
        val detail = XfSymbols.detail(project, XfSymbolKeys.option(member.name)).orEmpty()
        val title = XfSymbols.phraseText(project, OPTION_TITLE_PREFIX + member.name)
        val explain = XfSymbols.phraseText(project, OPTION_EXPLAIN_PREFIX + member.name)
        val content = listOfNotNull(title?.takeIf { it.isNotBlank() }?.let { "<b>${escape(it)}</b>" }, explain?.takeIf { it.isNotBlank() }?.let(::multiline))
            .joinToString("<br>")
        val sections = buildList {
            XfOptionDetail.editFormat(detail).takeIf { it.isNotEmpty() }?.let { add(message("doc.section.edit.format") to sectionCode(it)) }
            XfOptionDetail.subOptions(detail).takeIf { it.isNotEmpty() }?.let { add(message("doc.section.sub.options") to codeList(it)) }
            definedIn(project, XfSymbolKeys.option(member.name))?.let { add(it) }
        }
        return XfDocumentationTarget(member.name, AllIcons.Nodes.Property, plain(path) + bold(member.name) + typeSuffix(member.typeText), content, sections)
    }

    private fun addOn(project: Project, path: String, member: XfMember): XfDocumentationTarget {
        val key = XfSymbolKeys.addOn(member.name)
        val title = XfSymbols.detail(project, key)?.takeIf { it.isNotBlank() }
        val content = listOfNotNull(title?.let { "<b>${escape(it)}</b>" }, message("doc.add.on.value")).joinToString("<br>")
        return XfDocumentationTarget(member.name, AllIcons.Nodes.Plugin, plain(path) + bold(member.name) + typeSuffix(member.typeText), content, listOfNotNull(definedIn(project, key)))
    }

    private fun name(element: PsiElement, offset: Int): XfDocumentationTarget? {
        val host = PsiTreeUtil.getParentOfType(element, XfReferenceHost::class.java, false) ?: return null
        val project = host.project
        if (DumbService.isDumb(project)) return null
        val inHost = offset - host.textRange.startOffset
        val slot = XfNameSlots.of(host).firstOrNull { inHost >= it.range.startOffset && inHost <= it.range.endOffset } ?: return null
        val name = slot.name(host)
        if (name.isEmpty()) return null
        val key = slot.keys(host).firstOrNull { XfSymbols.locations(project, it).isNotEmpty() } ?: return null
        return symbol(project, slot.kind, slot.keyPrefix, key, name)
    }

    fun symbol(project: Project, kind: XfNameKind, keyPrefix: String, key: String, name: String): XfDocumentationTarget {
        return when (kind) {
            XfNameKind.PHRASE -> {
                val text = XfSymbols.phraseText(project, name).orEmpty()
                XfDocumentationTarget(name, AllIcons.Nodes.Tag, plain("phrase ") + bold(name), multiline(text), listOfNotNull(definedIn(project, key)))
            }
            XfNameKind.TEMPLATE -> {
                val type = key.removePrefix("t:").substringBefore(':')
                XfDocumentationTarget(name, XfIcons.FILE, plain("$type:") + bold(name), message("doc.kind.template"), listOfNotNull(definedIn(project, key)))
            }
            XfNameKind.MACRO -> macro(project, keyPrefix, key, name)
            XfNameKind.ROUTE -> {
                val route = key.substringAfter("r:").substringAfter(':')
                val controller = XfSymbols.detail(project, key)?.takeIf { it.isNotEmpty() }
                val sections = listOfNotNull(controller?.let { message("doc.section.controller") to sectionCode(it) }, definedIn(project, key))
                XfDocumentationTarget(route, AllIcons.Nodes.Related, bold(route), message("doc.kind.route"), sections)
            }
            XfNameKind.HANDLER -> handler(project, key, name)
            XfNameKind.WIDGET_POSITION -> XfDocumentationTarget(name, AllIcons.Nodes.Plugin, plain("widget position ") + bold(name), message("doc.kind.widget.position"), listOfNotNull(definedIn(project, key)))
            XfNameKind.PERMISSION -> XfDocumentationTarget(name, AllIcons.Nodes.Padlock, plain("permission ") + bold(key.substringAfter(':').replace(':', '.')), message("doc.kind.permission"), listOfNotNull(definedIn(project, key)))
            XfNameKind.EXTENSION -> XfDocumentationTarget(name, AllIcons.Nodes.Template, plain("extension ") + bold(name), "")
            XfNameKind.PROPERTY -> {
                val title = XfSymbols.detail(project, key)?.takeIf { it.isNotEmpty() }?.let(::escape)
                XfDocumentationTarget(name, AllIcons.Nodes.Property, plain("property ") + bold(name), title ?: message("doc.kind.property"), listOfNotNull(definedIn(project, key)))
            }
        }
    }

    fun handler(project: Project, key: String, name: String): XfDocumentationTarget {
        val className = XfJsHandlers.className(project, key).orEmpty()
        val location = XfJsHandlers.classLocation(project, className) ?: XfJsHandlers.registration(project, key)
        val sections = listOfNotNull(location?.let { definedIn(listOf(it)) })
        return XfDocumentationTarget(name, AllIcons.Nodes.Class, plain("$name: ") + bold(className), message("doc.kind.handler"), sections)
    }

    private fun macro(project: Project, keyPrefix: String, key: String, name: String): XfDocumentationTarget {
        val template = keyPrefix.removePrefix("m:").substringAfter(':').removeSuffix(":")
        val arguments = XfSymbols.detail(project, key).orEmpty()
        val definition = plain("$template::") + bold(name) + plain("($arguments)")
        return XfDocumentationTarget(name, AllIcons.Nodes.Method, definition, message("doc.kind.macro"), listOfNotNull(definedIn(project, key)))
    }

    private fun definedIn(project: Project, key: String): Pair<String, String>? {
        val locations = XfSymbols.locations(project, key)
        if (locations.isEmpty()) return null
        return definedIn(locations)
    }

    private fun definedIn(locations: List<XfSymbolLocation>): Pair<String, String> =
        message("doc.section.defined.in") to locations.map { it.relativePath }.distinct().joinToString("<br>") { sectionCode(it) }

    private fun typeSuffix(type: String?): String = if (type.isNullOrEmpty()) "" else plain(": $type")

    private fun bold(text: String): String = "<b>${escape(text)}</b>"

    private fun plain(text: String): String = escape(text)

    private fun sectionCode(text: String): String = "<code>${escape(text)}</code>"

    private fun codeList(items: List<String>): String = items.joinToString(", ") { sectionCode(it) }

    private fun multiline(text: String): String = escape(text.trim()).replace("\n", "<br>")

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

    private fun message(key: String): String = XfBundle.message(key)
}
