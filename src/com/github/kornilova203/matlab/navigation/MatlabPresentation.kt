package com.github.kornilova203.matlab.navigation

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabMethodsList
import com.github.kornilova203.matlab.psi.MatlabPackages
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/** How MATLAB functions and classes appear in Go to Symbol/Class and other lists: name, where, icon. */
class MatlabPresentationProvider : ItemPresentationProvider<NavigationItem> {
    override fun getPresentation(item: NavigationItem): ItemPresentation? {
        if (item !is MatlabFunctionDeclaration && item !is MatlabClassDeclaration) return null
        return object : ItemPresentation {
            override fun getPresentableText(): String? = when (item) {
                is MatlabFunctionDeclaration -> (item as PsiNamedElement).name + (item.parameters?.text ?: "()")
                else -> (item as PsiNamedElement).name
            }

            override fun getLocationString(): String? {
                val element = item as com.intellij.psi.PsiElement
                val cls = (item as? MatlabFunctionDeclaration)?.takeIf { it.parent is MatlabMethodsList }
                    ?.let { PsiTreeUtil.getParentOfType(it, MatlabClassDeclaration::class.java) as? PsiNamedElement }
                val file = element.containingFile?.virtualFile ?: return cls?.name
                val pkg = MatlabPackages.packageOf(file.parent) ?: MatlabPackages.packageOf(file.parent?.parent)
                return listOfNotNull(cls?.name, pkg?.let { "($it)" } ?: "(${file.name})").joinToString(" ")
            }

            override fun getIcon(unused: Boolean): Icon = when {
                item is MatlabClassDeclaration -> AllIcons.Nodes.Class
                (item as MatlabFunctionDeclaration).parent is MatlabMethodsList -> AllIcons.Nodes.Method
                else -> AllIcons.Nodes.Function
            }
        }
    }
}
