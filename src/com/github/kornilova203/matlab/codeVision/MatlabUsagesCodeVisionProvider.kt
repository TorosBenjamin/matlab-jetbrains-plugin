package com.github.kornilova203.matlab.codeVision

import com.github.kornilova203.matlab.psi.*
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
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

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
    override val id: String = "matlab.usages"

    companion object {
        private const val LIMIT = 100

        fun countUsages(element: PsiElement): Int {
            val count = AtomicInteger()
            ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.project)).forEach { _ ->
                count.incrementAndGet() < LIMIT
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
