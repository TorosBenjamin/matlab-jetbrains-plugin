package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.imports.MatlabImports
import com.github.kornilova203.matlab.psi.*
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor

/**
 * Greys out imports of project packages that nothing in the function (or script) uses.
 * Imports of packages outside the project (java.*, toolboxes) can't be checked.
 */
class MatlabUnusedImportInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : MatlabVisitor() {
        override fun visitCommandStatement(statement: MatlabCommandStatement) {
            val text = statement.commandArguments.text
            for (arg in MatlabImports.unusedImports(statement)) {
                val start = text.indexOf(arg).takeIf { it >= 0 } ?: continue
                holder.registerProblem(statement.commandArguments, "Unused import '$arg'",
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL, TextRange(start, start + arg.length), RemoveImportFix(statement, arg))
            }
        }
    }
}
