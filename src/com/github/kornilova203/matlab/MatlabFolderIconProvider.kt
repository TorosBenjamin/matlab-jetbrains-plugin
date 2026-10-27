package com.github.kornilova203.matlab

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import javax.swing.Icon

/** Package icon for MATLAB +package folders, class icon for @Class folders. */
class MatlabFolderIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        val dir = (element as? PsiDirectory)?.virtualFile ?: return null
        val name = dir.name
        if (name.length < 2 || (name[0] != '+' && name[0] != '@')) return null
        // Only if it really is MATLAB code (npm uses @scope folders too).
        val isMatlab = dir.children.any { it.extension == "m" || (it.isDirectory && (it.name.startsWith("+") || it.name.startsWith("@"))) }
        if (!isMatlab) return null
        return if (name[0] == '+') AllIcons.Nodes.Package else AllIcons.Nodes.Class
    }
}
