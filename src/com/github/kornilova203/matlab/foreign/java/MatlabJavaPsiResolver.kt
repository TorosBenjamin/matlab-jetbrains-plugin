package com.github.kornilova203.matlab.foreign.java

import com.github.kornilova203.matlab.foreign.MatlabJavaResolver
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

/** Java classes through the Java plugin: project sources, libraries and the JDK. Loaded only with that plugin. */
class MatlabJavaPsiResolver : MatlabJavaResolver {
    override fun findClass(project: Project, qualifiedName: String): PsiElement? =
        JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project))

    override fun findMember(cls: PsiElement, name: String): PsiElement? {
        if (cls !is PsiClass) return null
        return cls.findMethodsByName(name, true).firstOrNull()
            ?: cls.findFieldByName(name, true)
            ?: cls.findInnerClassByName(name, true)
    }
}
