package com.github.kornilova203.matlab.structure

import com.github.kornilova203.matlab.MatlabLanguage
import com.github.kornilova203.matlab.psi.*
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

/** Editor breadcrumbs: Shape › area(obj) › if nargin > 1 */
class MatlabBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(MatlabLanguage.INSTANCE)

    override fun acceptElement(element: PsiElement): Boolean = when (element) {
        is MatlabClassDeclaration, is MatlabFunctionDeclaration, is MatlabIfBlock, is MatlabElseifBlock,
        is MatlabElseBlock, is MatlabForLoop, is MatlabParforLoop, is MatlabWhileLoop, is MatlabSwitchBlock,
        is MatlabCaseBlock, is MatlabOtherwiseBlock, is MatlabTryBlock, is MatlabCatchBlock,
        is MatlabPropertiesBlock, is MatlabMethodsBlock, is MatlabEventsBlock, is MatlabEnumerationBlock -> true
        else -> false
    }

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is MatlabClassDeclaration -> (element as PsiNamedElement).name ?: "classdef"
        is MatlabFunctionDeclaration ->
            (element.getterOrSetterModifier?.text ?: "") + ((element as PsiNamedElement).name ?: "") +
                    (element.parameters?.text ?: "()")
        is MatlabIfBlock -> "if " + short(element.condition)
        is MatlabElseifBlock -> "elseif " + short(element.condition)
        is MatlabElseBlock -> "else"
        is MatlabForLoop -> "for " + short(element.forLoopRange)
        is MatlabParforLoop -> "parfor"
        is MatlabWhileLoop -> "while " + short(element.whileLoopCondition)
        is MatlabSwitchBlock -> "switch " + short(element.switchExpression)
        is MatlabCaseBlock -> "case " + short(element.caseExpression)
        is MatlabOtherwiseBlock -> "otherwise"
        is MatlabTryBlock -> "try"
        is MatlabCatchBlock -> "catch"
        is MatlabPropertiesBlock -> "properties" + (element.attributes?.text?.let { " $it" } ?: "")
        is MatlabMethodsBlock -> "methods" + (element.attributes?.text?.let { " $it" } ?: "")
        is MatlabEventsBlock -> "events"
        is MatlabEnumerationBlock -> "enumeration"
        else -> ""
    }

    private fun short(element: PsiElement?): String {
        val text = element?.text?.replace(Regex("\\s+"), " ")?.trim() ?: return ""
        return if (text.length > 30) text.take(29) + "…" else text
    }
}
