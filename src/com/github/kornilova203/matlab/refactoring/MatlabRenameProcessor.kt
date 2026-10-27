package com.github.kornilova203.matlab.refactoring

import com.github.kornilova203.matlab.psi.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * MATLAB ties names together: a constructor has its class's name, and a class or a file's main function
 * must match the file name. Renaming one of them renames the others.
 */
class MatlabRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is MatlabClassDeclaration || element is MatlabFunctionDeclaration

    /** Renaming a constructor renames its class (and with it the constructor, usages and file). */
    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? =
        (element as? MatlabFunctionDeclaration)?.let(::constructorClass) ?: element

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        when (element) {
            is MatlabClassDeclaration -> {
                for (constructor in constructors(element)) allRenames[constructor] = newName
                matchingFile(element)?.let { allRenames[it] = "$newName.m" }
            }
            is MatlabFunctionDeclaration -> {
                if (isMainFunction(element)) matchingFile(element)?.let { allRenames[it] = "$newName.m" }
            }
        }
    }

    private fun constructorClass(function: MatlabFunctionDeclaration): MatlabClassDeclaration? {
        if (function.parent !is MatlabMethodsList) return null
        val cls = PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java) ?: return null
        return cls.takeIf { name(it) == name(function) }
    }

    private fun constructors(cls: MatlabClassDeclaration): List<MatlabFunctionDeclaration> =
        cls.methodsBlockList.flatMap { it.methodsList?.functionDeclarationList.orEmpty() }
            .filter { name(it) == name(cls) }

    /** The first function of a function file: the one other files call by the file's name. */
    private fun isMainFunction(function: MatlabFunctionDeclaration): Boolean {
        val file = function.parent as? MatlabFile ?: return false
        return file.children.firstOrNull { it is MatlabFunctionDeclaration || it is MatlabExpr || it is MatlabClassDeclaration } == function
    }

    /** The containing file, if its name matches the element's (Shape.m for classdef Shape). */
    private fun matchingFile(element: PsiElement): PsiFile? {
        val file = element.containingFile ?: return null
        return file.takeIf { it.virtualFile?.nameWithoutExtension == name(element) }
    }

    private fun name(element: PsiElement) = (element as? PsiNamedElement)?.name
}
