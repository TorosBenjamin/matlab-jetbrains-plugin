package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.psi.*
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

class MatlabDocumentationProvider : DocumentationProvider {
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return getDeclaration(element, originalElement)?.let(::escape)
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Shift+F1 / "External Documentation": the function's MathWorks page, or a doc search if it has none. */
    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? {
        if (element is MatlabDeclaration) return emptyList()  // defined in the project
        val name = libraryName(originalElement) ?: unresolvedName(element, originalElement) ?: return emptyList()
        return listOf(MatlabLibraryDocs.pageUrl(name) ?: MatlabLibraryDocs.searchUrl(name))
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val declaration = getDeclaration(element, originalElement) ?: originalElement?.text ?: ""
        val result = DEFINITION_START + escape(declaration) + DEFINITION_END
        val libraryName = if (element is MatlabDeclaration) null else libraryName(originalElement)
        if (libraryName != null) {
            val description = MatlabLibraryDocs.descriptions.getProperty(libraryName)?.let(::escape) ?: ""
            return DEFINITION_START + escape(libraryName) + DEFINITION_END +
                    CONTENT_START + description + links(libraryName) + CONTENT_END
        }
        unresolvedName(element, originalElement)?.let { name ->
            // Not in the project and not a known MATLAB function: maybe a toolbox function.
            return result + CONTENT_START + "Not defined in this project." + links(name) + CONTENT_END
        }
        if (element is MatlabFunctionDeclaration || element is MatlabClassDeclaration) {
            var comment: PsiElement? = element.getChildOfType(MatlabTypes.COMMENT) ?: return result
            val commentText = StringBuilder()
            var isFirst = true
            while (comment != null && comment.elementType == MatlabTypes.COMMENT) {
                if (isFirst) isFirst = false
                else commentText.append("<br/>")
                commentText.append(escape(comment.text.trimStart('%').trim()))
                if (comment.nextSibling?.elementType == MatlabTypes.NEWLINE) {
                    comment = PsiTreeUtil.skipWhitespacesForward(comment.nextSibling)
                } else {
                    break
                }
            }
            return result + CONTENT_START + commentText + CONTENT_END
        }
        return result
    }
    
    private fun links(name: String): String {
        val search = "<a href=\"${MatlabLibraryDocs.searchUrl(name)}\">Search MathWorks documentation</a>"
        val page = MatlabLibraryDocs.pageUrl(name) ?: return "<p>$search</p>"
        return "<p><a href=\"$page\">MathWorks documentation</a> &middot; $search</p>"
    }

    /** Name of a built-in function at [originalElement], package-qualified if needed (matlab.lang.makeValidName). */
    private fun libraryName(originalElement: PsiElement?): String? {
        if (originalElement?.elementType != MatlabTypes.IDENTIFIER) return null
        val ref = originalElement.parent as? MatlabRefExpr ?: return null
        var qualified: PsiElement = ref
        while (true) {
            val parent = qualified.parent
            qualified = when {
                parent is MatlabQualifiedExpr && parent.lastChild == qualified -> parent
                parent is MatlabFunctionExpr && parent.firstChild == qualified &&
                        parent.parent is MatlabQualifiedExpr && parent.parent.lastChild == parent -> parent.parent
                else -> break
            }
        }
        val qualifiedName = qualified.text.substringBefore('(').filterNot { it.isWhitespace() }
        return listOf(qualifiedName, originalElement.text).firstOrNull { MatlabLibraryDocs.isKnown(it) }
    }

    /** An identifier that doesn't resolve to anything in the project. */
    private fun unresolvedName(element: PsiElement?, originalElement: PsiElement?): String? {
        if (originalElement?.elementType != MatlabTypes.IDENTIFIER) return null
        val ref = originalElement.parent as? MatlabRefExpr ?: return null
        if (element is MatlabDeclaration || ref.reference?.resolve() != null) return null
        return originalElement.text
    }

    private fun getDeclaration(element: PsiElement?, originalElement: PsiElement?): String? {
        when (element) {
            is MatlabAssignExpr -> return element.text
            is MatlabGlobalVariableDeclaration -> return "global ${originalElement?.text}"
            is MatlabParameter -> {
                val function = element.parent.parent
                val functionName = if (function is MatlabFunctionDeclaration) getName(function) else ""
                return "parameter '${element.text}' of function '${functionName}'"
            }
            is MatlabRetValue -> {
                val function = element.parent.parent
                val functionName = if (function is MatlabFunctionDeclaration) getName(function) else ""
                return "return value '${element.text}' of function '${functionName}'"
            }
            is MatlabFunctionDeclaration -> {
                val result = StringBuilder("function ")
                result.append(printElement(element.returnValues, "", " = "))
                result.append(printElement(element.getterOrSetterModifier, "", ""))
                result.append(getName(element))
                result.append(printElement(element.parameters, "", ""))
                return result.toString()
            }
            is MatlabClassDeclaration -> {
                val result = StringBuilder("classdef ")
                result.append(printElement(element.attributes, "", " "))
                result.append(getName(element))
                result.append(printElement(element.superClasses, " ", ""))  // the text already starts with "<"
                return result.toString()
            }
        }
        return null
    }

    private fun getName(element: PsiElement?): String {
        val ident = element?.getChildOfType(MatlabTypes.IDENTIFIER) ?: return ""
        return ident.text
    }

    private fun printElement(element: PsiElement?, prefix: String, suffix: String): String {
        return if (element == null) "" else prefix + element.text + suffix
    }
}