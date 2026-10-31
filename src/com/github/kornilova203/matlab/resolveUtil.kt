package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.types.MatlabTypeClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil

fun PsiElement.forEachSiblingBackwards(processor: (PsiElement) -> Boolean): Boolean {
    var el: PsiElement? = this.prevSibling
    while (el != null) {
        if (!processor(el)) return false
        el = el.prevSibling
    }
    return true
}

fun PsiElement.forEachSiblingForward(processor: (PsiElement) -> Boolean): Boolean {
    var el: PsiElement? = this.nextSibling
    while (el != null) {
        if (!processor(el)) return false
        el = el.nextSibling
    }
    return true
}

/** True if no function boundary lies between this and [parent]. Nested functions don't count: they share variables. */
fun PsiElement.inCurrentFunction(parent: PsiElement): Boolean {
    var el: PsiElement = this
    while (el != parent) {
        if (el is MatlabFunctionDeclaration && !isNestedFunction(el)) return false
        el = el.parent
    }
    return true
}

/**
 * A function defined inside another function's body shares that function's variables.
 * In files whose functions don't end with `end`, the parser puts each following function inside the
 * previous one, but those are separate local functions, so the outer one must be closed by `end`.
 */
fun isNestedFunction(function: MatlabFunctionDeclaration): Boolean {
    val outer = (function.parent as? MatlabBlock)?.parent as? MatlabFunctionDeclaration ?: return false
    return outer.lastChild?.node?.elementType == MatlabTypes.END
}

fun PsiElement.findDeclaration(processor: PsiScopeProcessor, state: ResolveState, inCurrentScope: Boolean, declarationIsBeforeUsage: Boolean = true): Boolean {
    if (this is MatlabDeclaration) {
        if ((inCurrentScope || this.visibleOutsideFunction) && (declarationIsBeforeUsage || this.visibleBeforeDeclaration)) {
            return processor.execute(this, state)
        }
    }
    return true
}

fun processDeclarations(parent: PsiElement, processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    val inCurrentScope = place.inCurrentFunction(parent)

    if (lastParent != null && lastParent !is MatlabAssignExpr) {
        if (!lastParent.findDeclaration(processor, state, inCurrentScope)) {
            return false
        }
    }

    val res = lastParent?.forEachSiblingBackwards { it.findDeclaration(processor, state, inCurrentScope) }
    if (res == false) return false

    // A nested function sees the outer function's variables even if they're assigned after its definition.
    val seesLaterDeclarations = lastParent is MatlabFunctionDeclaration && isNestedFunction(lastParent)
    return lastParent?.forEachSiblingForward { it.findDeclaration(processor, state, inCurrentScope, seesLaterDeclarations) } ?: true
}

/** Members of the class of [type]: its own first, then those inherited from superclasses (not private ones). */
fun processDeclarationsInClass(processor: PsiScopeProcessor, state: ResolveState, type: MatlabTypeClass): Boolean {
    val classDeclaration = type.decl
    if (!processClassMembers(processor, state, classDeclaration, inherited = false)) return false
    for (superClass in MatlabInheritance.allSuperClasses(classDeclaration)) {
        if (!processClassMembers(processor, state, superClass, inherited = true)) return false
    }
    return false
}

private fun processClassMembers(processor: PsiScopeProcessor, state: ResolveState, cls: MatlabClassDeclaration, inherited: Boolean): Boolean {
    val declarations = PsiTreeUtil.findChildrenOfAnyType(cls, false,
            MatlabProperty::class.java,
            MatlabFunctionDeclaration::class.java,
            MatlabEnumItem::class.java)
    val className = (cls as PsiNamedElement).name
    for (declaration in declarations + MatlabPackages.methodFiles(cls)) {
        // A subclass doesn't get the superclass's constructor or private members
        if (inherited && (MatlabInheritance.isPrivate(declaration) ||
                    declaration is MatlabFunctionDeclaration && (declaration as PsiNamedElement).name == className)) continue
        if (!processor.execute(declaration, state)) return false
    }
    return true
}