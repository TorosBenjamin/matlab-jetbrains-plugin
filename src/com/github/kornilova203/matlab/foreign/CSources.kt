package com.github.kornilova203.matlab.foreign

import com.github.kornilova203.matlab.psi.MatlabCommandStatement
import com.github.kornilova203.matlab.psi.MatlabFunctionExpr
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * C, C++ and Fortran code called from MATLAB:
 *   - MEX files: `foo(x)` runs the compiled foo.c (its mexFunction, or a C++ MexFunction class)
 *   - C libraries: `loadlibrary('mylib', 'mylib.h')`, then `calllib('mylib', 'add', 1, 2)`
 */
object CSources {
    /** Source files that `mex` builds into a MEX function. */
    val MEX_EXTENSIONS = listOf("c", "cpp", "cc", "cxx", "cu", "f", "F", "f90", "F90")

    private val GATEWAY = Regex("\\b(mexFunction)\\b|\\bclass[ \\t]+(MexFunction)\\b")

    fun isSourceFile(name: String): Boolean = name.substringAfterLast('.', "") in MEX_EXTENSIONS

    /** The gateway functions of MEX sources named [name], e.g. foo.c for `foo(x)`. */
    fun mexGateways(project: Project, name: String, near: PsiFile?): List<PsiElement> =
        MEX_EXTENSIONS.flatMap { ForeignFiles.byName(project, "$name.$it", near) }.mapNotNull { source ->
            val file = ForeignFiles.psi(project, source) ?: return@mapNotNull null
            val match = GATEWAY.find(ForeignFiles.text(file)) ?: return@mapNotNull null
            val group = match.groups[1] ?: match.groups[2] ?: return@mapNotNull null
            foreignTarget(file, group.range.first, group.value)
        }

    /** Function [name] declared in the header of library [library], as loaded by loadlibrary in [file]. */
    fun libraryFunction(project: Project, library: String, name: String, file: PsiFile): PsiElement? {
        val header = libraryHeader(project, library, file) ?: return null
        return function(header, name)
    }

    /**
     * The header of library [library]: the one given to `loadlibrary` for it in [file], else mylib.h
     * (or, for libfoo, foo.h).
     */
    fun libraryHeader(project: Project, library: String, file: PsiFile): PsiFile? {
        val lib = baseName(library)
        for (call in PsiTreeUtil.findChildrenOfType(file, MatlabFunctionExpr::class.java)) {
            if (call.expr.text != "loadlibrary") continue
            val args = call.arguments.children
            if (args.size < 2 || stringValue(args[0])?.let(::baseName) != lib) continue
            val header = stringValue(args[1]) ?: continue
            ForeignFiles.find(project, header, file)?.let { return it }
        }
        for (command in PsiTreeUtil.findChildrenOfType(file, MatlabCommandStatement::class.java)) {
            if (command.refExpr.text != "loadlibrary") continue
            val args = command.commandArguments.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (args.size < 2 || baseName(args[0]) != lib) continue
            ForeignFiles.find(project, args[1], file)?.let { return it }
        }
        for (header in listOf("$lib.h", lib.removePrefix("lib") + ".h")) {
            ForeignFiles.byName(project, header, file).firstOrNull()?.let { return ForeignFiles.psi(project, it) }
        }
        return null
    }

    /** "mylib" for 'mylib', 'mylib.so', 'lib/mylib.dll'. */
    private fun baseName(library: String): String =
        library.replace('\\', '/').substringAfterLast('/').substringBefore('.')

    /** The declaration of C function [name] in [file]: `type name(`, not a call like `x = name(`. */
    fun function(file: PsiFile, name: String): PsiElement? {
        val n = Regex.escape(name)
        val offset = findDeclaration(ForeignFiles.text(file),
            Regex("^[ \\t]*(?!return\\b)[A-Za-z_][\\w \\t*&:<>,]*?[\\s*&]($n)[ \\t]*\\(", RegexOption.MULTILINE))
            ?: return null
        return foreignTarget(file, offset, name)
    }
}
