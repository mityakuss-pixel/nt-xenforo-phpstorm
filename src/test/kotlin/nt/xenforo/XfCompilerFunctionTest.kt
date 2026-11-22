package nt.xenforo

import com.jetbrains.php.lang.psi.elements.Method
import nt.xenforo.reference.XfCallableReference

class XfCompilerFunctionTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "src/XF/Template/Templater.php",
            "<?php\nnamespace XF\\Template;\n\nclass Templater\n{\n\tprotected \$defaultFunctions = [\n\t\t'phrase_dynamic' => 'fnPhraseDynamic',\n\t];\n" +
                "\tpublic function fnPhraseDynamic(\$templater, &\$escape, \$phraseName, array \$params = []) {}\n}\n",
        )
        myFixture.addFileToProject(
            "src/XF/Template/Compiler.php",
            "<?php\nnamespace XF\\Template;\n\nclass Compiler\n{\n\tprotected \$defaultFunctions = [\n\t\t'include' => 'IncludeFn',\n\t\t'phrase' => 'Phrase',\n\t];\n}\n",
        )
        for (name in listOf("Phrase", "IncludeFn")) {
            myFixture.addFileToProject(
                "src/XF/Template/Compiler/Func/$name.php",
                "<?php\nnamespace XF\\Template\\Compiler\\Func;\n\nclass $name\n{\n\tpublic function compile(\$func, \$compiler, array \$context) {}\n}\n",
            )
        }
    }

    fun testPhraseOpensCompilerFunction() {
        configureTemplate("{{ phr<caret>ase('save') }}")
        assertEquals("\\XF\\Template\\Compiler\\Func\\Phrase", (resolve() as Method).containingClass?.fqn)
    }

    fun testIncludeOpensCompilerFunction() {
        configureTemplate("{{ incl<caret>ude('demo') }}")
        assertEquals("\\XF\\Template\\Compiler\\Func\\IncludeFn", (resolve() as Method).containingClass?.fqn)
    }

    fun testTemplaterFunctionStillOpensTemplater() {
        configureTemplate("{{ phrase_dyn<caret>amic('save') }}")
        assertEquals("fnPhraseDynamic", (resolve() as Method).name)
    }

    private fun resolve(): Any? = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfCallableReference).resolve()
}
