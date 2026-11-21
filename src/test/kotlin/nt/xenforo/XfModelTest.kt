package nt.xenforo

import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocProperty
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import nt.xenforo.reference.XfCallableReference
import nt.xenforo.reference.XfMemberReference

class XfModelTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/XF/Mvc/Entity/Entity.php", ENTITY)
        myFixture.addFileToProject("src/XF/Entity/User.php", USER)
        myFixture.addFileToProject("src/XF/Entity/UserProfile.php", USER_PROFILE)
        myFixture.addFileToProject("src/XF/Template/Templater.php", TEMPLATER)
    }

    fun testVisitorMembersComeFromEntity() {
        configureTemplate("{{ \$xf.visitor.<caret> }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "user_id", "username", "is_super_admin", "Profile", "canSearch", "hasPermission")
        assertDoesntContain(variants, "save", "offsetGet", "getStructure")
    }

    fun testRelationIsFollowed() {
        configureTemplate("{{ \$xf.visitor.Profile.<caret> }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "about", "location")
        assertDoesntContain(variants, "username")
    }

    fun testMethodCompletionAddsParentheses() {
        configureTemplate("{{ \$xf.visitor.hasPerm<caret> }}")
        myFixture.completeBasic()
        assertEquals("{{ \$xf.visitor.hasPermission() }}", myFixture.editor.document.text)
    }

    fun testPropertyResolvesToDocblock() {
        configureTemplate("{\$xf.visitor.user<caret>name}")
        val target = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfMemberReference).resolve()
        assertInstanceOf(target, PhpDocProperty::class.java)
        assertEquals("username", (target as PhpDocProperty).name)
    }

    fun testOptionsFromDataAndOutput() {
        addDataFile("options.xml", OPTIONS)
        addOutputFile("options/demoLimit.json", "{\n    \"edit_format\": \"spinbox\",\n    \"data_type\": \"integer\",\n    \"sub_options\": []\n}")
        configureTemplate("<xf:if is=\"\$xf.options.<caret>\"></xf:if>")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "boardTitle", "registrationSetup", "demoLimit")
    }

    fun testSubOptions() {
        addDataFile("options.xml", OPTIONS)
        configureTemplate("{{ \$xf.options.registrationSetup.<caret> }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertSameElements(variants, "enabled", "emailConfirmation")
    }

    fun testCustomFunctionsFromListener() {
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\n    \"title\": \"Demo\"\n}")
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_<caret> }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "demo_badge", "demo_total")
    }

    fun testCustomFunctionResolvesToClosure() {
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ demo_ba<caret>dge(\$user) }}")
        val target = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfCallableReference).resolve()
        assertInstanceOf(target, Function::class.java)
        assertTrue((target as Function).isClosure)
    }

    fun testCustomFilterResolvesToMethod() {
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", LISTENER)
        configureTemplate("{{ \$value|demo_mo<caret>ney }}")
        val target = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfCallableReference).resolve()
        assertEquals("filterMoney", (target as Method).name)
    }

    fun testBuiltinFunctionResolvesToTemplater() {
        configureTemplate("{{ phr<caret>ase('x') }}")
        val target = (myFixture.getReferenceAtCaretPositionWithAssertion() as XfCallableReference).resolve()
        assertEquals("fnPhrase", (target as Method).name)
    }

    companion object {
        private val ENTITY = """
            <?php
            namespace XF\Mvc\Entity;
            abstract class Entity implements \ArrayAccess
            {
                public function save() {}
                public function offsetGet(${'$'}key) {}
                public function offsetSet(${'$'}key, ${'$'}value) {}
                public function offsetExists(${'$'}key) {}
                public function offsetUnset(${'$'}key) {}
            }
        """.trimIndent()

        private val USER = """
            <?php
            namespace XF\Entity;
            use XF\Mvc\Entity\Entity;
            /**
             * COLUMNS
             * @property int|null ${'$'}user_id
             * @property string ${'$'}username
             *
             * GETTERS
             * @property-read bool ${'$'}is_super_admin
             *
             * RELATIONS
             * @property-read \XF\Entity\UserProfile|null ${'$'}Profile
             */
            class User extends Entity
            {
                public function canSearch(&${'$'}error = null) {}
                public function hasPermission(${'$'}group, ${'$'}permission) {}
                public static function getStructure(${'$'}structure) {}
            }
        """.trimIndent()

        private val USER_PROFILE = """
            <?php
            namespace XF\Entity;
            use XF\Mvc\Entity\Entity;
            /**
             * COLUMNS
             * @property string ${'$'}about
             * @property string ${'$'}location
             */
            class UserProfile extends Entity
            {
            }
        """.trimIndent()

        private val TEMPLATER = """
            <?php
            namespace XF\Template;
            class Templater
            {
                protected ${'$'}defaultFunctions = [
                    'phrase' => 'fnPhrase',
                ];
                protected ${'$'}defaultFilters = [
                    'escape' => 'filterEscape',
                ];
                public function fnPhrase(${'$'}templater, &${'$'}escape, ${'$'}name, array ${'$'}params = []) {}
                public function filterEscape(${'$'}templater, ${'$'}value, &${'$'}escape, ${'$'}type = true) {}
            }
        """.trimIndent()

        private val LISTENER = """
            <?php
            namespace Demo\Test;
            class Listener
            {
                public static function templaterSetup(\XF\Container ${'$'}container, \XF\Template\Templater &${'$'}templater)
                {
                    ${'$'}templater->addFunction('demo_badge', function (\XF\Template\Templater ${'$'}templater, &${'$'}escape, ${'$'}user, ${'$'}size = 's') {
                        return '';
                    });
                    ${'$'}templater->addFunctions([
                        'demo_total' => [self::class, 'fnTotal'],
                    ]);
                    ${'$'}templater->addFilter('demo_money', [Listener::class, 'filterMoney']);
                }

                public static function fnTotal(${'$'}templater, &${'$'}escape, array ${'$'}items) {}

                public static function filterMoney(${'$'}templater, ${'$'}value, &${'$'}escape, ${'$'}currency = 'USD') {}
            }
        """.trimIndent()

        private val OPTIONS = """
            <options>
              <option option_id="boardTitle" edit_format="textbox" data_type="string" advanced="0">
                <default_value>XenForo</default_value>
              </option>
              <option option_id="registrationSetup" edit_format="template" data_type="array" advanced="0">
                <default_value>{}</default_value>
                <sub_options>enabled
            emailConfirmation</sub_options>
              </option>
            </options>
        """.trimIndent()
    }
}
