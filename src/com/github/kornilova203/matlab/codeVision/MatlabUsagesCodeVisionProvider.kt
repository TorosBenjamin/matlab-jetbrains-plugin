package com.github.kornilova203.matlab.codeVision

import com.github.kornilova203.matlab.psi.*
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import java.util.concurrent.atomic.AtomicInteger

/** "3 usages" above functions, classes and properties; clicking it shows the usages. */
class MatlabUsagesCodeVisionProvider : ReferencesCodeVisionProvider() {
    override fun acceptsFile(file: PsiFile): Boolean = file is MatlabFile

    override fun acceptsElement(element: PsiElement): Boolean =
        element is MatlabFunctionDeclaration || element is MatlabClassDeclaration || element is MatlabProperty

    override fun getHint(element: PsiElement, file: PsiFile): String? = usagesText(countUsages(element))

    /**
     * The base class only places hints on elements whose preceding sibling is whitespace with a line break.
     * MATLAB line breaks are NEWLINE tokens, so it would place none; put one on each accepted element.
     */
    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        if (file !is MatlabFile) return emptyList()
        val entries = ArrayList<Pair<TextRange, CodeVisionEntry>>()
        for (element in SyntaxTraverser.psiTraverser(file).filter(::acceptsElement)) {
            val hint = getHint(element, file) ?: continue
            val pointer = SmartPointerManager.createPointer(element)
            val entry = ClickableTextCodeVisionEntry(hint, id, { event, clickEditor ->
                pointer.element?.let { handleClick(clickEditor, it, event) }
            })
            entries += element.textRange to entry
        }
        return entries
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
    override val id: String = "matlab.usages"

    companion object {
        private const val LIMIT = 100

        fun countUsages(element: PsiElement): Int {
            val count = AtomicInteger()
            ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.project)).forEach { ref ->
                // A classdef signature declares the method rather than using it.
                ref is MatlabMethodSignatureReference || count.incrementAndGet() < LIMIT
            }
            return count.get()
        }

        fun usagesText(count: Int): String = when {
            count == 0 -> "no usages"
            count == 1 -> "1 usage"
            count >= LIMIT -> "$LIMIT+ usages"
            else -> "$count usages"
        }
    }
}
