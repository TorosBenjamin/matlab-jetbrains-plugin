package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.psi.*

import com.github.kornilova203.matlab.psi.MatlabDeclaration
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

/**
 * @author Liudmila Kornilova
 **/
class MatlabFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner? = null

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = (element as? PsiNamedElement)?.name ?: ""

    override fun getDescriptiveName(element: PsiElement): String = (element as? PsiNamedElement)?.name ?: ""

    override fun getType(element: PsiElement): String = when (element) {
        is MatlabClassDeclaration -> "class"
        is MatlabFunctionDeclaration -> if (element.parent is MatlabMethodsList) "method" else "function"
        is MatlabProperty -> "property"
        is MatlabParameter -> "parameter"
        is MatlabRetValue -> "output"
        is MatlabEnumItem -> "enumeration member"
        is MatlabGlobalVariableDeclaration -> "global variable"
        is MatlabPersistentVariable -> "persistent variable"
        else -> "variable"
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is MatlabDeclaration
}
