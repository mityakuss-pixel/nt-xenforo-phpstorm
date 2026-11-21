package nt.xenforo.meta

class XfTagInfo(
    val name: String,
    val attributes: List<String>,
    val required: List<String>,
    val block: Boolean,
    val nested: Boolean,
)

object XfTags {
    private val ROW = listOf("label", "hint", "explain", "rowtype", "rowclass", "rowid", "labelid", "initialhtml", "html", "finalhtml", "error")
    private val CONTROL = listOf(
        "name", "value", "class", "id", "placeholder", "readonly", "disabled", "required", "autofocus",
        "maxlength", "autocomplete", "title", "style", "dir", "data-xf-init",
    )
    private val HTML_COMMON = listOf("class", "id", "title", "style", "data-xf-init", "data-xf-click")

    private val tags = LinkedHashMap<String, XfTagInfo>()

    private val expressionAttributes: Map<String, Set<String>> = mapOf(
        "if" to setOf("is"),
        "elseif" to setOf("is"),
        "foreach" to setOf("loop", "value", "key", "i", "if", "istart"),
        "set" to setOf("var"),
        "map" to setOf("from", "to"),
        "macro" to setOf("args"),
        "avatar" to setOf("user", "canonical"),
        "profilebanner" to setOf("user", "canonical"),
        "username" to setOf("user", "rich"),
        "usertitle" to setOf("user", "banner"),
        "userbanners" to setOf("user"),
        "userblurb" to setOf("user"),
        "useractivity" to setOf("user"),
        "date" to setOf("time"),
        "breadcrumb" to setOf("source"),
        "options" to setOf("source"),
        "callback" to setOf("params"),
        "description" to setOf("meta"),
        "js" to setOf("defer"),
        "likes" to setOf("liked"),
        "pageaction" to setOf("if"),
        "prefixinput" to setOf("prefixes"),
        "prefixinputrow" to setOf("prefixes"),
        "redirect" to setOf("referrer"),
    )

    private val LEXER_TAGS = setOf("comment", "plain")

    val BRANCHES: Map<String, Set<String>> = mapOf(
        "if" to setOf("else", "elseif"),
        "foreach" to setOf("else"),
    )

    private fun tag(name: String, attributes: List<String>, required: List<String> = emptyList(), block: Boolean = false, nested: Boolean = false) {
        tags[name] = XfTagInfo(name, (required + attributes).distinct(), required, block, nested)
    }

    private fun control(name: String, extra: List<String> = emptyList(), required: List<String> = emptyList(), block: Boolean = false) {
        tag(name, CONTROL + extra, required, block)
        tag(name + "row", CONTROL + extra + ROW, required, block)
    }

