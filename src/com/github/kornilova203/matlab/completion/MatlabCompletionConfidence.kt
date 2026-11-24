package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.util.ThreeState

/** No completion popping up while typing in a comment or a string (Ctrl+Space still works there). */
class MatlabCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
        if (isCommentOrString(contextElement)) ThreeState.YES else ThreeState.UNSURE

    companion object {
        fun isCommentOrString(element: PsiElement): Boolean =
            element is PsiComment || element.elementType in STRINGS || element.parent?.elementType in STRINGS

        private val STRINGS = setOf(MatlabTypes.SINGLE_QUOTE_STRING, MatlabTypes.DOUBLE_QUOTE_STRING)
    }
}
