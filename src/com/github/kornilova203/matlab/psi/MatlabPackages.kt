package com.github.kornilova203.matlab.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * MATLAB's folder conventions:
 *   +pkg/        package: its files are used as pkg.name (or after `import pkg.*` / `import pkg.name`)
 *   +pkg/+sub/   subpackage pkg.sub
 *   @Cls/        class folder: Cls.m (classdef or old-style constructor) plus one file per method
 */
object MatlabPackages {
    enum class Kind { PACKAGE, CLASS, FUNCTION }
    class Member(val name: String, val kind: Kind)

    /** Package of a folder, e.g. "geometry.shapes" for .../+geometry/+shapes; null outside packages. */
    fun packageOf(dir: VirtualFile?): String? {
        val parts = ArrayList<String>()
        var d = dir
        while (d != null && d.name.startsWith("+")) {
            parts.add(0, d.name.substring(1))
            d = d.parent
        }
        return if (parts.isEmpty()) null else parts.joinToString(".")
    }

    /** All package folders in the project by package name; one package may span several folders. */
    private fun index(project: Project): Map<String, List<VirtualFile>> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val map = HashMap<String, MutableList<VirtualFile>>()
            ProjectFileIndex.getInstance(project).iterateContent { file ->
                if (file.isDirectory && file.name.startsWith("+")) packageOf(file)?.let { map.getOrPut(it) { ArrayList() } += file }
                true
            }
            CachedValueProvider.Result.create(map as Map<String, List<VirtualFile>>, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }

    fun isPackage(project: Project, name: String): Boolean = index(project).containsKey(name)
    fun rootPackages(project: Project): Set<String> = index(project).keys.map { it.substringBefore('.') }.toSet()

    fun members(project: Project, pkg: String): List<Member> {
        val result = LinkedHashMap<String, Member>()
        for (dir in index(project)[pkg].orEmpty()) {
            for (child in dir.children) {
                when {
                    child.isDirectory && child.name.startsWith("+") -> result.putIfAbsent(child.name.substring(1), Member(child.name.substring(1), Kind.PACKAGE))
                    child.isDirectory && child.name.startsWith("@") -> result.putIfAbsent(child.name.substring(1), Member(child.name.substring(1), Kind.CLASS))
                    !child.isDirectory && child.extension == "m" -> {
                        val kind = if (mainDeclaration(project, child) is MatlabClassDeclaration) Kind.CLASS else Kind.FUNCTION
                        result.putIfAbsent(child.nameWithoutExtension, Member(child.nameWithoutExtension, kind))
                    }
                }
            }
        }
        return result.values.toList()
    }

    /** Declarations of package member [name]: the class, the function, or an @-folder class's constructor. */
    fun memberDeclarations(project: Project, pkg: String, name: String): List<PsiElement> =
        index(project)[pkg].orEmpty().mapNotNull { dir ->
            val file = dir.findChild("$name.m") ?: dir.findChild("@$name")?.findChild("$name.m")
            file?.let { mainDeclaration(project, it) }
        }

    /** The class or first function a file defines. */
    fun mainDeclaration(project: Project, file: VirtualFile): PsiElement? {
        val psi = PsiManager.getInstance(project).findFile(file) as? MatlabFile ?: return null
        return psi.children.firstOrNull { it is MatlabClassDeclaration || it is MatlabFunctionDeclaration }
    }

    /**
     * Whether [declaration] can be used by its plain name from [context]. Package members need their
     * package name or an import; methods in @-folders (value(obj)) are found by plain name.
     */
    fun isVisibleByPlainName(declaration: PsiElement, context: PsiElement): Boolean {
        val vFile = declaration.containingFile?.virtualFile ?: return true
        var dir = vFile.parent ?: return true
        if (dir.name.startsWith("@")) {
            if (vFile.nameWithoutExtension != dir.name.substring(1)) return true  // a method file
            dir = dir.parent ?: return true
        }
        val pkg = packageOf(dir) ?: return true
        val name = (declaration as? PsiNamedElement)?.name ?: return false
        val imports = imports(context)
        return pkg in imports.packages || "$pkg.$name" in imports.names
    }

    class Imports(val packages: Set<String>, val names: Set<String>)

    /** `import pkg.*` and `import pkg.name` statements in the function (or script) around [context]. */
    fun imports(context: PsiElement): Imports {
        val scope = com.github.kornilova203.matlab.psi.types.ownScope(context)
        return CachedValuesManager.getCachedValue(scope) {
            val packages = HashSet<String>()
            val names = HashSet<String>()
            for (statement in PsiTreeUtil.findChildrenOfType(scope, MatlabCommandStatement::class.java)) {
                if (statement.refExpr.text != "import") continue
                for (arg in importArguments(statement)) {
                    if (arg.endsWith(".*")) packages += arg.removeSuffix(".*") else names += arg
                }
            }
            CachedValueProvider.Result.create(Imports(packages, names), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    /** "geometry.*", "geometry.Point" from `import geometry.* geometry.Point;` */
    fun importArguments(statement: MatlabCommandStatement): List<String> =
        statement.commandArguments.text.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() && !it.startsWith("%") }

    /** "a.b.c" for an expression made only of names and dots; null otherwise. */
    fun dottedName(expr: PsiElement?): String? = when (expr) {
        is MatlabRefExpr -> expr.text
        is MatlabQualifiedExpr -> {
            val left = dottedName(expr.firstChild)
            val right = expr.lastChild as? MatlabRefExpr
            if (left != null && right != null) "$left.${right.text}" else null
        }
        else -> null
    }

    /** classdef Cls in @Cls/Cls.m next to [file], if [file] is a method file in a class folder. */
    fun classOfMethodFile(file: VirtualFile?, project: Project): MatlabClassDeclaration? {
        val dir = file?.parent ?: return null
        if (!dir.name.startsWith("@") || file.nameWithoutExtension == dir.name.substring(1)) return null
        val classFile = dir.findChild(dir.name.substring(1) + ".m") ?: return null
        return mainDeclaration(project, classFile) as? MatlabClassDeclaration
    }

    /** Methods defined in separate files of [cls]'s @-folder. */
    fun methodFiles(cls: MatlabClassDeclaration): List<MatlabFunctionDeclaration> {
        val classFile = cls.containingFile?.originalFile?.virtualFile ?: return emptyList()
        val dir = classFile.parent ?: return emptyList()
        if (dir.name != "@" + classFile.nameWithoutExtension) return emptyList()
        return dir.children.filter { it.extension == "m" && it != classFile }
            .mapNotNull { mainDeclaration(cls.project, it) as? MatlabFunctionDeclaration }
    }
}
