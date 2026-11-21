package nt.xenforo.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import nt.xenforo.lang.XfFileType
import nt.xenforo.lang.XfLanguage
import nt.xenforo.lang.XfTemplateFiles

class XfFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, XfLanguage) {
    override fun getFileType(): FileType = XfFileType

    override fun toString(): String = "XenForo template"

    val templateType: String
        get() = XfTemplateFiles.templateType(originalFile.virtualFile ?: viewProvider.virtualFile) ?: XfTemplateFiles.DEFAULT_TYPE

    val templateTitle: String
        get() = XfTemplateFiles.templateTitle(originalFile.virtualFile ?: viewProvider.virtualFile)
}
