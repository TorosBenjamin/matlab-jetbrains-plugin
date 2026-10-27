package com.github.kornilova203.matlab.highlighting

import com.github.kornilova203.matlab.structure.MatlabStructureElement
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

/** A line above each `%% Section` title, like MATLAB's editor. Can be turned off under Editor | General | Gutter Icons. */
class MatlabSectionSeparatorProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = "MATLAB section separators"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiComment || MatlabStructureElement.sectionTitle(element) == null) return null
        if (element.textOffset == 0) return null  // no line above the first line
        // The same separator Java draws between methods.
        return LineMarkersPass.createMethodSeparatorLineMarker(element, EditorColorsManager.getInstance())
    }
}
