package com.github.kornilova203.matlab.structure

import com.github.kornilova203.matlab.psi.MatlabFile
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager

/** `%% Title` code sections at the top level of a script. */
object MatlabSections {
    class Section(val title: String, val header: PsiComment, val firstLine: Int, val lastLine: Int)

    fun sections(file: MatlabFile): List<Section> {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
        val headers = file.children.filterIsInstance<PsiComment>().filter { MatlabStructureElement.sectionTitle(it) != null }
        return headers.mapIndexed { i, header ->
            val first = document.getLineNumber(header.textOffset)
            val last = if (i + 1 < headers.size) document.getLineNumber(headers[i + 1].textOffset) - 1 else lastLine(document)
            Section(MatlabStructureElement.sectionTitle(header)!!, header, first, last)
        }
    }

    /** The section containing [offset], if any. */
    fun sectionAt(file: MatlabFile, offset: Int): Section? {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
        val line = document.getLineNumber(offset.coerceIn(0, document.textLength))
        return sections(file).lastOrNull { it.firstLine <= line && line <= it.lastLine }
    }

    private fun lastLine(document: Document) = (document.lineCount - 1).coerceAtLeast(0)
}
