package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.MatlabPackages
import com.github.kornilova203.matlab.psi.MatlabVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor

/**
 * Reports imports of project packages that don't exist: `import geometry.Pointt`, `import geometyr.*`.
 * Imports whose first package isn't in the project (java.*, toolboxes, matlab.*) can't be checked and
 * are left alone.
 */
class MatlabUnresolvedImportInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : MatlabVisitor() {
        override fun visitCommandStatement(statement: MatlabCommandStatement) {
            if (statement.refExpr.text != "import") return
            val args = statement.commandArguments
            val project = statement.project
            for (arg in MatlabPackages.importArguments(statement)) {
                val parts = arg.split('.')
                if (!MatlabPackages.isPackage(project, parts[0])) continue
                val problem = when {
                    arg.endsWith(".*") -> arg.removeSuffix(".*").takeIf { !MatlabPackages.isPackage(project, it) }
                    else -> {
                        val pkg = arg.substringBeforeLast('.', "")
                        val name = arg.substringAfterLast('.')
                        arg.takeIf { pkg.isEmpty() || !MatlabPackages.isPackage(project, pkg) ||
                                MatlabPackages.members(project, pkg).none { it.name == name } }
                    }
                } ?: continue
                val start = args.text.indexOf(arg)
                if (start < 0) continue
                holder.registerProblem(args, "Cannot find '$problem'", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                    TextRange(start, start + arg.length))
            }
        }
    }
}
