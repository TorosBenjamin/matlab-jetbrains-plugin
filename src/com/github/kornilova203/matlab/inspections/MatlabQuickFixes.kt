package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.imports.MatlabImports
import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.MatlabPackages
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType

/** Removes [name] from `import a.b c.*`, or the whole statement if it's the only name. */
class RemoveImportFix(statement: MatlabCommandStatement, private val name: String) : LocalQuickFixOnPsiElement(statement) {
    override fun getFamilyName(): String = "Remove import"
    override fun getText(): String = "Remove import '$name'"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val statement = startElement as? MatlabCommandStatement ?: return
        MatlabImports.setImportNames(statement, MatlabPackages.importArguments(statement).filter { it != name })
    }
}

/** Removes a function or method that's never used. */
class RemoveFunctionFix(function: MatlabFunctionDeclaration, private val description: String) : LocalQuickFixOnPsiElement(function) {
    override fun getFamilyName(): String = "Remove unused function"
    override fun getText(): String = "Remove $description"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val function = startElement as? MatlabFunctionDeclaration ?: return
        // The empty line before it goes too, so no gap is left
        val previous = function.prevSibling
        if (previous.elementType == MatlabTypes.NEWLINE && previous.prevSibling.elementType == MatlabTypes.NEWLINE) previous.delete()
        removeStatement(function)
    }
}

/** Removes a statement that can never run, e.g. after `return`. */
class RemoveUnreachableCodeFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
    override fun getFamilyName(): String = "Remove unreachable code"
    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        removeStatement(startElement)
    }
}

private fun removeStatement(element: PsiElement) = MatlabImports.removeStatement(element)
