package com.github.kornilova203.matlab.foreign

import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.createElementFromText
import com.github.kornilova203.matlab.psi.impl.MatlabCommandArgumentsImpl
import com.github.kornilova203.matlab.psi.impl.MatlabLiteralExprImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement

/** Changes text inside a string literal, e.g. when a referenced file or function is renamed. */
class MatlabLiteralManipulator : AbstractElementManipulator<MatlabLiteralExprImpl>() {
    override fun handleContentChange(element: MatlabLiteralExprImpl, range: TextRange, newContent: String): MatlabLiteralExprImpl? {
        val replacement = createElementFromText(element.project, range.replace(element.text, newContent))
        return element.replace(replacement) as? MatlabLiteralExprImpl
    }

    override fun getRangeInElement(element: MatlabLiteralExprImpl): TextRange =
        if (element.textLength >= 2) TextRange(1, element.textLength - 1) else TextRange.EMPTY_RANGE
}

/** Changes text in command arguments (`mex foo.c`) by re-parsing the whole statement. */
class MatlabCommandArgumentsManipulator : AbstractElementManipulator<MatlabCommandArgumentsImpl>() {
    override fun handleContentChange(element: MatlabCommandArgumentsImpl, range: TextRange, newContent: String): MatlabCommandArgumentsImpl? {
        val statement = element.parent as? MatlabCommandStatement ?: return null
        val start = element.startOffsetInParent
        val newText = range.shiftRight(start).replace(statement.text, newContent)
        val replacement: PsiElement = createElementFromText(element.project, newText) as? MatlabCommandStatement ?: return null
        return (statement.replace(replacement) as? MatlabCommandStatement)?.commandArguments as? MatlabCommandArgumentsImpl
    }
}
