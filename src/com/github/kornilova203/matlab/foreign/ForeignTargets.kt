package com.github.kornilova203.matlab.foreign

import com.github.kornilova203.matlab.psi.MatlabLiteralExpr
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.elementType
import javax.swing.Icon

/*
 * Code in other languages that MATLAB code uses: Python modules (py.mod.func), Java classes, C/C++ MEX
 * files and C libraries (calllib). The IDE may not understand those languages (a .py file in IntelliJ
 * IDEA without the Python plugin is plain text), so declarations are found by searching the file's text.
 * When the IDE does understand the language, the language's own element is used, so find usages and
 * rename work from both sides.
 */

/** The declaration of [name] at [offset] in [file]. */
fun foreignTarget(file: PsiFile, offset: Int, name: String): PsiElement {
    var element = file.findElementAt(offset)
    while (element != null && element !is PsiFile) {
        if (element is PsiNamedElement && element.name == name && element.textOffset == offset) return element
        element = element.parent
    }
    return ForeignDeclaration(file, offset, name)
}

/** A declaration in a file of a language the IDE doesn't understand: opens the file at it. */
class ForeignDeclaration(private val file: PsiFile, private val offset: Int, private val name: String) : FakePsiElement() {
    override fun getParent(): PsiElement = file
    override fun getContainingFile(): PsiFile = file
    override fun getName(): String = name
    override fun getTextOffset(): Int = offset
    override fun getTextRange(): TextRange = TextRange(offset, offset + name.length)
    override fun getNavigationElement(): PsiElement = this
    override fun isValid(): Boolean = file.isValid
    override fun canNavigate(): Boolean = file.virtualFile != null
    override fun canNavigateToSource(): Boolean = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        file.virtualFile?.let { OpenFileDescriptor(file.project, it, offset).navigate(requestFocus) }
    }
    override fun getPresentableText(): String = name
    override fun getLocationString(): String = file.name
    override fun getIcon(open: Boolean): Icon? = file.getIcon(0)
    override fun equals(other: Any?): Boolean = other is ForeignDeclaration && other.file == file && other.offset == offset
    override fun hashCode(): Int = file.hashCode() * 31 + offset
    override fun toString(): String = "ForeignDeclaration($name in ${file.name})"
}

object ForeignFiles {
    /**
     * Files named [name] in the project and its libraries: those in [near]'s folder first, then the
     * project's own, then the shortest paths.
     */
    fun byName(project: Project, name: String, near: PsiFile? = null): List<VirtualFile> {
        val dir = near?.originalFile?.virtualFile?.parent
        val index = ProjectFileIndex.getInstance(project)
        return FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.allScope(project))
            .filter { !it.isDirectory }
            .sortedWith(compareBy<VirtualFile>({ it.parent != dir }, { !index.isInContent(it) }, { it.path.length }))
    }

    /** Like [byName], but only files in folders [parents] (outermost first), e.g. com/example/Util.java. */
    fun byPath(project: Project, parents: List<String>, name: String, near: PsiFile? = null): List<VirtualFile> =
        byName(project, name, near).filter { inFolders(it.parent, parents) }

    /** Whether [dir] is .../parents[0]/parents[1]/.../parents.last(). */
    fun inFolders(dir: VirtualFile?, parents: List<String>): Boolean {
        var d = dir
        for (i in parents.indices.reversed()) {
            if (d == null || d.name != parents[i]) return false
            d = d.parent
        }
        return true
    }

    /** A file named like the last part of [path] ('src/foo.c', "lib\\foo.h"), next to [near] if [path] is relative. */
    fun find(project: Project, path: String, near: PsiFile): PsiFile? {
        val normalized = path.replace('\\', '/')
        val relative = near.originalFile.virtualFile?.parent?.findFileByRelativePath(normalized)
        val file = relative?.takeIf { !it.isDirectory } ?: byName(project, normalized.substringAfterLast('/'), near).firstOrNull()
        return file?.let { PsiManager.getInstance(project).findFile(it) }
    }

    fun psi(project: Project, file: VirtualFile): PsiFile? = PsiManager.getInstance(project).findFile(file)

    /**
     * The text of [file], also when the IDE has no support for its language and treats it as binary
     * (then the PSI file has no text).
     */
    fun text(file: PsiFile): CharSequence {
        PsiDocumentManager.getInstance(file.project).getDocument(file)?.let { return it.immutableCharSequence }
        val virtualFile = file.originalFile.virtualFile ?: return file.text
        return LoadTextUtil.loadText(virtualFile)
    }
}

/** Offset of group 1 of the first match of the first of [patterns] that matches. */
fun findDeclaration(text: CharSequence, vararg patterns: Regex): Int? {
    for (pattern in patterns) {
        val match = pattern.find(text) ?: continue
        return match.groups[1]?.range?.first ?: continue
    }
    return null
}

/** The value of a string literal, 'abc' or "abc"; null for anything else. */
fun stringValue(element: PsiElement?): String? {
    if (element !is MatlabLiteralExpr) return null
    val token = element.firstChild ?: return null
    if (token.elementType != MatlabTypes.SINGLE_QUOTE_STRING && token.elementType != MatlabTypes.DOUBLE_QUOTE_STRING) return null
    val text = token.text
    return if (text.length >= 2) text.substring(1, text.length - 1) else null
}
