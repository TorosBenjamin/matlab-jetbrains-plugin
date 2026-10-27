package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.types.ownScope
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Greys out imports of project packages that nothing in the function (or script) uses.
 * Imports of packages outside the project (java.*, toolboxes) can't be checked.
 */
class MatlabUnusedImportInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : MatlabVisitor() {
        override fun visitCommandStatement(statement: MatlabCommandStatement) {
            if (statement.refExpr.text != "import") return
            val project = statement.project
            val args = MatlabPackages.importArguments(statement)
                .filter { MatlabPackages.isPackage(project, it.substringBefore('.')) }
            if (args.isEmpty()) return
            // Packages of everything used by plain name in this scope.
            val used = HashSet<String>()
            for (ref in PsiTreeUtil.findChildrenOfType(ownScope(statement), MatlabRefExpr::class.java)) {
                if (ref.parent is MatlabQualifiedExpr && ref.parent.firstChild != ref) continue
                val target = ref.reference?.resolve() ?: continue
                val file = target.containingFile?.virtualFile ?: continue
                val dir = if (file.parent?.name?.startsWith("@") == true) file.parent.parent else file.parent
                val pkg = MatlabPackages.packageOf(dir) ?: continue
                used += pkg
                used += "$pkg.${ref.text}"
            }
            val text = statement.commandArguments.text
            for (arg in args) {
                val isUsed = if (arg.endsWith(".*")) arg.removeSuffix(".*") in used else arg in used
                if (isUsed) continue
                val start = text.indexOf(arg).takeIf { it >= 0 } ?: continue
                holder.registerProblem(statement.commandArguments, "Unused import '$arg'",
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL, TextRange(start, start + arg.length))
            }
        }
    }
}
