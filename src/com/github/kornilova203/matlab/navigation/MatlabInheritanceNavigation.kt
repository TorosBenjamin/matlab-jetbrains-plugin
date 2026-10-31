package com.github.kornilova203.matlab.navigation

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabInheritance
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.Processor

/**
 * Gutter icons on class and method names:
 *   class Shape        ↓ is subclassed by Circle, Square
 *   method area        ↑ overrides Shape.area   ↓ is overridden in Circle, Square
 */
class MatlabInheritanceLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun getName(): String = "MATLAB inheritance"

    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        if (element.elementType != MatlabTypes.IDENTIFIER) return
        val owner = element.parent as? PsiNameIdentifierOwner ?: return
        if (owner.nameIdentifier != element) return
        when (owner) {
            is MatlabClassDeclaration -> {
                val subclasses = MatlabInheritance.allSubclasses(owner)
                if (subclasses.isNotEmpty()) {
                    result += NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
                        .setTargets(subclasses)
                        .setTooltipText("Is subclassed by " + names(subclasses))
                        .setPopupTitle("Subclasses of ${owner.name}")
                        .createLineMarkerInfo(element)
                }
            }
            is MatlabFunctionDeclaration -> {
                val superMethods = MatlabInheritance.superMethods(owner)
                if (superMethods.isNotEmpty()) {
                    result += NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                        .setTargets(superMethods)
                        .setTooltipText("Overrides method in " + names(superMethods.mapNotNull(MatlabInheritance::classOf)))
                        .setPopupTitle("Super Methods")
                        .createLineMarkerInfo(element)
                }
                val overriding = MatlabInheritance.overridingMethods(owner)
                if (overriding.isNotEmpty()) {
                    result += NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
                        .setTargets(overriding)
                        .setTooltipText("Is overridden in " + names(overriding.mapNotNull(MatlabInheritance::classOf)))
                        .setPopupTitle("Overriding Methods")
                        .createLineMarkerInfo(element)
                }
            }
        }
    }

    private fun names(elements: List<PsiElement>): String =
        elements.mapNotNull { (it as? PsiNamedElement)?.name }.distinct().joinToString(", ")
}

/** Navigate | Implementation(s): subclasses of a class, methods that override a method. */
class MatlabDefinitionsSearch : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>) {
        val targets = when (val element = queryParameters.element) {
            is MatlabClassDeclaration -> MatlabInheritance.allSubclasses(element)
            is MatlabFunctionDeclaration -> MatlabInheritance.overridingMethods(element)
            else -> return
        }
        for (target in targets) {
            if (!consumer.process(target)) return
        }
    }
}

/** Navigate | Super Method (Ctrl+U): the overridden method, or the superclasses of the class at the caret. */
class MatlabGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean = file is MatlabFile

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val targets = superTargets(file.findElementAt(editor.caretModel.offset) ?: return)
        when {
            targets.isEmpty() -> return
            targets.size == 1 -> (targets[0] as? Navigatable)?.navigate(true)
            else -> JBPopupFactory.getInstance().createPopupChooserBuilder(targets)
                .setTitle("Choose Super")
                .setRenderer(SimpleListCellRenderer.create("") { target ->
                    (target as? PsiNamedElement)?.name + " (" + target.containingFile?.name + ")"
                })
                .setItemChosenCallback { (it as? Navigatable)?.navigate(true) }
                .createPopup()
                .showInBestPositionFor(editor)
        }
    }

    companion object {
        /** The methods overridden by the method around [element]; else the superclasses of the class around it. */
        fun superTargets(element: PsiElement): List<PsiElement> {
            var function = PsiTreeUtil.getParentOfType(element, MatlabFunctionDeclaration::class.java, false)
            while (function != null) {
                if (MatlabInheritance.classOf(function) != null) return MatlabInheritance.superMethods(function)
                function = PsiTreeUtil.getParentOfType(function, MatlabFunctionDeclaration::class.java, true)
            }
            val cls = PsiTreeUtil.getParentOfType(element, MatlabClassDeclaration::class.java, false) ?: return emptyList()
            return MatlabInheritance.superClasses(cls)
        }
    }
}
