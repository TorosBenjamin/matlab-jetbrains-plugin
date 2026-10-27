package com.github.kornilova203.matlab.highlighting

import com.github.kornilova203.matlab.MatlabElementTypes.*
import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.MatlabTypes.*
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

/**
 * @author Liudmila Kornilova
 **/
class MatlabAnnotator : Annotator {
    companion object {
        val FUNCTION_DECLARATION = createTextAttributesKey("MATLAB.FUNC_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val CLASS_DECLARATION = createTextAttributesKey("MATLAB.CLASS_DECLARATION", DefaultLanguageHighlighterColors.CLASS_NAME)
        val LAMBDA_PARENTH = createTextAttributesKey("MATLAB.LAMBDA_PARENTH", DefaultLanguageHighlighterColors.METADATA)
        val KEYWORD = createTextAttributesKey("MATLAB.KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val ID = createTextAttributesKey("MATLAB.ID", DefaultLanguageHighlighterColors.IDENTIFIER)
        /** Words after a command name, e.g. `on` in `hold on`; MATLAB passes them as char arrays. */
        val COMMAND_ARGUMENT = createTextAttributesKey("MATLAB.COMMAND_ARGUMENT", DefaultLanguageHighlighterColors.STRING)
        /** `obj.Width` */
        val FIELD = createTextAttributesKey("MATLAB.FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        /** `obj.move(1, 1)` */
        val METHOD_CALL = createTextAttributesKey("MATLAB.METHOD_CALL", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
        val PARAMETER = createTextAttributesKey("MATLAB.PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
        /** The object a method works on (first parameter, constructor output), like `this`/`self`. */
        val OBJECT_REFERENCE = createTextAttributesKey("MATLAB.OBJECT_REFERENCE", DefaultLanguageHighlighterColors.KEYWORD)
    }

    // Called once per PSI element (leaves included), so only look at the element itself.
    override fun annotate(el: PsiElement, holder: AnnotationHolder) {
        val type = el.node.elementType
        val attrs = when {
            type == AT && el.parent is MatlabLambdaExpr -> LAMBDA_PARENTH
            (type == LPARENTH || type == RPARENTH) &&
                    el.parent is MatlabParameters && el.parent.parent is MatlabLambdaExpr  -> LAMBDA_PARENTH
            type == IDENTIFIER && el.parent is MatlabFunctionDeclaration                   -> FUNCTION_DECLARATION
            type == IDENTIFIER && el.parent is MatlabClassDeclaration                      -> CLASS_DECLARATION
            type == METHODS || type == EVENTS || type == PROPERTIES || type == ENUMERATION ||
                    type == PERSISTENT || type == ARGUMENTS_KEYWORD                        -> KEYWORD
            type == COMMAND_ARGUMENTS                                                   -> COMMAND_ARGUMENT
            type == IDENTIFIER && el.parent?.node?.elementType == COMMAND_ARGUMENTS      -> null
            type == IDENTIFIER                                                             -> identifierColor(el)
            else -> null
        } ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(el)
            .textAttributes(attrs)
            .create()
    }

    private fun identifierColor(identifier: PsiElement): TextAttributesKey {
        when (val parent = identifier.parent) {
            is MatlabParameter -> return if (isObjectReference(parent)) OBJECT_REFERENCE else PARAMETER
            is MatlabRetValue -> return if (isObjectReference(parent)) OBJECT_REFERENCE else ID
            is MatlabRefExpr -> {
                memberColor(parent)?.let { return it }
                return when (val target = parent.reference?.resolve()) {
                    is MatlabParameter -> if (isObjectReference(target)) OBJECT_REFERENCE else PARAMETER
                    is MatlabRetValue -> if (isObjectReference(target)) OBJECT_REFERENCE else ID
                    else -> ID
                }
            }
        }
        return ID
    }

    /** Field or method name after a dot: `obj.Width`, `obj.move(1, 1)`. */
    private fun memberColor(ref: MatlabRefExpr): TextAttributesKey? {
        val call = ref.parent as? MatlabFunctionExpr
        val isCallee = call != null && call.firstChild == ref
        val node = if (isCallee) call!! else ref
        if (PsiTreeUtil.skipWhitespacesBackward(node).elementType != DOT) return null
        return if (isCallee) METHOD_CALL else FIELD
    }

    private fun isObjectReference(declaration: PsiElement): Boolean {
        val function = PsiTreeUtil.getParentOfType(declaration, MatlabFunctionDeclaration::class.java) ?: return false
        if (declaration is MatlabParameter) {
            if (function.parameters?.parameterList?.firstOrNull() != declaration) return false
            if (isInClassFolder(function)) return true
            return isMethod(function) && !isStatic(function) && !isConstructor(function)
        }
        if (declaration is MatlabRetValue) {
            return isMethod(function) && isConstructor(function)
        }
        return false
    }

    private fun isMethod(function: MatlabFunctionDeclaration) = function.parent is MatlabMethodsList

    private fun isStatic(function: MatlabFunctionDeclaration): Boolean {
        val attributes = (function.parent.parent as? MatlabMethodsBlock)?.attributes?.attributeList ?: return false
        return attributes.any { attr ->
            val text = attr.text.replace(" ", "")
            text == "Static" || text == "Static=true"
        }
    }

    private fun isConstructor(function: MatlabFunctionDeclaration): Boolean {
        val cls = PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java) ?: return false
        return (function as PsiNamedElement).name == (cls as PsiNamedElement).name
    }

    /** Top-level function of a file in an @ClassName folder (old-style class method). */
    private fun isInClassFolder(function: MatlabFunctionDeclaration): Boolean {
        if (function.parent !is MatlabFile) return false
        val dir = function.containingFile.originalFile.virtualFile?.parent?.name ?: return false
        if (!dir.startsWith("@")) return false
        // The constructor (same name as the folder) takes inputs, not the object.
        return (function as PsiNamedElement).name != dir.substring(1)
    }
}
