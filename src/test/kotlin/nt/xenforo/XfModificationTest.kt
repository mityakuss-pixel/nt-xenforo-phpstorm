package nt.xenforo

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import nt.xenforo.commands.XfAddOns
import nt.xenforo.modification.XfModificationCreation
import nt.xenforo.modification.XfModificationJsonInspection
import nt.xenforo.modification.XfModificationTemplateReference
import nt.xenforo.modification.XfModifications
import nt.xenforo.modification.XfTemplatesXmlMarkerService

class XfModificationTest : XfTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/addons/Demo/Test/addon.json", "{\"title\": \"Demo\"}")
        myFixture.addFileToProject("src/addons/Other/Mods/addon.json", "{\"title\": \"Mods\"}")
        addDataFile(
            "templates.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<templates>\n" +
                "  <template type=\"public\" title=\"forum_view\" addon_id=\"XF\" version_id=\"1\" version_string=\"1\"><![CDATA[<div class=\"block\">\n\t<span>Forum</span>\n</div>]]></template>\n" +
                "</templates>",
        )
    }

    fun testJsonAndXmlModificationsAreParsed() {
        addModification("demo_badge", "thread_view", "<h1>", "\$0<span>\\\"new\\\"</span>\\n", "Adds a badge")
        myFixture.addFileToProject(
            "src/addons/Other/Mods/_data/template_modifications.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<template_modifications>\n" +
                "  <modification type=\"public\" template=\"thread_view\" modification_key=\"other_mod\" description=\"Other\" execution_order=\"5\" enabled=\"0\" action=\"preg_replace\">\n" +
                "    <find><![CDATA[#<h1>(.*)</h1>#s]]></find>\n    <replace><![CDATA[<h2>\$1</h2>]]></replace>\n  </modification>\n" +
                "</template_modifications>",
        )
        val modifications = XfModifications.forTemplate(project, "public", "thread_view").associateBy { it.key }
        val demo = modifications.getValue("demo_badge")
        assertEquals("\$0<span>\"new\"</span>\n", demo.replace)
        assertEquals("Adds a badge", demo.description)
        assertEquals("Demo/Test", demo.addOnId)
        val other = modifications.getValue("other_mod")
        assertEquals("#<h1>(.*)</h1>#s", other.find)
        assertEquals("preg_replace", other.action)
        assertEquals(5, other.order)
        assertFalse(other.enabled)
    }

    fun testMatchesFollowXenForoActions() {
        val text = "<div class=\"a\">1</div>\n<DIV class=\"b\">2</DIV>"
        assertEquals(listOf(0), XfModifications.matches("str_replace", "<div class=\"a\">", text))
        assertEquals(2, XfModifications.matches("preg_replace", "/<div class=\"(\\w+)\">/i", text)?.size)
        assertEquals(1, XfModifications.matches("preg_replace", "{<div class=\"(?P<name>b)\">}i", text)?.size)
        assertNull(XfModifications.matches("preg_replace", "/(unclosed/", text))
        assertNull(XfModifications.matches("preg_replace", "#div#U", text))
        assertEquals(emptyList<Int>(), XfModifications.matches("str_replace", "missing", text))
    }

    fun testMarkersInTemplateFile() {
        addModification("demo_heading", "thread_view", "<h1>", "\$0!", "Heading", addOn = "Other/Mods")
        configureTemplate("<div>\n\t<h1>Title</h1>\n</div>", "thread_view.html")
        val gutters = myFixture.findAllGutters().filter { it.tooltipText?.contains("demo_heading") == true }
        assertEquals(1, gutters.size)
        assertTrue(gutters.single().tooltipText!!.contains("Other/Mods"))
    }

    fun testMarkersInTemplatesXml() {
        addModification("demo_forum", "forum_view", "<span>Forum</span>", "\$0", "Forum")
        val file = myFixture.findFileInTempDir("src/addons/XF/_data/templates.xml")
        myFixture.configureFromExistingVirtualFile(file)
        XfTemplatesXmlMarkerService.getInstance(project).applyNow(myFixture.editor, file)
        val markers = myFixture.editor.markupModel.allHighlighters.filter { it.gutterIconRenderer?.tooltipText?.contains("demo_forum") == true }
        assertEquals(3, myFixture.editor.document.getLineNumber(markers.single().startOffset))
        XfTemplatesXmlMarkerService.getInstance(project).applyNow(myFixture.editor, file)
        assertEquals(1, myFixture.editor.markupModel.allHighlighters.count { it.gutterIconRenderer?.tooltipText?.contains("demo_forum") == true })
    }

    fun testInspectionReportsStaleModification() {
        myFixture.enableInspections(XfModificationJsonInspection())
        val stale = addModification("demo_stale", "forum_view", "<span>Old</span>", "\$0", "Stale")
        myFixture.configureFromExistingVirtualFile(stale.virtualFile)
        assertEquals(listOf("Find text is not found in public:forum_view"), warnings())
        val missing = addModification("demo_missing", "no_such_template", "<span>", "\$0", "Missing")
        myFixture.configureFromExistingVirtualFile(missing.virtualFile)
        assertEquals(listOf("Template public:no_such_template is not found"), warnings())
        val fine = addModification("demo_fine", "forum_view", "<span>Forum</span>", "\$0", "Fine")
        myFixture.configureFromExistingVirtualFile(fine.virtualFile)
        assertEmpty(warnings())
    }

    fun testTemplateValueResolves() {
        val file = addModification("demo_ref", "forum_view", "<span>Forum</span>", "\$0", "Ref")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val offset = file.text.indexOf("forum_view") + 2
        val reference = myFixture.file.findReferenceAt(offset)
        assertInstanceOf(reference, XfModificationTemplateReference::class.java)
        assertNotNull((reference as XfModificationTemplateReference).multiResolve(false).singleOrNull())
    }

    fun testSelectionInTemplatesXml() {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("src/addons/XF/_data/templates.xml"))
        val text = myFixture.editor.document.text
        val start = text.indexOf("<span>")
        myFixture.editor.selectionModel.setSelection(start, start + "<span>Forum</span>".length)
        val selection = XfModificationCreation.selection(myFixture.editor, myFixture.file.virtualFile)!!
        assertEquals("public", selection.type)
        assertEquals("forum_view", selection.title)
        assertEquals("<span>Forum</span>", selection.find)
    }

    fun testCreateWithoutMakeCommandWritesJson() {
        val root = myFixture.findFileInTempDir("src").parent
        val addOn = XfAddOns.find(myFixture.findFileInTempDir("src/addons/Demo/Test/addon.json"), root)!!
        val selection = nt.xenforo.modification.XfTemplateSelection("public", "forum_view", "<span>Forum</span>", "")
        XfModificationCreation.create(project, addOn, selection, "demo_new", "New badge", "str_replace", "<span>Forum</span>", "\$0\n\t<b>Новое</b>", 20)
        val json = VfsUtilCore.loadText(myFixture.findFileInTempDir("src/addons/Demo/Test/_output/template_modifications/public/demo_new.json"))
        assertEquals(
            "{\n    \"template\": \"forum_view\",\n    \"description\": \"New badge\",\n    \"execution_order\": 20,\n    \"enabled\": true,\n" +
                "    \"action\": \"str_replace\",\n    \"find\": \"<span>Forum</span>\",\n" +
                "    \"replace\": \"\$0\\n\\t<b>\\u041d\\u043e\\u0432\\u043e\\u0435</b>\"\n}",
            json,
        )
    }

    fun testSuggestedKey() {
        assertEquals("nt_profile_member_view", XfModificationCreation.suggestKey("NT/Profile", "member_view"))
    }

    private fun warnings(): List<String> =
        myFixture.doHighlighting().filter { it.inspectionToolId == "XfTemplateModification" }.mapNotNull { it.description }

    private fun addModification(key: String, template: String, find: String, replace: String, description: String, addOn: String = "Demo/Test"): PsiFile =
        myFixture.addFileToProject(
            "src/addons/$addOn/_output/template_modifications/public/$key.json",
            "{\n    \"template\": \"$template\",\n    \"description\": \"$description\",\n    \"execution_order\": 10,\n    \"enabled\": true,\n" +
                "    \"action\": \"str_replace\",\n    \"find\": \"${find.replace("\"", "\\\"")}\",\n    \"replace\": \"$replace\"\n}",
        )
}
