package com.github.kornilova203.matlab.psi.types

import com.github.kornilova203.matlab.isNestedFunction
import com.github.kornilova203.matlab.psi.*
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/*
 * Inference of what functions return and what variables hold, used by name resolution and therefore
 * by go-to-definition, completion, coloring and find usages alike. Only class types are inferred.
 */

/** Class returned by [function], cached until the next code change. */
fun inferReturnType(function: MatlabFunctionDeclaration): MatlabType =
    CachedValuesManager.getCachedValue(function) {
        // Guards against cycles, e.g. f returns g() and g returns f().
        val type = RecursionManager.doPreventingRecursion(function, false) { computeReturnType(function) }
        CachedValueProvider.Result.create(type ?: MatlabTypeUnknown(), PsiModificationTracker.MODIFICATION_COUNT)
    }

private fun computeReturnType(function: MatlabFunctionDeclaration): MatlabType {
    val output = function.returnValues?.retValueList?.firstOrNull() ?: return MatlabTypeUnknown()
    val name = (output as PsiNamedElement).name ?: return MatlabTypeUnknown()
    declaredOutputType(function, name)?.let { return it }
    return inferVariableType(name, function)
}

/** `arguments (Output)` / `obj Logger` / `end` */
private fun declaredOutputType(function: MatlabFunctionDeclaration, output: String): MatlabType? {
    val blocks = function.block?.children?.filterIsInstance<MatlabArgumentsBlock>() ?: return null
    for (block in blocks) {
        if (!hasAttribute(block.attributes, "Output")) continue
        for (validation in block.argumentValidationList?.argumentValidationList.orEmpty()) {
            if (validation.refExprList.firstOrNull()?.text != output) continue
            val typeName = validation.className?.refExprList?.lastOrNull() as? MatlabTypedExpr ?: return null
            return typeName.getType().takeIf { it is MatlabTypeClass }
        }
    }
    return null
}

/**
 * Class held by variable [name] in [scope] (a function or a file): the type of the first assignment
 * `name = <expression of known class>` in that scope, e.g. `inst = Logger();`.
 */
fun inferVariableType(name: String, scope: PsiElement): MatlabType {
    val type = RecursionManager.doPreventingRecursion(scope to name, false) {
        for (assign in PsiTreeUtil.findChildrenOfType(scope, MatlabAssignExpr::class.java)) {
            if ((assign.left as? MatlabRefExpr)?.text != name) continue
            if (ownScope(assign) != scope) continue
            val value = (assign.right as? MatlabTypedExpr)?.getType()
            if (value is MatlabTypeClass) return@doPreventingRecursion value
        }
        null
    }
    return type ?: MatlabTypeUnknown()
}

/** The function whose variables [element] belongs to (nested functions share their parent's), or the file. */
fun ownScope(element: PsiElement): PsiElement {
    var function = PsiTreeUtil.getParentOfType(element, MatlabFunctionDeclaration::class.java)
    while (function != null && isNestedFunction(function)) {
        function = PsiTreeUtil.getParentOfType(function, MatlabFunctionDeclaration::class.java)
    }
    return function ?: element.containingFile
}

/**
 * The class whose members `x.member` refers to when `x` has [type]. A class name may resolve to the
 * class's constructor (`AppConfig.getInstance()` with an explicit `function obj = AppConfig()`).
 */
fun memberOwnerClass(type: MatlabType): MatlabTypeClass? = when (type) {
    is MatlabTypeClass -> type
    is MatlabTypeFunction -> type.decl.isConstructor()?.let(::MatlabTypeClass)
    else -> null
}
