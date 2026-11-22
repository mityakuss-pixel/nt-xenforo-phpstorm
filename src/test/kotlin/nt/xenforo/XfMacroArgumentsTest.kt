package nt.xenforo

class XfMacroArgumentsTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        addOutputFile(
            "templates/public/custom_fields_macros.html",
            "<xf:macro name=\"custom_field_value\" arg-definition=\"!\" arg-value=\"!\" arg-wrapperClass=\"\"></xf:macro>",
        )
    }

    fun testLegacyNameCallListsArguments() {
        val variants = complete("<xf:macro name=\"custom_fields_macros::custom_field_value\" arg<caret> />")
        assertContainsElements(variants, "arg-definition", "arg-value", "arg-wrapperClass")
    }

    fun testIdCallSkipsArgumentsAlreadySet() {
        val variants = complete("<xf:macro id=\"public:custom_fields_macros::custom_field_value\" arg-value=\"{\$value}\" arg<caret> />", "second.html")
        assertContainsElements(variants, "arg-definition")
        assertDoesntContain(variants, "arg-value")
    }

    fun testLocalMacroCall() {
        val variants = complete("<xf:macro id=\"row\" arg-user=\"!\" arg-size=\"s\">x</xf:macro>\n<xf:macro id=\"row\" a<caret> />", "third.html")
        assertContainsElements(variants, "arg-user", "arg-size")
    }

    fun testDefinitionGetsNoArguments() {
        val variants = complete("<xf:macro id=\"custom_field_value\" a<caret>>x</xf:macro>", "fourth.html")
        assertDoesntContain(variants, "arg-definition", "arg-value")
    }

    private fun complete(text: String, name: String = "test.html"): List<String> {
        configureTemplate(text, name)
        return myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
    }
}
