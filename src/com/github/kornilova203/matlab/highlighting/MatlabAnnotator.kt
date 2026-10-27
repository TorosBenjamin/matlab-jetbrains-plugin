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
        /** Constant properties, like `static final` fields in Java. */
        val CONSTANT = createTextAttributesKey("MATLAB.CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
        /** `double` in `Width (1,1) double`, superclasses, ?ClassName */
        val TYPE = createTextAttributesKey("MATLAB.TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
        /** `SetAccess` in `properties (SetAccess = private)` */
        val ATTRIBUTE = createTextAttributesKey("MATLAB.ATTRIBUTE", DefaultLanguageHighlighterColors.METADATA)
        /** `private` in `properties (SetAccess = private)` */
        val ATTRIBUTE_VALUE = createTextAttributesKey("MATLAB.ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.KEYWORD)
        val EVENT = createTextAttributesKey("MATLAB.EVENT", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val ENUM_MEMBER = createTextAttributesKey("MATLAB.ENUM_MEMBER", DefaultLanguageHighlighterColors.CONSTANT)

        private val ATTRIBUTE_VALUES = setOf("public", "private", "protected", "immutable", "true", "false")
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
        // Declarations: decided by position in the tree.
        when (val parent = identifier.parent) {
            is MatlabProperty -> return if (isConstantProperty(parent)) CONSTANT else FIELD
            is MatlabEnumItem -> return ENUM_MEMBER
            is MatlabParameter -> return if (isObjectReference(parent)) OBJECT_REFERENCE else PARAMETER
            is MatlabRetValue -> return if (isObjectReference(parent)) OBJECT_REFERENCE else ID
            is MatlabRefExpr -> {
                positionColor(parent)?.let { return it }
                // Uses: decided by what the name resolves to.
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

    /** Names whose role follows from where they are: types, attributes, events. */
    private fun positionColor(ref: MatlabRefExpr): TextAttributesKey? = when (val parent = ref.parent) {
        is MatlabClassName, is MatlabSuperClasses, is MatlabMetaClassExpr -> TYPE
        is MatlabEventsList -> EVENT
        is MatlabAttribute -> when {
            parent.firstChild == ref || PsiTreeUtil.skipWhitespacesBackward(ref).elementType == TILDA -> ATTRIBUTE
            ref.text in ATTRIBUTE_VALUES -> ATTRIBUTE_VALUE
            else -> null
        }
        else -> null
    }

    /**
     * Name after a dot: `obj.Width`, `obj.move(1, 1)`, `obj.area` (method called without parentheses),
     * `Shape.Version` (constant). Without parentheses the syntax is the same for fields and methods,
     * so look up what the name resolves to in the object's class.
     */
    private fun memberColor(ref: MatlabRefExpr): TextAttributesKey? {
        val call = ref.parent as? MatlabFunctionExpr
        val isCallee = call != null && call.firstChild == ref
        val node = if (isCallee) call!! else ref
        if (PsiTreeUtil.skipWhitespacesBackward(node).elementType != DOT) return null
        if (isCallee) return METHOD_CALL
        return when (val target = ref.reference?.resolve()) {
            is MatlabFunctionDeclaration -> METHOD_CALL
            is MatlabProperty -> if (isConstantProperty(target)) CONSTANT else FIELD
            is MatlabEnumItem -> ENUM_MEMBER
            else -> FIELD
        }
    }

    private fun isConstantProperty(property: MatlabProperty): Boolean {
        val block = PsiTreeUtil.getParentOfType(property, MatlabPropertiesBlock::class.java) ?: return false
        return hasAttribute(block.attributes, "Constant")
    }
}
