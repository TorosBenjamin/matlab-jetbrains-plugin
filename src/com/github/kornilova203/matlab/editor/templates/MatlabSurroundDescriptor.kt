package com.github.kornilova203.matlab.editor.templates

import com.github.kornilova203.matlab.psi.MatlabBlock
import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType

/** Code | Surround With (Ctrl+Alt+T): wrap the selected statements in if/for/while/try ... end. */
class MatlabSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        if (file !is MatlabFile) return PsiElement.EMPTY_ARRAY
        var first = file.findElementAt(startOffset)?.let(::skipBlank) ?: return PsiElement.EMPTY_ARRAY
        // Climb to the statement level: a direct child of the file or of a block.
        while (first.parent !is MatlabFile && first.parent !is MatlabBlock) first = first.parent ?: return PsiElement.EMPTY_ARRAY
        val statements = ArrayList<PsiElement>()
        var e: PsiElement? = first
        while (e != null && e.textRange.startOffset < endOffset) {
            if (e.textRange.endOffset > endOffset && e !is PsiWhiteSpace && e.elementType != MatlabTypes.NEWLINE) break
            statements += e
            e = e.nextSibling
        }
        while (statements.isNotEmpty() && isBlank(statements.last())) statements.removeAt(statements.size - 1)
        return statements.toTypedArray()
    }

    override fun getSurrounders(): Array<Surrounder> = SURROUNDERS
    override fun isExclusive(): Boolean = false

    private fun isBlank(e: PsiElement) = e is PsiWhiteSpace || e.elementType == MatlabTypes.NEWLINE
    private fun skipBlank(e: PsiElement): PsiElement? {
        var x: PsiElement? = e
        while (x != null && isBlank(x)) x = x.nextSibling ?: x.parent?.nextSibling
        return x
    }

    private class BlockSurrounder(private val name: String, private val header: String, private val placeholder: String?,
                                  private val footer: String = "") : Surrounder {
        override fun getTemplateDescription(): String = name
        override fun isApplicable(elements: Array<PsiElement>): Boolean = elements.isNotEmpty()

        override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
            val document = editor.document
            val firstLine = document.getLineNumber(elements.first().textRange.startOffset)
            val lastLine = document.getLineNumber(elements.last().textRange.endOffset)
            val start = document.getLineStartOffset(firstLine)
            val end = document.getLineEndOffset(lastLine)
            val lines = document.charsSequence.subSequence(start, end).toString().lines()
            val indent = lines.first().takeWhile { it == ' ' || it == '\t' }
            val body = lines.joinToString("\n") { if (it.isBlank()) it else "    $it" }
            val middle = if (footer.isEmpty()) "" else "$indent$footer\n$indent    \n"
            val text = "$indent$header\n$body\n$middle${indent}end"
            document.replaceString(start, end, text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            // Select the placeholder (e.g. the condition) so it can be typed over.
            val at = placeholder?.let { text.indexOf(it) } ?: -1
            return if (at >= 0) TextRange(start + at, start + at + placeholder!!.length)
            else TextRange.from(start + text.length, 0)
        }
    }

    companion object {
        private val SURROUNDERS: Array<Surrounder> = arrayOf(
            BlockSurrounder("if", "if condition", "condition"),
            BlockSurrounder("if / else", "if condition", "condition", "else"),
            BlockSurrounder("for", "for i = 1:n", "1:n"),
            BlockSurrounder("while", "while condition", "condition"),
            BlockSurrounder("try / catch", "try", null, "catch err"),
        )
    }
}
