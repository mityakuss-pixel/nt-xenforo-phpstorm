package nt.xenforo

import com.jetbrains.php.lang.psi.elements.PhpClass
import nt.xenforo.inspection.XfUnknownTagInspection
import nt.xenforo.model.XfTagCatalog
import nt.xenforo.reference.XfTagGotoDeclarationHandler

class XfTagCatalogTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/XF/Template/Compiler.php", COMPILER)
        myFixture.addFileToProject("src/XF/Template/Compiler/Tag/AbstractTag.php", ABSTRACT_TAG)
        myFixture.addFileToProject("src/XF/Template/Compiler/Tag/AbstractFormElement.php", ABSTRACT_FORM_ELEMENT)
        myFixture.addFileToProject("src/XF/Template/Compiler/Tag/Avatar.php", AVATAR)
        myFixture.addFileToProject("src/XF/Template/Compiler/Tag/TextBoxRow.php", TEXT_BOX_ROW)
        myFixture.addFileToProject("src/XF/Template/Compiler/Tag/IfTag.php", tagClass("IfTag", "\$tag->assertAttribute('is');"))
        myFixture.addFileToProject("src/config.php", CONFIG)
        myFixture.addFileToProject("src/Demo/Tag/Confirm.php", CONFIRM)
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        myFixture.addFileToProject("src/addons/Demo/Test/Tag/Badge.php", BADGE)
    }

    fun testTagNamesComeFromCompilerAndRegistrations() {
        configureTemplate("<xf:<caret>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "avatar", "textbox", "textboxrow", "if", "confirm", "badge", "else", "comment")
        assertDoesntContain(variants, "profilebanner", "widget")
    }

    fun testAttributesOfCustomTagComeFromClass() {
        val info = XfTagCatalog.get(project, "confirm")!!
        assertEquals(listOf("href"), info.required)
        assertSameElements(info.attributes, "href", "title", "overlay")
        assertTrue(info.block)
    }

    fun testKnownTagGetsAttributesFromClass() {
        val info = XfTagCatalog.get(project, "avatar")!!
        assertEquals(listOf("user", "size"), info.required)
        assertContainsElements(info.attributes, "user", "size", "canonical", "forcetype", "brandnew")
    }

    fun testRowOptionsOnlyForRowTags() {
        assertContainsElements(XfTagCatalog.get(project, "textboxrow")!!.attributes, "label", "hint", "rowtype")
        assertDoesntContain(XfTagCatalog.get(project, "textbox")!!.attributes, "label", "hint", "rowtype")
    }

    fun testUnknownTagInspectionUsesProjectTags() {
        myFixture.enableInspections(XfUnknownTagInspection())
        configureTemplate(
            "<xf:confirm href=\"x\">ok</xf:confirm>\n<xf:badge user=\"\$u\" />\n" +
                "<xf:<warning descr=\"Unknown XenForo tag <xf:profilebanner>\">profilebanner</warning> user=\"\$u\" size=\"m\" />",
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun testJumpToTagClass() {
        configureTemplate("<xf:con<caret>firm href=\"x\">ok</xf:confirm>")
        val offset = myFixture.caretOffset
        val targets = XfTagGotoDeclarationHandler().getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, myFixture.editor)
        assertEquals("\\Demo\\Tag\\Confirm", (targets!!.single() as PhpClass).fqn)
    }

    fun testDocumentationNamesImplementation() {
        configureTemplate("<xf:ava<caret>tar user=\"\$u\" size=\"s\" />")
        val target = nt.xenforo.documentation.XfDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single()
        val html = (target as nt.xenforo.documentation.XfDocumentationTarget).html()
        assertTrue(html, html.contains("XF\\Template\\Compiler\\Tag\\Avatar"))
    }

    private companion object {
        fun tagClass(name: String, body: String): String = """
            <?php
            namespace XF\Template\Compiler\Tag;
            class $name extends AbstractTag
            {
                public function compile(${'$'}tag, ${'$'}compiler, array ${'$'}context, ${'$'}inlineExpected)
                {
                    $body
                }
            }
        """.trimIndent()

        val COMPILER = """
            <?php
            namespace XF\Template;
            class Compiler
            {
                protected ${'$'}defaultTags = [
                    'avatar' => 'Avatar',
                    'if' => 'IfTag',
                    'textbox' => 'TextBoxRow',
                    'textboxrow' => 'TextBoxRow',
                ];

                public function setTag(${'$'}name, ${'$'}tag) {}
            }
        """.trimIndent()

        val ABSTRACT_TAG = """
            <?php
            namespace XF\Template\Compiler\Tag;
            abstract class AbstractTag
            {
                public function __construct(${'$'}name) {}
            }
        """.trimIndent()

        val ABSTRACT_FORM_ELEMENT = """
            <?php
            namespace XF\Template\Compiler\Tag;
            abstract class AbstractFormElement extends AbstractTag
            {
                protected ${'$'}defaultRowOptions = [
                    'label' => true,
                    'hint' => true,
                    'rowtype' => false,
                ];
            }
        """.trimIndent()

        val AVATAR = tagClass(
            "Avatar",
            "\$tag->assertEmpty()->assertAttribute('user')->assertAttribute('size');\n" +
                "if (isset(\$tag->attributes['brandnew'])) {}\n\$canonical = \$tag->attributes['canonical'];",
        )

        val TEXT_BOX_ROW = """
            <?php
            namespace XF\Template\Compiler\Tag;
            class TextBoxRow extends AbstractFormElement
            {
                public function compile(${'$'}tag, ${'$'}compiler, array ${'$'}context, ${'$'}inlineExpected)
                {
                    ${'$'}tag->assertAttribute('name');
                }
            }
        """.trimIndent()

        val CONFIG = """
            <?php
            ${'$'}config['container'] = function (\XF\Container ${'$'}c) {
                ${'$'}c->extend('templateCompiler', function (\XF\Template\Compiler ${'$'}compiler) {
                    ${'$'}compiler->setTag('confirm', '\Demo\Tag\Confirm');
                    return ${'$'}compiler;
                });
            };
        """.trimIndent()

        val CONFIRM = """
            <?php
            namespace Demo\Tag;
            class Confirm extends \XF\Template\Compiler\Tag\AbstractTag
            {
                public function compile(${'$'}tag, ${'$'}compiler, array ${'$'}context, ${'$'}inlineExpected)
                {
                    ${'$'}tag->assertAttribute('href');
                    ${'$'}attributes = ${'$'}tag->attributes;
                    ${'$'}title = ${'$'}attributes['title'] ?? '';
                    ${'$'}overlay = ${'$'}tag->attributes['overlay'] ?? false;
                    foreach (${'$'}tag->children AS ${'$'}child) {}
                }
            }
        """.trimIndent()

        val LISTENER = """
            <?php
            namespace Demo\Test;
            class Listener
            {
                public static function compilerSetup(\XF\Template\Compiler ${'$'}compiler)
                {
                    ${'$'}compiler->setTags(['badge' => new \Demo\Test\Tag\Badge('badge')]);
                }
            }
        """.trimIndent()

        val BADGE = """
            <?php
            namespace Demo\Test\Tag;
            class Badge extends \XF\Template\Compiler\Tag\AbstractTag
            {
                public function compile(${'$'}tag, ${'$'}compiler, array ${'$'}context, ${'$'}inlineExpected)
                {
                    ${'$'}tag->assertEmpty()->assertAttribute('user');
                }
            }
        """.trimIndent()
    }
}
