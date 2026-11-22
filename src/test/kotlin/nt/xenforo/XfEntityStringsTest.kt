package nt.xenforo

import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import nt.xenforo.php.XfEntityFieldReference
import nt.xenforo.php.XfEntityStructures

class XfEntityStringsTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject(
            "src/XF/Mvc/Entity/Entity.php",
            "<?php\nnamespace XF\\Mvc\\Entity;\n\nabstract class Entity\n{\n\tconst UINT = 1;\n\tconst STR = 2;\n\tconst BOOL = 3;\n" +
                "\tpublic function get(\$key) {}\n\tpublic function isChanged(\$key) {}\n}\n",
        )
        myFixture.addFileToProject(
            "src/XF/Mvc/Entity/Finder.php",
            "<?php\nnamespace XF\\Mvc\\Entity;\n\nclass Finder\n{\n\t/** @return static */\n\tpublic function where(\$column, \$operator = null, \$value = null) { return \$this; }\n" +
                "\t/** @return static */\n\tpublic function order(\$column, \$direction = 'ASC') { return \$this; }\n" +
                "\t/** @return static */\n\tpublic function with(\$relations) { return \$this; }\n}\n",
        )
        myFixture.addFileToProject(
            "src/XF/Entity/User.php",
            "<?php\nnamespace XF\\Entity;\n\nuse XF\\Mvc\\Entity\\Entity;\n\nclass User extends Entity\n{\n" +
                "\tpublic static function getStructure(\$structure)\n\t{\n" +
                "\t\t\$structure->columns = [\n\t\t\t'user_id' => ['type' => self::UINT],\n\t\t\t'username' => ['type' => self::STR],\n\t\t];\n" +
                "\t\t\$structure->getters = ['is_super_admin' => true];\n" +
                "\t\t\$structure->relations = [\n\t\t\t'Profile' => ['entity' => 'XF:UserProfile', 'type' => self::TO_ONE],\n\t\t];\n" +
                "\t\treturn \$structure;\n\t}\n}\n",
        )
        myFixture.addFileToProject(
            "src/XF/Entity/UserProfile.php",
            "<?php\nnamespace XF\\Entity;\n\nclass UserProfile extends \\XF\\Mvc\\Entity\\Entity\n{\n\tpublic static function getStructure(\$structure)\n\t{\n" +
                "\t\t\$structure->columns = ['about' => ['type' => self::STR], 'location' => ['type' => self::STR]];\n\t\treturn \$structure;\n\t}\n}\n",
        )
        myFixture.addFileToProject("src/XF/Finder/UserFinder.php", "<?php\nnamespace XF\\Finder;\n\nclass UserFinder extends \\XF\\Mvc\\Entity\\Finder\n{\n}\n")
        myFixture.addFileToProject(
            "src/addons/Demo/Test/XF/Entity/User.php",
            "<?php\nnamespace Demo\\Test\\XF\\Entity;\n\nclass User extends XFCP_User\n{\n\tpublic static function getStructure(\$structure)\n\t{\n" +
                "\t\t\$structure = parent::getStructure(\$structure);\n\t\t\$structure->columns['demo_flag'] = ['type' => self::BOOL];\n\t\treturn \$structure;\n\t}\n}\n",
        )
    }

    fun testWhereListsColumnsRelationsAndExtensionColumns() {
        val variants = complete("\\XF::finder('XF:User')->where('<caret>');")
        assertContainsElements(variants, "user_id", "username", "demo_flag", "Profile")
        assertDoesntContain(variants, "is_super_admin")
    }

    fun testRelationPathAndVariableFinder() {
        val variants = complete("\$finder = \$this->finder('XF:User');\n\t\t\$finder->order('Profile.<caret>');")
        assertContainsElements(variants, "about", "location")
    }

    fun testWithListsOnlyRelations() {
        val variants = complete("\$this->finder(\\XF\\Finder\\UserFinder::class)->with(['<caret>']);")
        assertContainsElements(variants, "Profile")
        assertDoesntContain(variants, "username")
    }

    fun testEntityGetIncludesGetters() {
        val variants = complete("\$user = new \\XF\\Entity\\User();\n\t\t\$user->get('<caret>');")
        assertContainsElements(variants, "username", "is_super_admin", "demo_flag")
    }

    fun testFilterTypes() {
        assertContainsElements(complete("\$this->filter('user_id', '<caret>');"), "uint", "str", "array-uint")
        assertContainsElements(complete("\$this->filter(['title' => '<caret>']);"), "str", "bool")
    }

    fun testColumnOfExtensionResolves() {
        configurePhp("\\XF::finder('XF:User')->where('demo_fl<caret>ag', 1);")
        val found = myFixture.file.findReferenceAt(myFixture.caretOffset)
        val references = if (found is PsiMultiReference) found.references.toList() else listOfNotNull(found)
        val target = references.filterIsInstance<XfEntityFieldReference>().single().resolve() as StringLiteralExpression
        assertEquals("demo_flag", target.contents)
        assertTrue(target.containingFile.virtualFile.path.contains("/Demo/Test/XF/Entity/User.php"))
    }

    fun testExtensionColumnInTemplate() {
        configureTemplate("{{ \$xf.visitor.<caret> }}")
        val variants = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(variants, "demo_flag", "Profile")
    }

    fun testColumnsFromEntityStructureListeners() {
        addOutputFile(
            "code_event_listeners/entity_structure_demo.json",
            "{\n    \"event_id\": \"entity_structure\",\n    \"execute_order\": 10,\n    \"callback_class\": \"Demo\\\\Test\\\\Listener\",\n" +
                "    \"callback_method\": \"userEntityStructure\",\n    \"active\": true,\n    \"hint\": \"XF\\\\Entity\\\\User\",\n    \"description\": \"\"\n}",
        )
        myFixture.addFileToProject(
            "src/addons/Other/Tools/_data/code_event_listeners.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<code_event_listeners>\n" +
                "  <listener event_id=\"entity_structure\" execute_order=\"10\" callback_class=\"\\Demo\\Test\\Listener\" callback_method=\"profileEntityStructure\"" +
                " active=\"1\" hint=\"XF\\Entity\\UserProfile\" description=\"\"/>\n</code_event_listeners>",
        )
        myFixture.addFileToProject(
            "src/addons/Demo/Test/Listener.php",
            "<?php\nnamespace Demo\\Test;\n\nclass Listener\n{\n" +
                "\tpublic static function userEntityStructure(\$em, &\$structure)\n\t{\n" +
                "\t\t\$structure->columns['demo_listener_flag'] = ['type' => \\XF\\Mvc\\Entity\\Entity::BOOL];\n" +
                "\t\t\$structure->relations['DemoProfile'] = ['entity' => 'XF:UserProfile'];\n\t}\n\n" +
                "\tpublic static function profileEntityStructure(\$em, &\$structure)\n\t{\n" +
                "\t\t\$structure->columns['demo_profile_flag'] = ['type' => \\XF\\Mvc\\Entity\\Entity::STR];\n\t}\n}\n",
        )
        val user = complete("\\XF::finder('XF:User')->where('<caret>');")
        assertContainsElements(user, "user_id", "demo_listener_flag", "DemoProfile")
        assertDoesntContain(user, "demo_profile_flag")
        assertContainsElements(complete("\\XF::finder('XF:UserProfile')->where('<caret>');"), "about", "demo_profile_flag")
        assertEquals("bool", XfEntityStructures.of(project, "XF\\Entity\\User")["demo_listener_flag"]?.type)
    }

    private fun complete(statement: String): List<String> {
        configurePhp(statement)
        return myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
    }

    private var files = 0

    private fun configurePhp(statement: String) {
        files++
        val prefix = "<?php\n\nnamespace Demo\\Test\\Pub\\Controller;\n\nclass Demo$files\n{\n\tpublic function actionIndex()\n\t{\n\t\t"
        val caret = statement.indexOf("<caret>")
        val file = myFixture.addFileToProject("src/addons/Demo/Test/Pub/Controller/Demo$files.php", prefix + statement.replace("<caret>", "") + "\n\t}\n}\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(prefix.length + caret)
    }
}
