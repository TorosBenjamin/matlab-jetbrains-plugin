package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.MatlabLibraryDocs
import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.stub.MatlabClassDeclarationIndex
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import javax.swing.Icon

/**
 * Completion that depends on where the caret is. Runs before the other contributors; in the positions
 * below it offers only what fits there and stops them, elsewhere it does nothing.
 *
 *   function Y = f(|)           new name: nothing to suggest
 *   properties (|               attribute names valid for properties blocks
 *   properties (SetAccess = |   attribute values (public, protected, private, immutable)
 *   Width (1,1) |               types
 *   Width double {|}            validation functions (mustBePositive, ...)
 *   arguments / |               the function's parameters
 */
class MatlabContextCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(MatlabTypes.IDENTIFIER), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                if (complete(parameters.position, result)) result.stopHere()
            }
        })
    }

    /** Returns true if the position was handled (the other contributors must not add anything). */
    private fun complete(identifier: PsiElement, result: CompletionResultSet): Boolean {
        val parent = identifier.parent
        if (isNewName(identifier, parent)) {
            blockKeywords(identifier, parent).forEach { result.addElement(MatlabKeywordCompletionContributor.keywordElement(it)) }
            return true
        }
        if (parent !is MatlabRefExpr) return false
        when (val owner = parent.parent) {
            is MatlabAttribute -> {
                // `[~]Name [= value]`: the first name is the attribute, anything after `=` its value.
                val name = PsiTreeUtil.getChildOfType(owner, MatlabRefExpr::class.java).takeIf { it != parent }?.text
                if (name == null) add(result, attributeNames(owner), AllIcons.Nodes.Annotationtype)
                else add(result, attributeValues(name), null, bold = true)
                return true
            }
            is MatlabClassName, is MatlabSuperClasses -> {
                if (PsiTreeUtil.skipWhitespacesBackward(parent).elementType == MatlabTypes.DOT) return false
                val classes = StubIndex.getInstance().getAllKeys(MatlabClassDeclarationIndex.KEY, identifier.project)
                val builtIn = if (owner is MatlabSuperClasses) SUPERCLASSES else TYPES
                add(result, builtIn, AllIcons.Nodes.Type)
                add(result, classes.filter { it.isNotEmpty() && it !in builtIn }, AllIcons.Nodes.Class)
                return true
            }
            is MatlabArgumentValidation -> if (owner.firstChild == parent) {
                val function = PsiTreeUtil.getParentOfType(owner, MatlabFunctionDeclaration::class.java)
                add(result, function?.parameters?.parameterList.orEmpty().mapNotNull { (it as PsiNamedElement).name },
                    AllIcons.Nodes.Parameter)
                return true
            }
        }
        if (PsiTreeUtil.getParentOfType(parent, MatlabPropertyValidationFunctions::class.java) != null &&
            parent.parent is MatlabPropertyValidationFunctions) {
            add(result, VALIDATORS, AllIcons.Nodes.Function)
            return true
        }
        return false
    }

    /** Positions where the user types a name that doesn't exist yet. */
    private fun isNewName(identifier: PsiElement, parent: PsiElement?): Boolean = when (parent) {
        is MatlabParameter, is MatlabRetValue, is MatlabProperty, is MatlabEnumItem,
        is MatlabPersistentVariable, is MatlabGlobalVariableDeclaration, is MatlabMethodSignature -> true
        is MatlabFunctionDeclaration, is MatlabClassDeclaration, is MatlabCatchBlock -> true
        is MatlabRefExpr -> parent.parent is MatlabEventsList
        else -> false
    }

    /** At the start of an item in a class block, the block may be ending (`end`) or a method starting. */
    private fun blockKeywords(identifier: PsiElement, parent: PsiElement?): List<String> {
        val item = if (parent is MatlabRefExpr) parent else parent ?: return emptyList()
        if (item.firstChild != identifier && parent !is MatlabRefExpr) return emptyList()
        return when (item) {
            is MatlabMethodSignature -> listOf("end", "function")
            is MatlabProperty, is MatlabEnumItem, is MatlabRefExpr -> listOf("end")
            else -> emptyList()
        }
    }

    private fun attributeNames(attribute: MatlabAttribute): List<String> {
        val owner = attribute.parent?.parent
        return when (owner) {
            is MatlabPropertiesBlock -> PROPERTY_ATTRIBUTES
            is MatlabMethodsBlock -> METHOD_ATTRIBUTES
            is MatlabEventsBlock -> EVENT_ATTRIBUTES
            is MatlabClassDeclaration -> CLASS_ATTRIBUTES
            is MatlabArgumentsBlock -> listOf("Input", "Output", "Repeating")
            else -> emptyList()
        }
    }

    private fun attributeValues(name: String): List<String> = when (name) {
        "SetAccess" -> listOf("public", "protected", "private", "immutable")
        "Access", "GetAccess", "ListenAccess", "NotifyAccess" -> listOf("public", "protected", "private")
        in NON_BOOLEAN_ATTRIBUTES -> emptyList()
        else -> listOf("true", "false")
    }

    private fun add(result: CompletionResultSet, names: Collection<String>, icon: Icon?, bold: Boolean = false) {
        for (name in names) {
            var element = LookupElementBuilder.create(name)
            if (icon != null) element = element.withIcon(icon)
            if (bold) element = element.bold()
            result.addElement(element)
        }
    }

    companion object {
        private val CLASS_ATTRIBUTES = listOf(
            "Abstract", "AllowedSubclasses", "ConstructOnLoad", "HandleCompatible", "Hidden", "InferiorClasses", "Sealed")
        private val PROPERTY_ATTRIBUTES = listOf(
            "AbortSet", "Abstract", "Access", "Constant", "Dependent", "GetAccess", "GetObservable", "Hidden",
            "NonCopyable", "SetAccess", "SetObservable", "Transient",
            // matlab.unittest
            "TestParameter", "MethodSetupParameter", "ClassSetupParameter")
        private val METHOD_ATTRIBUTES = listOf(
            "Abstract", "Access", "Hidden", "Sealed", "Static",
            // matlab.unittest
            "Test", "TestMethodSetup", "TestMethodTeardown", "TestClassSetup", "TestClassTeardown", "ParameterCombination")
        private val EVENT_ATTRIBUTES = listOf("Hidden", "ListenAccess", "NotifyAccess")
        private val NON_BOOLEAN_ATTRIBUTES = setOf("AllowedSubclasses", "InferiorClasses", "ParameterCombination")

        private val TYPES = listOf(
            "double", "single", "logical", "char", "string", "cell", "struct", "function_handle",
            "int8", "int16", "int32", "int64", "uint8", "uint16", "uint32", "uint64",
            "table", "timetable", "datetime", "duration", "categorical", "containers.Map")
        private val SUPERCLASSES = listOf("handle", "matlab.mixin.Copyable", "matlab.mixin.SetGet",
            "matlab.mixin.Heterogeneous", "matlab.mixin.CustomDisplay", "matlab.unittest.TestCase")

        /** Validation functions from the bundled MATLAB function list (mustBePositive, mustBeMember, ...). */
        private val VALIDATORS: List<String> by lazy { MatlabLibraryDocs.functionNames.filter { it.startsWith("mustBe") } }
    }
}
