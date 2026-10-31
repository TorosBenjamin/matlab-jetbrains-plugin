package com.github.kornilova203.matlab.foreign

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Python code called from MATLAB: `py.mod.func(x)`, `py.pkg.mod.Cls(x)`, `obj = py.mod.Cls(); obj.method()`.
 * Modules are found by file name (mod.py, pkg/mod.py, pkg/__init__.py) in the project and its libraries,
 * which in PyCharm include the interpreter's site-packages.
 */
object PythonModules {
    private const val INIT = "__init__.py"

    /** Built-ins that `py.` gives access to directly: py.list, py.dict, py.len... */
    val BUILTINS = listOf("bool", "bytearray", "bytes", "complex", "dict", "float", "frozenset", "int", "len", "list",
        "max", "memoryview", "min", "print", "range", "repr", "set", "slice", "str", "sum", "tuple", "type", "zip")

    /** The file of module [path] (a.b.c): a/b/c.py or a/b/c/__init__.py. */
    fun find(project: Project, path: List<String>, near: PsiFile? = null): VirtualFile? {
        val name = path.last()
        val parents = path.dropLast(1)
        ForeignFiles.byPath(project, parents, "$name.py", near).firstOrNull()?.let { return it }
        return ForeignFiles.byPath(project, path, INIT, near).firstOrNull()
    }

    /** What `py.` followed by [segments] refers to: a module, or a function, class, variable or method in one. */
    fun resolve(project: Project, segments: List<String>, near: PsiFile? = null): PsiElement? {
        for (k in segments.size downTo 1) {
            val module = find(project, segments.subList(0, k), near) ?: continue
            val file = ForeignFiles.psi(project, module) ?: continue
            if (k == segments.size) return file
            val member = segments[k]
            val offset = topLevelDeclaration(ForeignFiles.text(file), member) ?: return null
            if (k + 1 == segments.size) return foreignTarget(file, offset, member)
            if (k + 2 == segments.size) {
                val method = segments[k + 1]
                val methodOffset = classMember(ForeignFiles.text(file), offset, method) ?: return null
                return foreignTarget(file, methodOffset, method)
            }
            return null
        }
        return null
    }

    /** Offset of the name of a top-level `def name`, `class name`, `name = ...` or import of name. */
    fun topLevelDeclaration(text: CharSequence, name: String): Int? {
        val n = Regex.escape(name)
        return findDeclaration(text,
            Regex("^(?:async[ \\t]+)?def[ \\t]+($n)\\b", RegexOption.MULTILINE),
            Regex("^class[ \\t]+($n)\\b", RegexOption.MULTILINE),
            Regex("^($n)[ \\t]*(?::[^=\\n]*)?=(?!=)", RegexOption.MULTILINE),
            Regex("^(?:from[ \\t]+\\S+[ \\t]+)?import[ \\t][^\\n]*?\\b($n)\\b", RegexOption.MULTILINE))
    }

    /** Offset of method or attribute [name] in the body of the class whose name is at [classOffset]. */
    fun classMember(text: CharSequence, classOffset: Int, name: String): Int? {
        // The body ends at the next line that starts in the first column (other than a comment)
        val lineEnd = text.indexOf('\n', classOffset).takeIf { it >= 0 } ?: return null
        val end = Regex("\\n(?=[^\\s#])").find(text, lineEnd)?.range?.first ?: text.length
        val body = text.subSequence(lineEnd, end)
        val n = Regex.escape(name)
        val offset = findDeclaration(body,
            Regex("^[ \\t]+(?:async[ \\t]+)?def[ \\t]+($n)\\b", RegexOption.MULTILINE),
            Regex("^[ \\t]+($n)[ \\t]*(?::[^=\\n]*)?=(?!=)", RegexOption.MULTILINE)) ?: return null
        return lineEnd + offset
    }

    /** Names to complete after `py.` + [segments]: modules, then a module's public functions and classes. */
    fun completions(project: Project, segments: List<String>): List<Pair<String, Kind>> {
        if (segments.isEmpty()) return topLevelModules(project).map { it to Kind.MODULE } + BUILTINS.map { it to Kind.FUNCTION }
        val module = find(project, segments) ?: return emptyList()
        val result = LinkedHashMap<String, Kind>()
        if (module.name == INIT) {
            for (child in module.parent.children) {
                when {
                    child.isDirectory && child.findChild(INIT) != null -> result[child.name] = Kind.MODULE
                    !child.isDirectory && child.extension == "py" && child.name != INIT -> result[child.nameWithoutExtension] = Kind.MODULE
                }
            }
        }
        val text = ForeignFiles.psi(project, module)?.let(ForeignFiles::text) ?: return result.toList()
        for (match in DEFINITION.findAll(text)) {
            val (keyword, name) = match.destructured
            if (!name.startsWith("_")) result.putIfAbsent(name, if (keyword == "class") Kind.CLASS else Kind.FUNCTION)
        }
        return result.toList()
    }

    enum class Kind { MODULE, CLASS, FUNCTION }

    private val DEFINITION = Regex("^(?:async[ \\t]+)?(def|class)[ \\t]+(\\w+)", RegexOption.MULTILINE)

    /** Modules and packages in the project's own folders. */
    private fun topLevelModules(project: Project): List<String> {
        val result = LinkedHashSet<String>()
        for (file in FilenameIndex.getAllFilesByExt(project, "py", GlobalSearchScope.projectScope(project))) {
            var top = file.nameWithoutExtension
            var dir = file.parent
            if (file.name == INIT) {
                top = dir?.name ?: continue
                dir = dir.parent
            }
            while (dir != null && dir.findChild(INIT) != null) {
                top = dir.name
                dir = dir.parent
            }
            if (top.matches(IDENTIFIER) && !top.startsWith("_")) result += top
        }
        return result.sorted()
    }

    private val IDENTIFIER = Regex("[A-Za-z_]\\w*")
}
