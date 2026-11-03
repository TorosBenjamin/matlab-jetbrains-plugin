package com.github.kornilova203.matlab.imports

import com.github.kornilova203.matlab.MatlabLibraryDocs
import com.github.kornilova203.matlab.MatlabReference
import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.types.ownScope
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

/**
 * MATLAB's imports: only members of packages (+pkg folders) need them; `import pkg.Name` or `import pkg.*`
 * applies to the function it's in (or the script), not to the whole file, and goes after the function's help
 * comment and arguments blocks. The alternative is writing the package name: `pkg.Name(...)`.
 */
object MatlabImports {
    /**
     * Qualified names ("geometry.Point") that [ref] could mean: package members named like it, when it's a
     * plain name that resolves to nothing (`Point(1)`, `Point.origin()`), and isn't a built-in function.
     */
    fun candidates(ref: MatlabRefExpr): List<String> {
        if (!canBeImported(ref)) return emptyList()
        val name = ref.text
        val packages = MatlabPackages.packagesByMember(ref.project)[name] ?: return emptyList()
        if (MatlabLibraryDocs.isKnown(name) || MatlabReference(ref).resolve() != null) return emptyList()
        return packages.map { "$it.$name" }
    }

    /** A plain name: not after a dot, not assigned to, not in an import or a declaration. */
    private fun canBeImported(ref: MatlabRefExpr): Boolean {
        var node: PsiElement = ref
        val parent = ref.parent
        if (parent is MatlabFunctionExpr && parent.firstChild == ref) node = parent
        val above = node.parent
        if (above is MatlabQualifiedExpr && above.firstChild != node) return false  // obj.Name
        if (parent is MatlabCommandStatement && parent.refExpr == ref && ref.text == "import") return false
        if (PsiTreeUtil.getParentOfType(ref, MatlabCommandArguments::class.java) != null) return false
        return ref.isLeftPartOfAssign() == null
    }

    /**
     * Adds `import [qualifiedName]` where it applies to [context]: after the imports already in its function
     * (or script), else before the first statement, after the help comment and arguments blocks.
     */
    fun addImport(context: PsiElement, qualifiedName: String) {
        val file = context.containingFile
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file) ?: return
        documentManager.commitDocument(document)
        val scope = ownScope(context)
        val existing = importStatements(scope)
        val text = document.charsSequence
        if (existing.isNotEmpty()) {
            val last = existing.last()
            val lineEnd = document.getLineEndOffset(document.getLineNumber(last.textRange.endOffset))
            document.insertString(lineEnd, "\n" + indentOf(text, last.textRange.startOffset) + "import $qualifiedName")
        } else {
            val anchor = firstStatement(scope) ?: context
            val lineStart = document.getLineStartOffset(document.getLineNumber(anchor.textRange.startOffset))
            document.insertString(lineStart, indentOf(text, anchor.textRange.startOffset) + "import $qualifiedName\n")
        }
        documentManager.commitDocument(document)
    }

    /** Replaces [ref] with `qualifiedName`: Point(1) -> geometry.Point(1). */
    fun qualify(ref: MatlabRefExpr, qualifiedName: String) {
        val documentManager = PsiDocumentManager.getInstance(ref.project)
        val document = documentManager.getDocument(ref.containingFile) ?: return
        document.replaceString(ref.textRange.startOffset, ref.textRange.endOffset, qualifiedName)
        documentManager.commitDocument(document)
    }

    /** The `import` statements whose names apply in [scope] (a function or a file). */
    fun importStatements(scope: PsiElement): List<MatlabCommandStatement> =
        PsiTreeUtil.findChildrenOfType(scope, MatlabCommandStatement::class.java)
            .filter { it.refExpr.text == "import" && ownScope(it) == scope }

    /** The first statement of [scope]'s body, skipping the help comment and arguments blocks. */
    private fun firstStatement(scope: PsiElement): PsiElement? {
        val body = if (scope is MatlabFunctionDeclaration) scope.block ?: return null else scope
        var child = body.firstChild
        while (child != null && (child is PsiWhiteSpace || child is PsiComment || child.elementType == MatlabTypes.NEWLINE ||
                    child is MatlabArgumentsBlock || child.textLength == 0)) {
            child = child.nextSibling
        }
        return child
    }

    /** The whitespace before [offset] on its line, if only whitespace is before it. */
    private fun indentOf(text: CharSequence, offset: Int): String {
        var start = offset
        while (start > 0 && text[start - 1] != '\n') start--
        val before = text.subSequence(start, offset)
        return if (before.isBlank()) before.toString() else ""
    }

    /**
     * Names in `import` [statement] that nothing in its function (or script) uses. Only imports of packages
     * in the project or on the MATLAB path are checked (not java.*, toolboxes).
     */
    fun unusedImports(statement: MatlabCommandStatement): List<String> {
        if (statement.refExpr.text != "import") return emptyList()
        val project = statement.project
        val args = MatlabPackages.importArguments(statement).filter { MatlabPackages.isPackage(project, it.substringBefore('.')) }
        if (args.isEmpty()) return emptyList()
        // Packages of everything used by plain name in this scope
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
        return args.filter { arg -> if (arg.endsWith(".*")) arg.removeSuffix(".*") !in used else arg !in used }
    }

    /** Makes [statement] import exactly [names] (keeping a `;` or comment after them); removes it if none. */
    fun setImportNames(statement: MatlabCommandStatement, names: List<String>) {
        if (names.isEmpty()) {
            removeStatement(statement)
            return
        }
        val arguments = statement.commandArguments
        val text = arguments.text
        val current = Regex("[^\\s,;%]+").findAll(text.substringBefore('%')).toList()
        if (current.isEmpty()) return
        val range = TextRange(current.first().range.first, current.last().range.last + 1)
        ElementManipulators.handleContentChange(arguments, range, names.joinToString(" "))
    }

    /** Deletes [element] with its `;`, the line break after it, and the indentation before it. */
    fun removeStatement(element: PsiElement) {
        val indent = element.prevSibling as? PsiWhiteSpace
        deleteExpr(element)
        if (indent != null && indent.isValid && !indent.textContains('\n')) indent.delete()
    }
}
