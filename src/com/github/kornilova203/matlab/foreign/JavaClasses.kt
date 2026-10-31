package com.github.kornilova203.matlab.foreign

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Finds Java classes with the IDE's Java support (registered when the Java plugin is installed),
 * which also knows the JDK and the project's libraries.
 */
interface MatlabJavaResolver {
    /** The class named [qualifiedName] (java.util.ArrayList, com.example.Outer.Inner). */
    fun findClass(project: Project, qualifiedName: String): PsiElement?

    /** Method, field or inner class [name] of [cls], which this resolver returned. */
    fun findMember(cls: PsiElement, name: String): PsiElement?

    companion object {
        val EP_NAME = ExtensionPointName.create<MatlabJavaResolver>("io.github.bendzsi.matlab.javaResolver")
    }
}

/**
 * Java code called from MATLAB: `java.util.ArrayList()`, `com.example.Util.parse(x)`, `javaObject('com.example.Util')`.
 * Without the Java plugin, classes are found as source files by their path (com/example/Util.java).
 */
object JavaClasses {
    /** A class [segments] (com, example, Util), or member of class [segments] minus the last one. */
    fun resolve(project: Project, segments: List<String>, near: PsiFile? = null): PsiElement? {
        findClass(project, segments, near)?.let { return it }
        if (segments.size < 3) return null  // members of classes in the default package aren't worth the lookups
        val cls = findClass(project, segments.dropLast(1), near) ?: return null
        return findMember(cls, segments.last())
    }

    fun findClass(project: Project, segments: List<String>, near: PsiFile? = null): PsiElement? {
        val name = segments.lastOrNull() ?: return null
        // Class names start with a capital letter; this skips struct fields and packages
        if (name.isEmpty() || !name[0].isUpperCase()) return null
        val qualifiedName = segments.joinToString(".")
        for (resolver in MatlabJavaResolver.EP_NAME.extensionList) {
            resolver.findClass(project, qualifiedName)?.let { return it }
        }
        // Outer.Inner: Outer.java
        for (i in segments.indices) {
            if (!segments[i].first().isUpperCase()) continue
            val source = ForeignFiles.byPath(project, segments.subList(0, i), segments[i] + ".java", near).firstOrNull() ?: continue
            val file = ForeignFiles.psi(project, source) ?: continue
            var target: PsiElement = file
            var from = 0
            for (nested in segments.subList(i, segments.size)) {
                val offset = findDeclaration(ForeignFiles.text(file).substring(from), classDeclaration(nested))?.plus(from) ?: return target
                target = foreignTarget(file, offset, nested)
                from = offset
            }
            return target
        }
        return null
    }

    fun findMember(cls: PsiElement, name: String): PsiElement? {
        for (resolver in MatlabJavaResolver.EP_NAME.extensionList) {
            resolver.findMember(cls, name)?.let { return it }
        }
        val file = cls.containingFile ?: return null
        val text = ForeignFiles.text(file)
        val from = if (cls is PsiFile) 0 else cls.textOffset
        val n = Regex.escape(name)
        val offset = findDeclaration(text.substring(from),
            classDeclaration(name),
            // [modifiers] type name(  — but not `return name(`, `new name(` or `x = name(`
            Regex("^[ \\t]*(?!return\\b|new\\b|throw\\b|else\\b)(?:[\\w<>\\[\\],.?@]+[ \\t]+)+($n)[ \\t]*\\(", RegexOption.MULTILINE),
            // [modifiers] type name = ...;  or  type name;
            Regex("^[ \\t]*(?!return\\b|throw\\b)(?:[\\w<>\\[\\],.?@]+[ \\t]+)+($n)[ \\t]*[=;]", RegexOption.MULTILINE))
            ?: return null
        return foreignTarget(file, from + offset, name)
    }

    private fun classDeclaration(name: String) = Regex("\\b(?:class|interface|enum|record)[ \\t]+(${Regex.escape(name)})\\b")
}
