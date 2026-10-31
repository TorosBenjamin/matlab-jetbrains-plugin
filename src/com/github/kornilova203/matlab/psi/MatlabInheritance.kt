package com.github.kornilova203.matlab.psi

import com.github.kornilova203.matlab.stub.MatlabClassDeclarationIndex
import com.github.kornilova203.matlab.stub.MatlabSubclassIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * Class inheritance: `classdef Circle < Shape & pkg.Named`. Superclasses that aren't in the project or on
 * the MATLAB path (handle, matlab.mixin.Copyable...) are skipped.
 */
object MatlabInheritance {
    /** "Shape", "pkg.Named" from `classdef Circle < Shape & pkg.Named`. */
    fun superClassNames(cls: MatlabClassDeclaration): List<String> {
        val text = cls.superClasses?.text ?: return emptyList()
        return text.removePrefix("<").replace(Regex("\\.\\.\\.[^\\n]*\\n"), " ").split('&')
            .map { part -> part.filterNot { it.isWhitespace() } }.filter { it.isNotEmpty() }
    }

    /** Direct superclasses defined in the project or on the MATLAB path. */
    fun superClasses(cls: MatlabClassDeclaration): List<MatlabClassDeclaration> =
        CachedValuesManager.getCachedValue(cls) {
            CachedValueProvider.Result.create(superClassNames(cls).mapNotNull { findClass(cls, it) },
                PsiModificationTracker.MODIFICATION_COUNT)
        }

    /** All superclasses, nearest first; each once, even with cycles or diamonds. */
    fun allSuperClasses(cls: MatlabClassDeclaration): List<MatlabClassDeclaration> {
        val result = LinkedHashSet<MatlabClassDeclaration>()
        var current = listOf(cls)
        while (current.isNotEmpty()) {
            current = current.flatMap(::superClasses).filter { it != cls && result.add(it) }
        }
        return result.toList()
    }

    /** Classes that list [cls] as a superclass. */
    fun directSubclasses(cls: MatlabClassDeclaration): List<MatlabClassDeclaration> {
        val name = (cls as PsiNamedElement).name ?: return emptyList()
        val project = cls.project
        val result = ArrayList<MatlabClassDeclaration>()
        StubIndex.getInstance().processElements(MatlabSubclassIndex.KEY, name, project, GlobalSearchScope.allScope(project),
            MatlabClassDeclaration::class.java) { candidate ->
            if (candidate != cls && superClasses(candidate).contains(cls)) result += candidate
            true
        }
        return result
    }

    /** All classes that inherit from [cls], directly or not. */
    fun allSubclasses(cls: MatlabClassDeclaration): List<MatlabClassDeclaration> {
        val result = LinkedHashSet<MatlabClassDeclaration>()
        var current = listOf(cls)
        while (current.isNotEmpty()) {
            current = current.flatMap(::directSubclasses).filter { it != cls && result.add(it) }
        }
        return result.toList()
    }

    /** Methods defined in [cls]: in its methods blocks and in the files of its @-folder. */
    fun ownMethods(cls: MatlabClassDeclaration): List<MatlabFunctionDeclaration> =
        PsiTreeUtil.findChildrenOfType(cls, MatlabMethodsList::class.java).flatMap { it.functionDeclarationList } +
                MatlabPackages.methodFiles(cls)

    /** The class that [function] is a method of, or null if it's an ordinary function. */
    fun classOf(function: MatlabFunctionDeclaration): MatlabClassDeclaration? {
        if (function.parent is MatlabMethodsList) return PsiTreeUtil.getParentOfType(function, MatlabClassDeclaration::class.java)
        if (function.parent is MatlabFile) {
            val file = function.containingFile
            if (file.children.firstOrNull { it is MatlabFunctionDeclaration } != function) return null
            return MatlabPackages.classOfMethodFile(file.originalFile.virtualFile, function.project)
        }
        return null
    }

    /** The methods that [method] overrides: the nearest one with its name in each line of superclasses. */
    fun superMethods(method: MatlabFunctionDeclaration): List<MatlabFunctionDeclaration> {
        val cls = classOf(method) ?: return emptyList()
        val name = (method as PsiNamedElement).name ?: return emptyList()
        if (name == (cls as PsiNamedElement).name) return emptyList()  // constructors don't override
        val result = LinkedHashSet<MatlabFunctionDeclaration>()
        fun search(c: MatlabClassDeclaration, visited: MutableSet<MatlabClassDeclaration>) {
            for (superClass in superClasses(c)) {
                if (!visited.add(superClass)) continue
                val found = ownMethods(superClass).firstOrNull { (it as PsiNamedElement).name == name }
                if (found != null) result += found else search(superClass, visited)
            }
        }
        search(cls, hashSetOf(cls))
        return result.toList()
    }

    /** Methods in subclasses that override [method]. */
    fun overridingMethods(method: MatlabFunctionDeclaration): List<MatlabFunctionDeclaration> {
        val cls = classOf(method) ?: return emptyList()
        val name = (method as PsiNamedElement).name ?: return emptyList()
        if (name == (cls as PsiNamedElement).name) return emptyList()
        return allSubclasses(cls).flatMap { sub -> ownMethods(sub).filter { (it as PsiNamedElement).name == name } }
    }

    /** Whether members in [element]'s block are hidden from subclasses: `methods (Access = private)`. */
    fun isPrivate(element: PsiElement): Boolean {
        val block = PsiTreeUtil.getParentOfType(element, MatlabMethodsBlock::class.java, MatlabPropertiesBlock::class.java)
        val attributes = when (block) {
            is MatlabMethodsBlock -> block.attributes
            is MatlabPropertiesBlock -> block.attributes
            else -> null
        } ?: return false
        val text = attributes.text.filterNot { it.isWhitespace() }.lowercase()
        return Regex("(^|[(,])(get)?access=('|\")?private").containsMatchIn(text)
    }

    /** The class named [name] ("Shape" or "pkg.Shape") as seen from [context]: the project's first, then the path. */
    private fun findClass(context: PsiElement, name: String): MatlabClassDeclaration? {
        val project = context.project
        if (name.contains('.')) {
            return MatlabPackages.memberDeclarations(project, name.substringBeforeLast('.'), name.substringAfterLast('.'))
                .firstNotNullOfOrNull { it as? MatlabClassDeclaration }
        }
        for (scope in listOf(GlobalSearchScope.projectScope(project), ProjectScope.getLibrariesScope(project))) {
            var found: MatlabClassDeclaration? = null
            StubIndex.getInstance().processElements(MatlabClassDeclarationIndex.KEY, name, project, scope,
                MatlabClassDeclaration::class.java) { candidate ->
                if (candidate.containingFile.name == "$name.m" && MatlabPackages.isVisibleByPlainName(candidate, context)) {
                    found = candidate
                    false
                } else true
            }
            found?.let { return it }
        }
        return null
    }
}
