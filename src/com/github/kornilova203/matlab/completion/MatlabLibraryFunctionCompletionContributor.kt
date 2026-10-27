package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.MatlabLibraryDocs
import com.github.kornilova203.matlab.completion.MatlabKeywordCompletionContributor.Companion.M
import com.github.kornilova203.matlab.psi.MatlabQualifiedExpr
import com.github.kornilova203.matlab.psi.MatlabRefExpr
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

class MatlabLibraryFunctionCompletionContributor : CompletionContributor() {
    companion object {
        val KEYWORDS = hashSetOf("function", "end", "if", "else", "elseif", "while", "switch", "case", "otherwise", "for", "classdef", "try", "catch", "global", "spmd", "parfor")

        private val IDENT = psiElement(MatlabTypes.IDENTIFIER).withParent(MatlabRefExpr::class.java)
        private val IN_QUALIFIED_EXPR = and(M, IDENT.withSuperParent(2, MatlabQualifiedExpr::class.java))
        private val AFTER_NUMBER_LITERAL = psiElement().afterLeafSkipping(
                alwaysFalse<PsiElement>(),
                or(psiElement(MatlabTypes.INTEGER), psiElement(MatlabTypes.FLOAT))
        )

        /** Top-level names (functions and package roots), built on first completion. */
        private val library: Library by lazy { Library.build(MatlabLibraryDocs.functionNames) }
    }

    private class Library(val topLevel: List<LookupElement>, val packageMembers: Map<String, List<LookupElement>>) {
        companion object {
            fun build(names: List<String>): Library {
                val topLevel = LinkedHashMap<String, LookupElement>()
                val members = HashMap<String, LinkedHashMap<String, LookupElement>>()
                for (name in names) {
                    if (name in KEYWORDS) continue
                    val parts = name.split('.')
                    for (i in 0 until parts.size - 1) {
                        topLevel.getOrPut(parts[i]) { packageElement(parts[i]) }
                        val next = parts[i + 1]
                        val element = if (i + 1 == parts.size - 1) createFunctionLookupElement(next) else packageElement(next)
                        if (element != null) members.getOrPut(parts[i]) { LinkedHashMap() }.putIfAbsent(next, element)
                    }
                    val last = parts.last()
                    if (last !in topLevel) createFunctionLookupElement(last)?.let { topLevel[last] = it }
                }
                return Library(topLevel.values.toList(), members.mapValues { it.value.values.toList() })
            }

            private fun packageElement(name: String) = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Package)
        }
    }

    init {
        extend(CompletionType.BASIC,
                M,
                object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                        val element = parameters.position
                        if (AFTER_NUMBER_LITERAL.accepts(element)) {
                            result.stopHere()
                            return
                        }
                        if (IN_QUALIFIED_EXPR.accepts(element)) {
                            val qualifier = (element.parent.parent as? MatlabQualifiedExpr)?.firstChild
                            val name = qualifier?.lastChild?.text ?: return
                            library.packageMembers[name]?.let { result.addAllElements(it) }
                        } else {
                            result.addAllElements(library.topLevel)
                        }
                    }
                })
    }
}
