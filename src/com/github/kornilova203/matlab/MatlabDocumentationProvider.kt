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

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? {
        val url = MatlabLibraryDocs.urls.getProperty(originalElement?.text) ?: return emptyList()
        return listOf("https://www.mathworks.com/help/matlab/$url")
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val declaration = getDeclaration(element, originalElement) ?: originalElement?.text ?: ""
        val result = DEFINITION_START + escape(declaration) + DEFINITION_END
        val description = MatlabLibraryDocs.descriptions.getProperty(originalElement?.text)
        if (description != null) {
            return result + CONTENT_START + escape(description) + CONTENT_END
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