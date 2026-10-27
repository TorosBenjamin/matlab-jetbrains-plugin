package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.psi.*
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

/**
 * Reports functions nothing can call: unused local functions, nested functions and private methods.
 * Never reports a file's main function or public methods, which can be called from outside.
 */
class MatlabUnusedFunctionInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : MatlabVisitor() {
        override fun visitFunctionDeclaration(function: MatlabFunctionDeclaration) {
            val kind = privateKind(function) ?: return
            val name = (function as PsiNamedElement).name ?: return
            val scope = GlobalSearchScope.fileScope(function.containingFile)
            if (ReferencesSearch.search(function, scope).findFirst() != null) return
            if (isNamedInString(function.containingFile, name)) return  // feval('name'), callbacks
            val target = (function as com.intellij.psi.PsiNameIdentifierOwner).nameIdentifier ?: return
            holder.registerProblem(target, "$kind '$name' is never used", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
    }

    /** "Local function", "Nested function", "Private method", or null if it may be called from outside. */
    private fun privateKind(function: MatlabFunctionDeclaration): String? {
        val parent = function.parent
        if (parent is MatlabMethodsList) {
            val block = parent.parent as? MatlabMethodsBlock ?: return null
            val access = block.attributes?.attributeList.orEmpty().firstOrNull { attr ->
                attr.text.filterNot { it.isWhitespace() }.startsWith("Access=")
            }?.text?.substringAfter('=')?.trim()
            if (access != "private" && access != "'private'") return null
            if (function.getterOrSetterModifier != null) return null  // get.X / set.X run implicitly
            val cls = PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java)
            if ((function as PsiNamedElement).name == (cls as? PsiNamedElement)?.name) return null  // constructor
            return "Private method"
        }
        if (parent is MatlabBlock && parent.parent is MatlabFunctionDeclaration) {
            return if (com.github.kornilova203.matlab.isNestedFunction(function)) "Nested function" else "Local function"
        }
        if (parent is MatlabFile) {
            // The first function of a function file is what callers run; later ones and functions in scripts are local.
            val first = parent.children.firstOrNull { it.elementType != MatlabTypes.NEWLINE && it !is com.intellij.psi.PsiWhiteSpace && it !is com.intellij.psi.PsiComment }
            return if (first == function) null else "Local function"
        }
        return null
    }

    private fun isNamedInString(file: PsiElement, name: String): Boolean {
        val word = Regex("\\b${Regex.escape(name)}\\b")
        return SyntaxTraverser.psiTraverser(file).any { el ->
            val type = el.elementType
            (type == MatlabTypes.SINGLE_QUOTE_STRING || type == MatlabTypes.DOUBLE_QUOTE_STRING ||
                    type == MatlabTypes.COMMAND_ARGUMENTS) && word.containsMatchIn(el.text)
        }
    }
}
