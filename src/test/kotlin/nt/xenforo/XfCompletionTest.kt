package nt.xenforo

class XfCompletionTest : XfTestCase() {
    fun testTagNames() {
        configureTemplate("<xf:fore<caret>")
        myFixture.completeBasic()
        assertEquals("<xf:foreach loop=\"\" value=\"\"></xf:foreach>", myFixture.editor.document.text)
        assertEquals("<xf:foreach loop=\"".length, myFixture.editor.caretModel.offset)
    }

    fun testAttributesOfCurrentTag() {
        configureTemplate("<xf:avatar user=\"\$u\" <caret> />")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "size", "canonical", "defaultname")
        assertDoesntContain(variants, "user", "loop")
    }

    fun testFunctionsWithParentheses() {
        configureTemplate("{{ number_sh<caret> }}")
        myFixture.completeBasic()
        assertEquals("{{ number_short() }}", myFixture.editor.document.text)
    }

    fun testFilters() {
        configureTemplate("{{ \$title|for_a<caret> }}")
        myFixture.completeBasic()
        assertEquals("{{ \$title|for_attr }}", myFixture.editor.document.text)
    }

    fun testXfGlobals() {
        configureTemplate("{{ \$xf.visit<caret> }}")
        myFixture.completeBasic()
        assertEquals("{{ \$xf.visitor }}", myFixture.editor.document.text)
    }

    fun testPhrasesFromOutputAndData() {
        addOutputFile("phrases/demo_greeting.txt", "Hello, {name}!")
        addDataFile("phrases.xml", "<phrases>\n  <phrase title=\"demo_goodbye\" version_id=\"1\"><![CDATA[Goodbye]]></phrase>\n</phrases>")
        configureTemplate("{{ phrase('demo_<caret>') }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "demo_greeting", "demo_goodbye")
    }

    fun testTemplatesAndMacros() {
        addOutputFile("templates/public/demo_widget.html", "<xf:macro id=\"card\" arg-user=\"!\">x</xf:macro>\n<xf:macro id=\"list\">y</xf:macro>")
        configureTemplate("<xf:macro id=\"demo_widget::<caret>\" />")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "card", "list")
    }

    fun testRoutes() {
        addOutputFile("routes/public_demo-things_.json", route("demo-things", ""))
        addOutputFile("routes/public_demo-things_images.json", route("demo-things", "images"))
        configureTemplate("<a href=\"{{ link('demo-<caret>') }}\">x</a>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "demo-things", "demo-things/images")
    }

    private fun route(prefix: String, subName: String): String =
        "{\n    \"route_type\": \"public\",\n    \"route_prefix\": \"$prefix\",\n    \"sub_name\": \"$subName\",\n    \"controller\": \"Demo:Thing\"\n}"
}
