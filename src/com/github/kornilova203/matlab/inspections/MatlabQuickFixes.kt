package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.github.kornilova203.matlab.psi.deleteExpr
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType

/** Removes [name] from `import a.b c.*`, or the whole statement if it's the only name. */
class RemoveImportFix(statement: MatlabCommandStatement, private val name: String) : LocalQuickFixOnPsiElement(statement) {
    override fun getFamilyName(): String = "Remove import"
    override fun getText(): String = "Remove import '$name'"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val statement = startElement as? MatlabCommandStatement ?: return
        val arguments = statement.commandArguments
        val text = arguments.text
        val match = Regex("(^|\\s)(${Regex.escape(name)})(?=$|[\\s,;])").find(text) ?: return
        val start = match.groups[2]!!.range.first
        val others = Regex("[^\\s,;%]+").findAll(text.substringBefore('%')).count() > 1
        if (!others) {
            removeStatement(statement)
            return
        }
        // The name and the whitespace after it, or before it if it's the last one
        var end = start + name.length
        var from = start
        val after = text.substring(end).takeWhile { it == ' ' || it == '\t' }.length
        if (after > 0 && end + after < text.length && text[end + after] !in ";,%") end += after
        else from = start - text.substring(0, start).reversed().takeWhile { it == ' ' || it == '\t' }.length
        ElementManipulators.handleContentChange(arguments, TextRange(from, end), "")
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

/** Deletes [element] with its `;`, the line break after it, and the indentation before it. */
private fun removeStatement(element: PsiElement) {
    val indent = element.prevSibling as? PsiWhiteSpace
    deleteExpr(element)
    if (indent != null && indent.isValid && !indent.textContains('\n')) indent.delete()
}
