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

/**
 * In a MATLAB console's input: the session's variables, first; after `x.`, the fields, properties and methods
 * of what `x` holds in the workspace, asked from MATLAB.
 */
class MatlabConsoleCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val workspace = MatlabWorkspace.of(parameters.originalFile) ?: return
        val ref = parameters.position.parent as? MatlabRefExpr ?: return
        val node = (ref.parent as? MatlabFunctionExpr)?.takeIf { it.firstChild == ref } ?: ref
        val above = node.parent
        if (above is MatlabQualifiedExpr && above.firstChild != node) {
            addMembers(above.firstChild.text, workspace, result)
            return
        }
        for (variable in workspace.variables) {
            val element = LookupElementBuilder.create(variable.name)
                .withIcon(AllIcons.Nodes.Variable)
                .withTailText(if (variable.value.isEmpty()) "  ${variable.size}" else "  ${variable.value}", true)
                .withTypeText(variable.className)
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0))
        }
    }
}

/** After `x.`: what `x` has, if `x` is a variable (maybe with fields and indexes), so asking runs no function. */
private fun addMembers(qualifier: String, workspace: MatlabWorkspace, result: CompletionResultSet) {
    val expression = qualifier.filterNot { it.isWhitespace() }
    if (!SAFE_EXPRESSION.matches(expression)) return
    val variable = expression.takeWhile { it.isLetterOrDigit() || it == '_' }
    if (workspace.variables.none { it.name == variable }) return
    for (member in workspace.members(expression)) {
        val (icon, type) = when (member.kind) {
            MatlabMember.Kind.FIELD -> AllIcons.Nodes.Field to "field"
            MatlabMember.Kind.PROPERTY -> AllIcons.Nodes.Property to "property"
            MatlabMember.Kind.METHOD -> AllIcons.Nodes.Method to "method"
        }
        val priority = if (member.kind == MatlabMember.Kind.METHOD) 90.0 else 100.0
        result.addElement(PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(member.name).withIcon(icon).withTypeText(type), priority))
    }
}

/** `s`, `s.data`, `s(2).data{3}`: a variable with fields and constant indexes. */
private val SAFE_EXPRESSION = Regex("""[A-Za-z]\w*(\.[A-Za-z]\w*|\(\d+(,\d+)*\)|\{\d+(,\d+)*\})*""")

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
