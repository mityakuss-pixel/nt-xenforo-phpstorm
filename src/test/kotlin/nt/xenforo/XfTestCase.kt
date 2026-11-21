package nt.xenforo

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class XfTestCase : BasePlatformTestCase() {
    protected fun configureTemplate(text: String, name: String = "test.html", type: String = "public"): PsiFile {
        val caret = text.indexOf(CARET)
        val content = if (caret >= 0) text.removeRange(caret, caret + CARET.length) else text
        val file = myFixture.addFileToProject("src/addons/Demo/Test/_output/templates/$type/$name", content)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.file
    }

    protected fun addOutputFile(path: String, text: String) {
        myFixture.addFileToProject("src/addons/Demo/Test/_output/$path", text)
    }

    protected fun addDataFile(name: String, text: String) {
        myFixture.addFileToProject("src/addons/XF/_data/$name", text)
    }

    private companion object {
        const val CARET = "<caret>"
    }
}
