package nt.xenforo

import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class XfOutputJsonTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\", \"version_id\": 1000070, \"version_string\": \"1.0.0\"}")
        myFixture.addFileToProject("src/XF/Entity/User.php", "<?php\nnamespace XF\\Entity;\nclass User {}\n")
        myFixture.addFileToProject("src/addons/Demo/Test/Listener.php", "<?php\nnamespace Demo\\Test;\nclass Listener\n{\n\tpublic static function userStructure() {}\n}\n")
        myFixture.addFileToProject("src/addons/Demo/Test/Pub/Controller/DemoController.php", "<?php\nnamespace Demo\\Test\\Pub\\Controller;\nclass DemoController {}\n")
        myFixture.addFileToProject("src/addons/Demo/Test/Entity/Note.php", "<?php\nnamespace Demo\\Test\\Entity;\nclass Note {}\n")
    }

    fun testListenerCallbackAndHint() {
        val file = "code_event_listeners/entity_structure_1.json"
        addOutputFile(
            file,
            "{\n    \"event_id\": \"entity_structure\",\n    \"execute_order\": 10,\n    \"callback_class\": \"Demo\\\\Test\\\\Listener\",\n" +
                "    \"callback_method\": \"userStructure\",\n    \"active\": true,\n    \"hint\": \"XF\\\\Entity\\\\User\",\n    \"description\": \"\"\n}",
        )
        open(file)
        assertEquals("\\Demo\\Test\\Listener", (resolveAt("Demo\\\\Test\\\\Listener") as PhpClass).fqn)
        assertEquals("userStructure", (resolveAt("\"userStructure") as Method).name)
        assertEquals("\\XF\\Entity\\User", (resolveAt("XF\\\\Entity\\\\User") as PhpClass).fqn)
    }

    fun testRouteControllerAndContentTypeEntity() {
        addOutputFile(
            "routes/public_demo_.json",
            "{\n    \"route_type\": \"public\",\n    \"route_prefix\": \"demo\",\n    \"sub_name\": \"\",\n    \"controller\": \"Demo\\\\Test:Demo\"\n}",
        )
        open("routes/public_demo_.json")
        assertEquals("\\Demo\\Test\\Pub\\Controller\\DemoController", (resolveAt("Demo\\\\Test:Demo") as PhpClass).fqn)
        addOutputFile("content_type_fields/demo_note-entity.json", "{\n    \"content_type\": \"demo_note\",\n    \"field_name\": \"entity\",\n    \"field_value\": \"Demo\\\\Test:Note\"\n}")
        open("content_type_fields/demo_note-entity.json")
        assertEquals("\\Demo\\Test\\Entity\\Note", (resolveAt("Demo\\\\Test:Note") as PhpClass).fqn)
    }

    fun testAddOnJsonSchema() {
        myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"))
        myFixture.editor.document.let { document ->
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                document.setText("{\n  \"title\": \"Demo\",\n  \"version_id\": \"1.0\",\n  \"version_string\": \"1.0.0\",\n  \"author\": \"me\"\n}")
            }
        }
        val messages = myFixture.doHighlighting().mapNotNull { it.description }
        assertTrue(messages.toString(), messages.any { it.contains("author") })
        assertTrue(messages.toString(), messages.any { it.contains("Incompatible types") || it.contains("integer") })
    }

    private fun open(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("src/addons/Demo/Test/_output/$path"))
    }

    private fun resolveAt(fragment: String): Any? {
        val offset = myFixture.file.text.indexOf(fragment) + 2
        return myFixture.file.findReferenceAt(offset)?.resolve()
    }
}
