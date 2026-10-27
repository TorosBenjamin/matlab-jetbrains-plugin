package com.github.kornilova203.matlab.psi

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

/**
 * Links a method signature in a classdef (`a = area(obj)`) to the method's own file in the class folder
 * (@Shape/area.m), for Ctrl+click and so that renaming the method updates the signature.
 */
class MatlabMethodSignatureReference(signature: MatlabMethodSignature, private val name: PsiElement) :
    PsiReferenceBase<MatlabMethodSignature>(signature, name.textRangeInParent) {

    override fun resolve(): PsiElement? {
        val cls = PsiTreeUtil.getParentOfType(element, MatlabClassDeclaration::class.java) ?: return null
        return MatlabPackages.methodFiles(cls).firstOrNull { (it as PsiNamedElement).name == name.text }
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        name.replace(createIdentifierFromText(element.project, newElementName))
        return element
    }
}

class MatlabMethodSignatureReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(MatlabMethodSignature::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val name = element.node.findChildByType(MatlabTypes.IDENTIFIER)?.psi ?: return PsiReference.EMPTY_ARRAY
                return arrayOf(MatlabMethodSignatureReference(element as MatlabMethodSignature, name))
            }
        })
    }
}
