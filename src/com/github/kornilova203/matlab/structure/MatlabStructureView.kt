package com.github.kornilova203.matlab.structure

import com.github.kornilova203.matlab.Icons
import com.github.kornilova203.matlab.psi.*
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import javax.swing.Icon

/**
 * Structure panel: classes (properties, methods, events, enumeration members),
 * functions (with nested functions) and `%%` code sections of scripts.
 */
class MatlabStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is MatlabFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel = MatlabStructureViewModel(psiFile, editor)
            override fun isRootNodeShown(): Boolean = false
        }
    }
}

class MatlabStructureViewModel(file: MatlabFile, editor: Editor?) :
    StructureViewModelBase(file, editor, MatlabStructureElement(file)), StructureViewModel.ElementInfoProvider {

    init {
        // Lets "Autoscroll from Source" select the element under the caret.
        withSuitableClasses(
            MatlabFunctionDeclaration::class.java, MatlabClassDeclaration::class.java, MatlabProperty::class.java,
            MatlabMethodSignature::class.java, MatlabEnumItem::class.java
        )
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean {
        val value = element.value
        return value is MatlabProperty || value is MatlabMethodSignature || value is MatlabEnumItem ||
                value is MatlabRefExpr || value is PsiComment
    }
}

class MatlabStructureElement(private val element: PsiElement) :
    PsiTreeElementBase<PsiElement>(element) {

    override fun getChildrenBase(): Collection<StructureViewTreeElement> = children(element).map(::MatlabStructureElement)

    override fun getPresentableText(): String? = when (element) {
        is MatlabFile -> element.name
        is MatlabFunctionDeclaration -> functionText(element)
        is MatlabMethodSignature -> signatureName(element) + (element.parameters?.text ?: "")
        is MatlabClassDeclaration, is MatlabProperty, is MatlabEnumItem -> (element as PsiNamedElement).name
        is MatlabRefExpr -> element.text  // event
        is PsiComment -> sectionTitle(element)
        else -> element.text
    }

    override fun getLocationString(): String? = when (element) {
        is MatlabProperty -> element.className?.text
        is MatlabClassDeclaration -> element.superClasses?.text
        is MatlabRefExpr -> "event"
        else -> null
    }

    override fun getIcon(open: Boolean): Icon? = when (element) {
        is MatlabFile -> Icons.Matlab
        is MatlabClassDeclaration -> AllIcons.Nodes.Class
        is MatlabFunctionDeclaration -> if (isMethod(element)) AllIcons.Nodes.Method else AllIcons.Nodes.Function
        is MatlabMethodSignature -> AllIcons.Nodes.AbstractMethod
        is MatlabProperty -> AllIcons.Nodes.Property
        is MatlabEnumItem -> AllIcons.Nodes.Enum
        is MatlabRefExpr -> AllIcons.Nodes.Annotationtype
        is PsiComment -> AllIcons.Nodes.Tag
        else -> null
    }

    companion object {
        fun children(element: PsiElement): List<PsiElement> = when (element) {
            is MatlabFile -> topLevel(element)
            is MatlabClassDeclaration -> classMembers(element)
            is MatlabFunctionDeclaration -> element.block?.children?.filterIsInstance<MatlabFunctionDeclaration>() ?: emptyList()
            else -> emptyList()
        }

        private fun topLevel(file: MatlabFile): List<PsiElement> = file.children.filter {
            it is MatlabClassDeclaration || it is MatlabFunctionDeclaration || (it is PsiComment && sectionTitle(it) != null)
        }

        private fun classMembers(cls: MatlabClassDeclaration): List<PsiElement> {
            // Keep source order across blocks of different kinds.
            val members = mutableListOf<PsiElement>()
            PsiTreeUtil.processElements(cls) { el ->
                when (el) {
                    is MatlabProperty, is MatlabMethodSignature, is MatlabEnumItem -> members += el
                    is MatlabFunctionDeclaration -> if (el.parent is MatlabMethodsList) members += el
                    is MatlabRefExpr -> if (el.parent is MatlabEventsList) members += el
                }
                true
            }
            return members
        }

        private fun isMethod(function: MatlabFunctionDeclaration) = function.parent is MatlabMethodsList

        private fun functionText(function: MatlabFunctionDeclaration): String {
            val accessor = function.getterOrSetterModifier?.text ?: ""  // get. / set.
            return accessor + ((function as PsiNamedElement).name ?: "") + (function.parameters?.text ?: "()")
        }

        private fun signatureName(signature: MatlabMethodSignature): String =
            signature.node.findChildByType(MatlabTypes.IDENTIFIER)?.text ?: ""

        /** `%% Title` starts a code section; `%%` lines made only of percent signs are just separators. */
        fun sectionTitle(comment: PsiComment): String? {
            if (comment.elementType != MatlabTypes.COMMENT) return null
            val text = comment.text
            if (!text.startsWith("%%") || text.startsWith("%%%")) return null
            if (text.length > 2 && !text[2].isWhitespace()) return null
            return text.substring(2).trim().ifEmpty { "Section" }
        }
    }
}
