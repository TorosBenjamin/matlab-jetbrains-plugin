package com.github.kornilova203.matlab.editor

import com.github.kornilova203.matlab.psi.*
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * View | Parameter Info (Ctrl+P) inside a call: `add(a, b)`, `obj.move(d)`, `Point(x)`.
 * Types and defaults come from the function's `arguments` block; `options.Name` arguments are
 * name-value pairs, shown together as the last parameter.
 */
class MatlabParameterInfoHandler : ParameterInfoHandler<MatlabArguments, MatlabSignature> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): MatlabArguments? {
        val arguments = argumentsAt(context.file, context.offset) ?: return null
        val signature = signatureOf(arguments) ?: return null
        context.itemsToShow = arrayOf(signature)
        return arguments
    }

    override fun showParameterInfo(element: MatlabArguments, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): MatlabArguments? =
        argumentsAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: MatlabArguments, context: UpdateParameterInfoContext) {
        context.setCurrentParameter(currentArgument(parameterOwner, context.offset))
    }

    override fun updateUI(p: MatlabSignature, context: ParameterInfoUIContext) {
        val presentation = p.presentation(context.currentParameterIndex)
        context.setupUIComponentPresentation(presentation.text, presentation.highlightStart, presentation.highlightEnd,
            !context.isUIComponentEnabled, false, false, context.defaultParameterColor)
    }

    companion object {
        /** The argument list of a call around [offset], `(...)` of `f(...)`. */
        fun argumentsAt(file: PsiFile, offset: Int): MatlabArguments? =
            ParameterInfoUtils.findParentOfType(file, offset, MatlabArguments::class.java)
                ?.takeIf { it.parent is MatlabFunctionExpr }

        /** Index of the argument at [offset] in [arguments]: the number of top-level commas before it. */
        fun currentArgument(arguments: MatlabArguments, offset: Int): Int =
            ParameterInfoUtils.getCurrentParameterIndex(arguments.node, offset, MatlabTypes.COMMA)

        /** The signature of the function, method or constructor called with [arguments]. */
        fun signatureOf(arguments: MatlabArguments): MatlabSignature? {
            val call = arguments.parent as? MatlabFunctionExpr ?: return null
            val callee = call.expr
            val ref = callee as? MatlabRefExpr ?: (callee as? MatlabQualifiedExpr)?.lastChild as? MatlabRefExpr ?: return null
            val dotCall = callee is MatlabQualifiedExpr || call.parent.let { it is MatlabQualifiedExpr && it.lastChild == call }
            val function = when (val target = ref.reference?.resolve()) {
                is MatlabFunctionDeclaration -> target
                is MatlabClassDeclaration -> constructorOf(target) ?: return MatlabSignature((target as PsiNamedElement).name ?: "", emptyList())
                else -> return null
            }
            // obj.method(x): obj is the method's first parameter
            val skipObject = dotCall && isInstanceMethod(function)
            return MatlabSignature.of(function, skipObject)
        }

        private fun constructorOf(cls: MatlabClassDeclaration): MatlabFunctionDeclaration? {
            val name = (cls as PsiNamedElement).name ?: return null
            return PsiTreeUtil.findChildrenOfType(cls, MatlabFunctionDeclaration::class.java)
                .firstOrNull { (it as PsiNamedElement).name == name }
        }

        private fun isInstanceMethod(function: MatlabFunctionDeclaration): Boolean {
            if (function.parent !is MatlabMethodsList) return false
            val cls = PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java) ?: return false
            return !isStaticMethod(function) && (function as PsiNamedElement).name != (cls as PsiNamedElement).name
        }
    }
}

/** One parameter: `b double = 0`. */
class MatlabParameterDescription(val name: String, val type: String?, val default: String?) {
    override fun toString(): String = buildString {
        append(name)
        if (!type.isNullOrBlank()) append(' ').append(type)
        if (!default.isNullOrBlank()) append(" = ").append(default)
    }
}

/**
 * Parameters of a function: [positional] ones, then name-value [options] (from `options.Name` lines in
 * the arguments block), passed as `f(1, 'Name', value)` or `f(1, Name=value)`.
 */
class MatlabSignature(val name: String, val positional: List<MatlabParameterDescription>,
                      val options: List<MatlabParameterDescription> = emptyList()) {
    class Presentation(val text: String, val highlightStart: Int, val highlightEnd: Int)

    /** The text for the popup with argument [current] highlighted. */
    fun presentation(current: Int): Presentation {
        val parts = positional.map { it.toString() }.toMutableList()
        if (options.isNotEmpty()) parts += "Name=Value: " + options.joinToString(", ")
        if (parts.isEmpty()) return Presentation("<no parameters>", -1, -1)
        // Past the positional parameters: the name-value pairs, or varargin which takes the rest
        val index = when {
            current < 0 -> -1
            current < positional.size && positional[current].name != "varargin" -> current
            options.isNotEmpty() -> parts.size - 1
            positional.lastOrNull()?.name == "varargin" -> positional.size - 1
            else -> -1
        }
        val text = parts.joinToString(", ")
        if (index < 0) return Presentation(text, -1, -1)
        val start = parts.take(index).sumOf { it.length + 2 }
        return Presentation(text, start, start + parts[index].length)
    }

    companion object {
        fun of(function: MatlabFunctionDeclaration, skipObject: Boolean): MatlabSignature {
            val name = (function as PsiNamedElement).name ?: ""
            val validations = inputValidations(function)
            var parameters = function.parameters?.parameterList.orEmpty().map { it.text }
            if (skipObject) parameters = parameters.drop(1)
            // Parameters whose arguments-block lines are `options.Name`: a struct of name-value pairs
            val structs = validations.filter { it.refExprList.size > 1 }.map { it.refExprList[0].text }.toSet()
            val positional = parameters.filter { it !in structs }.map { param ->
                val validation = validations.firstOrNull { it.refExprList.size == 1 && it.refExprList[0].text == param }
                MatlabParameterDescription(param, validation?.let(::typeOf), validation?.defaultValue?.text)
            }
            val options = validations.filter { it.refExprList.size > 1 && it.refExprList[0].text in parameters }.map {
                MatlabParameterDescription(it.refExprList[1].text, typeOf(it), it.defaultValue?.text)
            }
            return MatlabSignature(name, positional, options)
        }

        /** `(1,1) double {mustBePositive}` */
        private fun typeOf(validation: MatlabArgumentValidation): String? =
            listOfNotNull(validation.propertySize?.text, validation.className?.text, validation.propertyValidationFunctions?.text)
                .joinToString(" ").ifEmpty { null }

        /** Lines of the function's input `arguments` blocks (not `arguments (Output)`). */
        private fun inputValidations(function: MatlabFunctionDeclaration): List<MatlabArgumentValidation> {
            val block = function.block ?: return emptyList()
            return block.children.filterIsInstance<MatlabArgumentsBlock>()
                .filter { it.attributes?.text?.contains("Output", ignoreCase = true) != true }
                .flatMap { it.argumentValidationList?.argumentValidationList.orEmpty() }
        }
    }
}