    init {
        tag("if", listOf("is", "contentcheck"), listOf("is"), block = true)
        tag("elseif", listOf("is", "contentcheck"), listOf("is"), nested = true)
        tag("else", emptyList(), nested = true)
        tag("contentcheck", emptyList(), block = true, nested = true)
        tag("foreach", listOf("loop", "value", "key", "i", "if", "istart"), listOf("loop", "value"), block = true)
        tag("set", listOf("var", "value"), listOf("var"))
        tag("include", listOf("template"), listOf("template"))
        tag("wrap", listOf("template"), listOf("template"), block = true)
        tag("extends", listOf("template"), listOf("template"))
        tag("map", listOf("from", "to"), listOf("from", "to"), nested = true)
        tag("macro", listOf("id", "template", "name", "args", "extends", "global"), listOf("id"))
        tag("extension", listOf("id", "value", "skipprint"), listOf("id"), block = true)
        tag("extensionparent", emptyList())
        tag("extensionvalue", listOf("id"), listOf("id"))
        tag("trim", emptyList(), block = true)
        tag("comment", emptyList(), block = true)
        tag("plain", emptyList(), block = true)
        tag("callback", listOf("class", "method", "params"), listOf("class", "method"))
        tag("mustache", listOf("name"), listOf("name"))

        tag("title", listOf("page", "fallback", "formatter"), block = true)
        tag("h1", listOf("fallback", "hidden"), block = true)
        tag("description", listOf("meta"), block = true)
        tag("head", listOf("option"), listOf("option"), block = true)
        tag("page", listOf("option", "value"), listOf("option", "value"))
        tag("breadcrumb", listOf("href", "source", "value"), listOf("href"), block = true)
        tag("pageaction", listOf("if"), block = true)
        tag("sidebar", listOf("key", "mode"), block = true)
        tag("sidenav", listOf("key", "mode"), block = true)
        tag("widgetpos", listOf("id", "position"), listOf("id"))
        tag("widget", listOf("key", "class", "position"))
        tag("ad", listOf("position"), listOf("position"))
        tag("pagenav", listOf("page", "perpage", "total", "link", "data", "params", "wrapperclass", "pageparam"), listOf("page", "perpage", "total", "link"))
        tag("css", listOf("src"), listOf("src"))
        tag("js", listOf("src", "prod", "dev", "addon", "min", "defer"), listOf("src"))
        tag("corejs", emptyList())
        tag("copyright", emptyList())
        tag("csrf", emptyList())
        tag("redirect", listOf("url", "fallback", "referrer"))
        tag("showignored", listOf("wrapperclass"))

        tag("avatar", listOf("user", "size", "canonical", "defaultname", "href", "notooltip", "update", "forcetype", "itemprop") + HTML_COMMON, listOf("user", "size"))
        tag("profilebanner", listOf("user", "size", "canonical", "toggle", "hidevisitor") + HTML_COMMON, listOf("user", "size"), block = true)
        tag("username", listOf("user", "rich", "defaultname", "href", "notooltip", "itemprop") + HTML_COMMON, listOf("user"))
        tag("usertitle", listOf("user", "banner", "tag") + HTML_COMMON, listOf("user"))
        tag("userbanners", listOf("user", "tag") + HTML_COMMON, listOf("user"))
        tag("userblurb", listOf("user", "tag") + HTML_COMMON, listOf("user"))
        tag("useractivity", listOf("user") + HTML_COMMON, listOf("user"))
        tag("date", listOf("time", "class", "itemprop"), listOf("time"))
        tag("fa", listOf("icon", "class", "title"), listOf("icon"))
        tag("likes", listOf("content", "count", "liked", "likes", "url", "users"))
        tag("react", listOf("content", "link", "list"))
        tag("reaction", listOf("id", "showtitle", "small", "tooltip", "class"), listOf("id"))
        tag("reactions", listOf("content", "link", "linkparams", "reactions", "summary"))

        tag("form", listOf("action", "ajax", "upload", "method", "data-force-flash-message", "data-redirect") + HTML_COMMON, listOf("action"), block = true)
        tag("formrow", ROW + HTML_COMMON, block = true)
        tag("inforow", ROW + HTML_COMMON, block = true)
        tag("submitrow", listOf("icon", "submit", "sticky", "rowtype", "rowclass", "class", "name", "value"))
        tag("button", listOf("href", "type", "icon", "fa", "overlay", "name", "value", "button") + HTML_COMMON, block = true)
        tag("hiddenval", listOf("name", "value"), listOf("name"), block = true)
        tag("datalist", listOf("data-xf-init") + HTML_COMMON, block = true)
        tag("datarow", listOf("rowclass", "rowtype", "label", "hint", "explain", "href", "overlay", "delete", "dir") + HTML_COMMON, block = true)
        tag("radiotabs", listOf("name", "value", "label") + ROW, block = true)

        control("textbox", listOf("type", "size", "pattern", "list", "code", "min", "max", "step", "units"), listOf("name"))
        control("textarea", listOf("rows", "autosize", "code"), listOf("name"))
        control("numberbox", listOf("min", "max", "step", "units", "buttons"), listOf("name"))
        control("passwordbox", listOf("checkstrength", "hideshow"), listOf("name"))
        control("telbox", listOf("dialcodename", "intlnumbername"), listOf("name"))
        control("checkbox", listOf("listclass", "standalone"), block = true)
        control("radio", listOf("listclass"), listOf("name"), block = true)
        control("select", listOf("multiple", "size"), listOf("name"), block = true)
        control("dateinput", listOf("weekstart", "min", "max"), listOf("name"))
        control("datetimeinput", listOf("weekstart", "min", "max"), listOf("name"))
        control("timeinput", listOf("step"), listOf("name"))
        control("tokeninput", listOf("href", "max-tokens", "min-length", "list-data"), listOf("name"))
        control("upload", listOf("accept", "multiple"), listOf("name"))
        control("assetupload", listOf("asset"), listOf("name"))
        control("editor", listOf("attachments", "previewable", "removebuttons", "data-min-height"), listOf("name"))
        control("codeeditor", listOf("mode", "data-line-wrapping", "data-submit-selector"), listOf("name"))
        control("prefixinput", listOf("prefixes", "type", "prefix-value", "textbox-value", "textbox-class", "href", "listen-to"), listOf("prefixes"))
        tag("captcha", listOf("force", "context"))
        tag("captcharow", listOf("force", "context") + ROW)

        tag("option", listOf("value", "label", "selected", "checked", "hint", "explain", "name", "disabled", "data-hide", "data-xf-init", "class"), nested = true, block = true)
        tag("options", listOf("source"), listOf("source"), nested = true)
        tag("optgroup", listOf("label"), listOf("label"), nested = true, block = true)
        tag("dependent", emptyList(), nested = true, block = true)
        for (child in listOf("label", "hint", "explain", "html", "afterhint", "afterhtml", "initialhtml", "finalhtml")) {
            tag(child, emptyList(), nested = true, block = true)
        }
        tag("menu", HTML_COMMON, nested = true, block = true)
        for (cell in listOf("cell", "main", "action", "popup", "extract")) {
            tag(cell, listOf("href", "overlay", "label", "hint", "explain") + HTML_COMMON, nested = true, block = true)
        }
        tag("delete", listOf("href", "tooltip"), nested = true)
        tag("toggle", listOf("name", "selected", "submit", "tooltip", "class"), nested = true)
    }

    val ALL: Collection<XfTagInfo> get() = tags.values

    fun get(name: String): XfTagInfo? = tags[name.lowercase()]

    fun isKnown(name: String): Boolean = tags.containsKey(name.lowercase())

    fun isExpressionAttribute(tag: String, attribute: String): Boolean =
        expressionAttributes[tag.lowercase()]?.contains(attribute.lowercase()) == true

    fun isStructural(name: String): Boolean = get(name)?.nested == true || name.lowercase() in LEXER_TAGS

    fun isBranch(parent: String, child: String): Boolean = BRANCHES[parent.lowercase()]?.contains(child.lowercase()) == true
}
