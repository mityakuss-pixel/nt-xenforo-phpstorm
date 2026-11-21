package nt.xenforo.format

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.templateLanguages.BlockWithParent
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.formatter.FormattingDocumentModelImpl
import com.intellij.psi.formatter.xml.HtmlPolicy
import com.intellij.psi.formatter.xml.SyntheticBlock
import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlTag
import nt.xenforo.psi.XfTokenSets
import nt.xenforo.psi.XfTypes

private val TAG_HEADERS = TokenSet.create(XfTypes.START_TAG, XfTypes.BRANCH_TAG)

private val LEAF_BLOCKS = TokenSet.create(XfTypes.EXPRESSION_BLOCK, XfTypes.VAR_BLOCK, XfTypes.ATTRIBUTE, XfTypes.END_TAG)

class XfFormattingModelBuilder : TemplateLanguageFormattingModelBuilder() {
    override fun createTemplateLanguageBlock(
        node: ASTNode,
        wrap: Wrap?,
        alignment: Alignment?,
        foreignChildren: MutableList<DataLanguageBlockWrapper>?,
        codeStyleSettings: CodeStyleSettings,
    ): TemplateLanguageBlock {
        val documentModel = FormattingDocumentModelImpl.createOn(node.psi.containingFile)
        return XfBlock(node, wrap, alignment, this, codeStyleSettings, foreignChildren, HtmlPolicy(codeStyleSettings, documentModel))
    }

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        if (formattingContext.node.elementType == XfTokenSets.OUTER) {
            return SimpleTemplateLanguageFormattingModelBuilder().createModel(formattingContext)
        }
        val file = formattingContext.containingFile
        val settings = formattingContext.codeStyleSettings
        val root = getRootBlock(file, file.viewProvider, settings)
        return DocumentBasedFormattingModel(root, formattingContext.project, settings, file.fileType, file)
    }

    override fun dontFormatMyModel(): Boolean = false
}

private class XfBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    factory: TemplateLanguageBlockFactory,
    settings: CodeStyleSettings,
    foreignChildren: List<DataLanguageBlockWrapper>?,
    private val htmlPolicy: HtmlPolicy,
) : TemplateLanguageBlock(node, wrap, alignment, factory, settings, foreignChildren) {

    override fun isLeaf(): Boolean = myNode.elementType in LEAF_BLOCKS || super.isLeaf()

    override fun buildChildren(): MutableList<Block> =
        if (myNode.elementType in LEAF_BLOCKS) mutableListOf() else super.buildChildren()

    override fun getIndent(): Indent? {
        if (myNode.text.isBlank()) return Indent.getNoneIndent()
        val type = myNode.elementType
        val parentType = myNode.treeParent?.elementType

        if (parentType in TAG_HEADERS) {
            return if (type == XfTypes.ATTRIBUTE || (type == XfTypes.TAG_SELF_CLOSE && startsLine())) Indent.getNormalIndent() else Indent.getNoneIndent()
        }

        if (type == XfTypes.BODY) {
            val foreignParent = foreignBlockParent(immediate = false) ?: return Indent.getNormalIndent()
            val xmlTag = foreignParent.node as? XmlTag
            return if (xmlTag != null && !htmlPolicy.indentChildrenOf(xmlTag)) Indent.getNormalIndent() else Indent.getNoneIndent()
        }

        if (parentType == XfTypes.BODY) {
            val parentBlock = parent
            if (parentBlock is XfBlock && parentBlock.indent == Indent.getNoneIndent()) return Indent.getNormalIndent()
        }

        val foreignParent = foreignBlockParent(immediate = true) ?: return Indent.getNoneIndent()
        val xmlTag = foreignParent.node as? XmlTag
        return if (xmlTag != null && !htmlPolicy.indentChildrenOf(xmlTag)) Indent.getNoneIndent() else Indent.getNormalIndent()
    }

    private fun startsLine(): Boolean {
        val text = myNode.psi.containingFile.viewProvider.contents
        var offset = myNode.startOffset - 1
        while (offset >= 0 && (text[offset] == ' ' || text[offset] == '\t')) offset--
        return offset < 0 || text[offset] == '\n'
    }

    override fun getTemplateTextElementType(): IElementType = XfTypes.CONTENT

    override fun isRequiredRange(range: TextRange): Boolean = false

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val type = myNode.elementType
        val indented = type in TAG_HEADERS || type == XfTypes.TAG ||
            (parent is DataLanguageBlockWrapper && (type != XfTypes.BODY || newChildIndex != 0))
        return ChildAttributes(if (indented) Indent.getNormalIndent() else Indent.getNoneIndent(), null)
    }

    private fun foreignBlockParent(immediate: Boolean): DataLanguageBlockWrapper? {
        var current: BlockWithParent? = parent
        while (current != null) {
            if (current is DataLanguageBlockWrapper && current.original !is SyntheticBlock) return current
            if (immediate && current is XfBlock) return null
            current = current.parent
        }
        return null
    }
}
