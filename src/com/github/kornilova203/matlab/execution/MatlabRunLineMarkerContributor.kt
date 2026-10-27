package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.github.kornilova203.matlab.structure.MatlabStructureElement
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType

/** Run icons in the gutter: on each `%%` section title, and on a function file's main function. */
class MatlabRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element is PsiComment && element.parent is MatlabFile && MatlabStructureElement.sectionTitle(element) != null) {
            return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
        }
        if (element.elementType == MatlabTypes.FUNCTION) {
            val function = element.parent as? MatlabFunctionDeclaration ?: return null
            val file = function.parent as? MatlabFile ?: return null
            val first = file.children.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment && it.elementType != MatlabTypes.NEWLINE }
            if (first == function) return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
        }
        return null
    }
}
