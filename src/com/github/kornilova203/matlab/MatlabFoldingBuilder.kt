package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.github.kornilova203.matlab.structure.MatlabStructureElement


class MatlabFoldingBuilder : CustomFoldingBuilder() {
    override fun buildLanguageFoldRegions(descriptors: MutableList<FoldingDescriptor>, root: PsiElement, document: Document, quick: Boolean) {
        if (root !is MatlabFile) {
            return
        }

        appendDescriptors(root.node, descriptors)
        appendSectionDescriptors(root, descriptors)
    }

    /** `%% Title` code sections fold from the end of the title line to the next section in the same block. */
    private fun appendSectionDescriptors(root: PsiElement, descriptors: MutableList<FoldingDescriptor>) {
        val headers = PsiTreeUtil.collectElementsOfType(root, PsiComment::class.java)
            .filter { MatlabStructureElement.sectionTitle(it) != null }
            .groupBy { it.parent }
        for ((parent, sections) in headers) {
            val sorted = sections.sortedBy { it.textOffset }
            for ((i, header) in sorted.withIndex()) {
                val start = header.textRange.endOffset
                val end = if (i + 1 < sorted.size) contentEndBefore(sorted[i + 1]) else contentEnd(parent)
                if (end > start) descriptors.add(FoldingDescriptor(header.node, TextRange(start, end)))
            }
        }
    }

    /** End of the last non-blank element before [element]. */
    private fun contentEndBefore(element: PsiElement): Int =
        PsiTreeUtil.skipWhitespacesAndCommentsBackward(element)?.let { prev ->
            var e: PsiElement = prev
            while (e.node.elementType == MatlabTypes.NEWLINE) e = e.prevSibling ?: break
            e.textRange.endOffset
        } ?: element.textRange.startOffset

    /** End of the last non-blank element in [parent], before a closing `end`. */
    private fun contentEnd(parent: PsiElement): Int {
        var last = parent.lastChild
        while (last != null && (last is PsiWhiteSpace || last.node.elementType == MatlabTypes.NEWLINE ||
                    last.node.elementType == MatlabTypes.END)) last = last.prevSibling
        return last?.textRange?.endOffset ?: parent.textRange.endOffset
    }

    private fun appendDescriptors(node: ASTNode, descriptors: MutableList<FoldingDescriptor>) {
        val type = node.elementType
        val parentType = node.treeParent?.elementType
        if (type == MatlabTypes.BLOCK && parentType != MatlabTypes.CASE_BLOCK && parentType != MatlabTypes.OTHERWISE_BLOCK
                || type == MatlabTypes.SWITCH_BLOCK || type == MatlabTypes.CLASS_DECLARATION || type == MatlabTypes.EVENTS_LIST 
                || type == MatlabTypes.PROPERTIES_LIST || type == MatlabTypes.METHODS_LIST) {
            val start = getStartOffset(node)
            val end = getEndOffset(node)
            if (end > start) {
                val textRange = TextRange(start, end)
                descriptors.add(FoldingDescriptor(node, textRange))
            }
        }

        var child = node.firstChildNode
        while (child != null) {
            appendDescriptors(child, descriptors)
            child = child.treeNext
        }
    }

    private fun getStartOffset(node: ASTNode): Int {
        val child = when (node.elementType) {
            MatlabTypes.SWITCH_BLOCK -> node.findChildByType(MatlabTypes.SWITCH_EXPRESSION)
            MatlabTypes.CLASS_DECLARATION ->
                if (node.findChildByType(MatlabTypes.SUPER_CLASSES) != null) node.findChildByType(MatlabTypes.SUPER_CLASSES)
                else node.findChildByType(MatlabTypes.IDENTIFIER)
            else -> null
        }
        if (child != null) {
            return child.textRange.endOffset
        }

        // Start after the block's header line. Comments on their own lines (a function's help text) are
        // folded with the body; a comment at the end of the header line (`if x % check`) stays visible.
        var prev = node.treePrev
        while (prev != null) {
            val type = prev.elementType
            val isOwnLineComment = type == MatlabTypes.COMMENT && startsLine(prev)
            if (type != WHITE_SPACE && type != MatlabTypes.NEWLINE && !isOwnLineComment) {
                return prev.textRange.endOffset
            }
            prev = prev.treePrev
        }
        return node.textRange.startOffset
    }

    private fun startsLine(node: ASTNode): Boolean {
        var prev = node.treePrev
        while (prev != null && prev.elementType == WHITE_SPACE) prev = prev.treePrev
        return prev == null || prev.elementType == MatlabTypes.NEWLINE
    }

    private fun getEndOffset(node: ASTNode): Int {
        val type = node.elementType
        if (type == MatlabTypes.SWITCH_BLOCK || type == MatlabTypes.CLASS_DECLARATION) {
            return node.textRange.endOffset - MatlabTypes.END.toString().length
        }
        return findNotSpaceEnd(node)
    }

    private fun findNotSpaceEnd(node: ASTNode): Int {
        var next = node.treeNext
        while (next != null) {
            val type = next.elementType
            if (type != WHITE_SPACE && type != MatlabTypes.NEWLINE) {
                return next.textRange.startOffset
            }
            next = next.treeNext
        }
        return if (node.treeParent != null) findNotSpaceEnd(node.treeParent) else node.textRange.endOffset
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = "..."
}
