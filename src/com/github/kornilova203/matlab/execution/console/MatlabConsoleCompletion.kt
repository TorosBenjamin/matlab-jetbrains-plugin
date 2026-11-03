package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.psi.MatlabFunctionExpr
import com.github.kornilova203.matlab.psi.MatlabQualifiedExpr
import com.github.kornilova203.matlab.psi.MatlabRefExpr
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement

/** In a MATLAB console's input: the session's variables, first. */
class MatlabConsoleCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val workspace = MatlabWorkspace.of(parameters.originalFile) ?: return
        val ref = parameters.position.parent as? MatlabRefExpr ?: return
        // A plain name, not obj.name
        val node = (ref.parent as? MatlabFunctionExpr)?.takeIf { it.firstChild == ref } ?: ref
        val above = node.parent
        if (above is MatlabQualifiedExpr && above.firstChild != node) return
        for (variable in workspace.variables) {
            val element = LookupElementBuilder.create(variable.name)
                .withIcon(AllIcons.Nodes.Variable)
                .withTailText(if (variable.value.isEmpty()) "  ${variable.size}" else "  ${variable.value}", true)
                .withTypeText(variable.className)
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0))
        }
    }
}

/**
 * The console's input is one command at a time: its variables live on in the workspace, so "unused" there
 * means nothing.
 */
class MatlabConsoleInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        toolId in SUPPRESSED && MatlabWorkspace.of(element.containingFile) != null

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = emptyArray()

    private companion object {
        val SUPPRESSED = setOf("MatlabUnusedVariable", "MatlabUnusedFunction", "MatlabUnusedImport", "MatlabUnreachableCode")
    }
}
