package nt.xenforo.index

private const val CONTAINER_MARKER = "ontainer"
private val CONTAINER_HOLDER = """(?:\${'$'}\w*[cC]ontainer\w*|\${'$'}this->container|->container\(\))"""
private val ASSIGNMENT = Regex("""$CONTAINER_HOLDER\s*\[\s*(['"])([\w.:-]+)\1\s*]\s*=(?![=>])""")
private val SETTER = Regex("""$CONTAINER_HOLDER\s*->\s*(?:set|extend)\(\s*(['"])([\w.:-]+)\1""")

object XfContainerKeys {
    fun scan(text: CharSequence, result: MutableMap<String, XfSymbol>) {
        if (!text.contains(CONTAINER_MARKER)) return
        for (pattern in listOf(ASSIGNMENT, SETTER)) {
            for (match in pattern.findAll(text)) {
                val key = match.groups[2] ?: continue
                result.putIfAbsent(XfSymbolKeys.container(key.value), XfSymbol(key.range.first, ""))
            }
        }
    }
}
