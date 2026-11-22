package nt.xenforo.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.Lexer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataModifications
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.tree.IElementType
import nt.xenforo.psi.XfTokenSets
import nt.xenforo.psi.XfTypes

open class XfTemplateDataElementType(debugName: String, private val embedsBlocks: Boolean) :
    TemplateDataElementType(debugName, XfLanguage, XfTypes.CONTENT, XfTokenSets.OUTER) {

    override fun isInsertionToken(tokenType: IElementType?, tokenSequence: CharSequence): Boolean =
        XfTokenSets.INSERTION_STARTS.contains(tokenType)

    override fun collectTemplateModifications(sourceCode: CharSequence, baseLexer: Lexer): TemplateDataModifications {
        val insertions = if (embedsBlocks) XfEmbeddedBlocks.insertions(sourceCode, baseLexer) else emptyList()
        if (insertions.isEmpty()) return super.collectTemplateModifications(sourceCode, baseLexer)
        val modifications = TemplateDataModifications()
        var next = 0
        baseLexer.start(sourceCode)
        while (true) {
            val type = baseLexer.tokenType ?: break
            ProgressManager.checkCanceled()
            val start = baseLexer.tokenStart
            while (next < insertions.size && insertions[next].offset <= start) {
                modifications.addRangeToRemove(insertions[next].offset, insertions[next].text)
                next++
            }
            if (type === XfTypes.CONTENT) {
                modifications.addAll(appendCurrentTemplateToken(baseLexer.tokenEnd, baseLexer.tokenSequence))
            } else {
                modifications.addOuterRange(TextRange(start, baseLexer.tokenEnd), isInsertionToken(type, baseLexer.tokenSequence))
            }
            baseLexer.advance()
        }
        while (next < insertions.size) {
            modifications.addRangeToRemove(insertions[next].offset, insertions[next].text)
            next++
        }
        return modifications
    }

    companion object {
        val PLAIN = XfTemplateDataElementType("XF_TEMPLATE_DATA", false)
        val HTML = XfTemplateDataElementType("XF_HTML_TEMPLATE_DATA", true)
    }
}

class XfInsertion(val offset: Int, val text: String)

object XfEmbeddedBlocks {
    private val OPENERS = mapOf("js" to "<script>", "css" to "<style lang=\"less\">")
    private val CLOSERS = mapOf("js" to "</script>", "css" to "</style>")

    fun insertions(source: CharSequence, lexer: Lexer): List<XfInsertion> {
        val result = ArrayList<XfInsertion>()
        var pending: String? = null
        var open: String? = null
        var nameAfterOpen = false
        var nameAfterClose = false
        var closeStart = 0
        lexer.start(source)
        while (true) {
            val type = lexer.tokenType ?: break
            when (type) {
                XfTypes.TAG_OPEN -> nameAfterOpen = true
                XfTypes.TAG_CLOSE_OPEN -> {
                    nameAfterClose = true
                    closeStart = lexer.tokenStart
                }
                XfTypes.TAG_NAME -> {
                    val name = lexer.tokenSequence.toString().lowercase()
                    if (nameAfterOpen && open == null && name in OPENERS) pending = name
                    if (nameAfterClose && name == open) {
                        result += XfInsertion(closeStart, CLOSERS.getValue(name))
                        open = null
                    }
                    nameAfterOpen = false
                    nameAfterClose = false
                }
                XfTypes.TAG_END -> {
                    pending?.let {
                        result += XfInsertion(lexer.tokenEnd, OPENERS.getValue(it))
                        open = it
                    }
                    pending = null
                }
                XfTypes.TAG_SELF_CLOSE -> pending = null
            }
            lexer.advance()
        }
        if (open != null) result.removeAt(result.lastIndex)
        return result
    }
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
                (it as? PsiFileImpl)?.setContentElementType(if (dataLanguage.isKindOf(HTMLLanguage.INSTANCE)) XfTemplateDataElementType.HTML else XfTemplateDataElementType.PLAIN)
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
