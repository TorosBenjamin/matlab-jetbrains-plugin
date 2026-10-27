package com.github.kornilova203.matlab.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

/**
 * @author Liudmila Kornilova
 **/
open class MatlabASTWrapperPsiElement(node : ASTNode) : ASTWrapperPsiElement(node)  {
    /** The element's own reference plus those from reference contributors (e.g. method signatures). */
    override fun getReferences(): Array<PsiReference> {
        val contributed = ReferenceProvidersRegistry.getReferencesFromProviders(this)
        val own = reference ?: return contributed
        return arrayOf(own, *contributed)
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(${node.elementType})"
    }
}
