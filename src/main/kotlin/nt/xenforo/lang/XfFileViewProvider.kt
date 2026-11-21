package nt.xenforo.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.tree.IElementType
import nt.xenforo.psi.XfTokenSets
import nt.xenforo.psi.XfTypes

object XfTemplateDataElementType : TemplateDataElementType("XF_TEMPLATE_DATA", XfLanguage, XfTypes.CONTENT, XfTokenSets.OUTER) {
    override fun isInsertionToken(tokenType: IElementType?, tokenSequence: CharSequence): Boolean =
        XfTokenSets.INSERTION_STARTS.contains(tokenType)
}

class XfFileViewProvider(manager: PsiManager, file: VirtualFile, eventSystemEnabled: Boolean) :
    MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled), TemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language = XfLanguage

    private val dataLanguage: Language = XfTemplateFiles.dataLanguage(file) ?: HTMLLanguage.INSTANCE

    override fun getTemplateDataLanguage(): Language = dataLanguage

    override fun getLanguages(): Set<Language> = setOf(XfLanguage, dataLanguage)

    override fun supportsIncrementalReparse(rootLanguage: Language): Boolean = false

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        XfFileViewProvider(manager, fileCopy, false)

    override fun createFile(lang: Language): PsiFile? {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        return when {
            lang === XfLanguage -> definition.createFile(this)
            lang === dataLanguage -> definition.createFile(this).also {
                (it as? PsiFileImpl)?.setContentElementType(XfTemplateDataElementType)
            }
            else -> null
        }
    }
}

class XfFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = XfFileViewProvider(manager, file, eventSystemEnabled)
}
