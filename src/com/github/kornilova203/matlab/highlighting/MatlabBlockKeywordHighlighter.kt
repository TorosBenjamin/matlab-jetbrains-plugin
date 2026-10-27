package com.github.kornilova203.matlab.highlighting

import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.MatlabTypes.*
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Consumer

/**
 * With the caret on a block keyword (`if`, `elseif`, `else`, `end`, `function`, `case`, ...),
 * highlights all keywords of that block.
 *
 * @author Liudmila Kornilova
 **/
class MatlabBlockKeywordHighlighterFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is MatlabFile) return null
        val ranges = findBlockKeywords(target)
        if (ranges.isEmpty()) return null
        return object : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
            override fun getTargets(): List<PsiElement> = listOf(target)
            override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) =
                selectionConsumer.consume(targets)

            override fun computeUsages(targets: List<PsiElement>) {
                myReadUsages.addAll(ranges)
            }
        }
    }

    companion object {
        fun findBlockKeywords(target: PsiElement): List<TextRange> {
            var element = target
            val type = PsiUtilCore.getElementType(element)
            if (type == NEWLINE || type == WHITE_SPACE) element = PsiTreeUtil.prevLeaf(element) ?: return emptyList()
            val visitor = BlockVisitor(element)
            element.parent?.accept(visitor)
            return visitor.ranges
        }
    }

    private class BlockVisitor(private val element: PsiElement) : MatlabVisitor() {
        val ranges = mutableListOf<TextRange>()

        private fun firstAndEnd(o: PsiElement): MutableList<PsiElement> =
            if (PsiUtilCore.getElementType(o.lastChild) == END) mutableListOf(o.firstChild, o.lastChild) else mutableListOf(o.firstChild)

        private fun addIfCaretOnDelimiter(delimiters: List<PsiElement>) {
            // A block without its matching keyword (e.g. missing `end`) isn't highlighted.
            if (delimiters.size < 2 || element !in delimiters) return
            delimiters.mapTo(ranges) { it.textRange }
            ranges.sortBy { it.startOffset }
        }

        private fun highlightFirstAndEnd(o: PsiElement) = addIfCaretOnDelimiter(firstAndEnd(o))

        private fun <T : PsiElement> addChildKeywords(o: PsiElement, delimiters: MutableList<PsiElement>, clazz: Class<T>, type: IElementType) {
            PsiTreeUtil.getChildrenOfType(o, clazz)?.forEach { child ->
                child.node.findChildByType(type)?.psi?.let { delimiters.add(it) }
            }
        }

        override fun visitIfBlock(o: MatlabIfBlock) {
            val delimiters = firstAndEnd(o)
            addChildKeywords(o, delimiters, MatlabElseifBlock::class.java, ELSEIF)
            addChildKeywords(o, delimiters, MatlabElseBlock::class.java, ELSE)
            addIfCaretOnDelimiter(delimiters)
        }

        override fun visitTryBlock(o: MatlabTryBlock) {
            val delimiters = firstAndEnd(o)
            addChildKeywords(o, delimiters, MatlabCatchBlock::class.java, CATCH)
            addIfCaretOnDelimiter(delimiters)
        }

        override fun visitSwitchBlock(o: MatlabSwitchBlock) {
            val delimiters = firstAndEnd(o)
            addChildKeywords(o, delimiters, MatlabCaseBlock::class.java, CASE)
            addChildKeywords(o, delimiters, MatlabOtherwiseBlock::class.java, OTHERWISE)
            addIfCaretOnDelimiter(delimiters)
        }

        // The caret is on a keyword of a child block: highlight the whole parent block.
        override fun visitElseBlock(o: MatlabElseBlock) { (o.parent as? MatlabIfBlock)?.accept(this) }
        override fun visitElseifBlock(o: MatlabElseifBlock) { (o.parent as? MatlabIfBlock)?.accept(this) }
        override fun visitCatchBlock(o: MatlabCatchBlock) { (o.parent as? MatlabTryBlock)?.accept(this) }
        override fun visitCaseBlock(o: MatlabCaseBlock) { (o.parent as? MatlabSwitchBlock)?.accept(this) }
        override fun visitOtherwiseBlock(o: MatlabOtherwiseBlock) { (o.parent as? MatlabSwitchBlock)?.accept(this) }

        override fun visitFunctionDeclaration(o: MatlabFunctionDeclaration) = highlightFirstAndEnd(o)
        override fun visitMethodsBlock(o: MatlabMethodsBlock) = highlightFirstAndEnd(o)
        override fun visitPropertiesBlock(o: MatlabPropertiesBlock) = highlightFirstAndEnd(o)
        override fun visitEventsBlock(o: MatlabEventsBlock) = highlightFirstAndEnd(o)
        override fun visitClassDeclaration(o: MatlabClassDeclaration) = highlightFirstAndEnd(o)
        override fun visitForLoop(o: MatlabForLoop) = highlightFirstAndEnd(o)
        override fun visitWhileLoop(o: MatlabWhileLoop) = highlightFirstAndEnd(o)
    }
}
