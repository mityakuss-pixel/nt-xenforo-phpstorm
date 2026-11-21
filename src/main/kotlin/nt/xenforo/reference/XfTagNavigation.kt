package nt.xenforo.reference

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import nt.xenforo.lang.XfFileViewProvider
import nt.xenforo.lang.XfLanguage
import nt.xenforo.model.XfTagCatalog
import nt.xenforo.psi.XfTypes

class XfTagGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val provider = sourceElement?.containingFile?.viewProvider as? XfFileViewProvider ?: return null
        val element = provider.findElementAt(offset, XfLanguage) ?: return null
        if (element.elementType != XfTypes.TAG_NAME) return null
        val tagClass = XfTagCatalog.tagClass(element.project, element.text) ?: return null
        return arrayOf(tagClass)
    }
}
