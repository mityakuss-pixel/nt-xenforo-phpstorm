package nt.xenforo.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.TemplateLanguageFileType
import nt.xenforo.XfBundle
import javax.swing.Icon

object XfFileType : LanguageFileType(XfLanguage), TemplateLanguageFileType {
    override fun getName(): String = "XenForo Template"

    override fun getDescription(): String = XfBundle.message("filetype.description")

    override fun getDefaultExtension(): String = "html"

    override fun getIcon(): Icon = XfIcons.FILE
}
