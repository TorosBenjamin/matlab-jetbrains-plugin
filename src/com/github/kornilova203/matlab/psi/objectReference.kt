package com.github.kornilova203.matlab.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/*
 * The "object variable" of a method: the variable holding the object the method works on,
 * like `this` in Java or `self` in Python. In MATLAB it's a naming convention, not a keyword:
 *   - the first parameter of a non-static, non-constructor method: function area(obj)
 *   - the output of a constructor:                                 function obj = Shape(w, h)
 *   - the first parameter of a method in an @ClassName folder
 */

/** The class whose object [declaration] holds, if it's the object variable of a classdef method. */
fun objectClassOf(declaration: PsiElement): MatlabClassDeclaration? {
    val function = ownerFunction(declaration) ?: return null
    // Method in its own file of an @Cls folder that also has classdef Cls: function a = area(obj)
    if (function.parent is MatlabFile && declaration is MatlabParameter && isFirstParameter(declaration, function)) {
        MatlabPackages.classOfMethodFile(function.containingFile.originalFile.virtualFile, function.project)?.let { return it }
    }
    if (function.parent !is MatlabMethodsList) return null
    val cls = PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java) ?: return null
    val constructor = isConstructor(function, cls)
    return when (declaration) {
        is MatlabParameter ->
            if (!constructor && !isStaticMethod(function) && isFirstParameter(declaration, function)) cls else null
        is MatlabRetValue -> if (constructor) cls else null
        else -> null
    }
}

/** Like [objectClassOf], but also true for old-style @ClassName folder methods, which have no classdef. */
fun isObjectReference(declaration: PsiElement): Boolean {
    if (objectClassOf(declaration) != null) return true
    val function = ownerFunction(declaration) ?: return false
    return declaration is MatlabParameter && isFirstParameter(declaration, function) && isClassFolderMethod(function)
}

/** The function that declares [declaration] as a parameter or output. */
private fun ownerFunction(declaration: PsiElement): MatlabFunctionDeclaration? {
    if (declaration !is MatlabParameter && declaration !is MatlabRetValue) return null
    return declaration.parent?.parent as? MatlabFunctionDeclaration
}

private fun isFirstParameter(parameter: MatlabParameter, function: MatlabFunctionDeclaration) =
    function.parameters?.parameterList?.firstOrNull() == parameter

private fun isConstructor(function: MatlabFunctionDeclaration, cls: MatlabClassDeclaration) =
    (function as PsiNamedElement).name == (cls as PsiNamedElement).name

fun isStaticMethod(function: MatlabFunctionDeclaration): Boolean =
    hasAttribute((function.parent?.parent as? MatlabMethodsBlock)?.attributes, "Static")

/** `Name` or `Name = true` in an attribute list like (Static, Access = private). */
fun hasAttribute(attributes: MatlabAttributes?, name: String): Boolean =
    attributes?.attributeList.orEmpty().any { attr ->
        val text = attr.text.filterNot { it.isWhitespace() }
        text == name || text.equals("$name=true", ignoreCase = true)
    }

/** Top-level function of a file in an @ClassName folder, other than the constructor. */
private fun isClassFolderMethod(function: MatlabFunctionDeclaration): Boolean {
    if (function.parent !is MatlabFile) return false
    val dir = function.containingFile.originalFile.virtualFile?.parent?.name ?: return false
    return dir.startsWith("@") && (function as PsiNamedElement).name != dir.substring(1)
}
