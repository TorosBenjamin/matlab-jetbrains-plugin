package com.github.kornilova203.matlab.imports

import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabRefExpr
import com.github.kornilova203.matlab.psi.MatlabVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * `Point(1)` where Point only exists in a package (+geometry/Point.m): MATLAB won't find it without
 * `import geometry.Point` or writing `geometry.Point(1)`. Offers both.
 */
class MatlabUnimportedPackageMemberInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : MatlabVisitor() {
        override fun visitRefExpr(ref: MatlabRefExpr) {
            val candidates = MatlabImports.candidates(ref)
            if (candidates.isEmpty()) return
            val packages = candidates.joinToString(", ") { it.substringBeforeLast('.') }
            val fixes = candidates.flatMap { listOf(ImportFix(ref, it), QualifyFix(ref, it)) }
            holder.registerProblem(ref, "'${ref.text}' is in package $packages; import it or write the package name",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes.toTypedArray())
        }
    }
}

/** Adds `import pkg.Name` to the function (or script). */
class ImportFix(ref: MatlabRefExpr, private val qualifiedName: String) : LocalQuickFixOnPsiElement(ref) {
    override fun getFamilyName(): String = "Import"
    override fun getText(): String = "Import $qualifiedName"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        MatlabImports.addImport(startElement, qualifiedName)
    }
}

/** Writes the package name: Point(1) -> geometry.Point(1). */
class QualifyFix(ref: MatlabRefExpr, private val qualifiedName: String) : LocalQuickFixOnPsiElement(ref) {
    override fun getFamilyName(): String = "Qualify with package name"
    override fun getText(): String = "Qualify as $qualifiedName"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        MatlabImports.qualify(startElement as? MatlabRefExpr ?: return, qualifiedName)
    }
}

/** Code | Optimize Imports (Ctrl+Alt+O): removes unused imports of project and MATLAB path packages. */
class MatlabImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is MatlabFile

    override fun processFile(file: PsiFile): Runnable {
        val pointers = SmartPointerManager.getInstance(file.project)
        // Decided before changing anything; later statements first, so earlier ones keep their place
        val changes = PsiTreeUtil.findChildrenOfType(file, MatlabCommandStatement::class.java)
            .mapNotNull { statement ->
                val unused = MatlabImports.unusedImports(statement)
                if (unused.isEmpty()) null
                else pointers.createSmartPsiElementPointer(statement) to
                        com.github.kornilova203.matlab.psi.MatlabPackages.importArguments(statement).filter { it !in unused }
            }.reversed()
        return Runnable {
            for ((pointer, keep) in changes) {
                pointer.element?.let { MatlabImports.setImportNames(it, keep) }
            }
        }
    }
}
