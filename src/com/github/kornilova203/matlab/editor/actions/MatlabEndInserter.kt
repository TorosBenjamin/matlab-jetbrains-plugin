package com.github.kornilova203.matlab.editor.actions

import com.github.kornilova203.matlab.MatlabElementTypes
import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.github.kornilova203.matlab.settings.MatlabSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

/**
 * Pressing Enter after a line that opens a block (`if x`, `for i = 1:3`, `properties`, ...) inserts the
 * matching `end` below, but only if the file has fewer `end`s than block openers, so pressing Enter
 * inside code that's already closed doesn't add a stray `end`.
 */
class MatlabEndInserter : EnterHandlerDelegateAdapter() {
    private val keywordOpeners = setOf(
        MatlabTypes.IF, MatlabTypes.FOR, MatlabTypes.WHILE, MatlabTypes.SWITCH, MatlabTypes.TRY,
        MatlabTypes.PARFOR, MatlabTypes.SPMD, MatlabTypes.CLASSDEF, MatlabTypes.FUNCTION,
        MatlabElementTypes.PROPERTIES, MatlabElementTypes.METHODS, MatlabElementTypes.EVENTS,
        MatlabElementTypes.ENUMERATION, MatlabElementTypes.ARGUMENTS_KEYWORD,
    )

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (file !is MatlabFile || !MatlabSettings.getInstance().insertEnd) return EnterHandlerDelegate.Result.Continue
        val document = editor.document
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        if (line == 0) return EnterHandlerDelegate.Result.Continue
        // Only when Enter was pressed at the end of the line (nothing moved down with the caret).
        val restOfLine = document.charsSequence.subSequence(caret, document.getLineEndOffset(line))
        if (restOfLine.isNotBlank()) return EnterHandlerDelegate.Result.Continue

        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        val prevStart = document.getLineStartOffset(line - 1)
        val prevText = document.charsSequence.subSequence(prevStart, document.getLineEndOffset(line - 1))
        val indent = prevText.takeWhile { it == ' ' || it == '\t' }.toString()
        val opener = file.findElementAt(prevStart + indent.length) ?: return EnterHandlerDelegate.Result.Continue
        if (opener.elementType !in keywordOpeners) return EnterHandlerDelegate.Result.Continue

        val functionsUseEnd = functionsUseEnd(file)
        if (opener.elementType == MatlabTypes.FUNCTION && !functionsUseEnd) return EnterHandlerDelegate.Result.Continue
        if (!hasUnclosedBlock(file, functionsUseEnd)) return EnterHandlerDelegate.Result.Continue

        document.insertString(document.getLineEndOffset(line), "\n${indent}end")
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        return EnterHandlerDelegate.Result.Continue
    }

    private fun hasUnclosedBlock(file: PsiFile, functionsUseEnd: Boolean): Boolean {
        var balance = 0
        for (leaf in SyntaxTraverser.psiTraverser(file).filter { it.firstChild == null }) {
            when (leaf.elementType) {
                MatlabTypes.END -> balance--  // `end` used as an index (x(end)) is parsed as an identifier
                MatlabTypes.FUNCTION -> if (functionsUseEnd) balance++
                in keywordOpeners -> balance++
            }
        }
        return balance > 0
    }

    /** Functions need `end` in class files, scripts with local functions, and files where other functions use it. */
    private fun functionsUseEnd(file: PsiFile): Boolean {
        if (PsiTreeUtil.findChildOfType(file, MatlabClassDeclaration::class.java) != null) return true
        val firstStatement = file.children.firstOrNull {
            it !is com.intellij.psi.PsiWhiteSpace && it !is com.intellij.psi.PsiComment &&
                    it.elementType != MatlabTypes.NEWLINE && it.elementType != TokenType.WHITE_SPACE
        }
        if (firstStatement != null && firstStatement !is MatlabFunctionDeclaration) return true  // script
        if (PsiTreeUtil.findChildrenOfType(file, MatlabFunctionDeclaration::class.java)
                .any { it.lastChild?.elementType == MatlabTypes.END }) return true
        // While a block is unclosed the parse may not show it, so also accept an `end` lined up with a `function`.
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return false
        val leaves = SyntaxTraverser.psiTraverser(file).filter { it.firstChild == null }.toList()
        fun indentOf(offset: Int): Int? {
            val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
            val before = document.charsSequence.subSequence(lineStart, offset)
            return if (before.isBlank()) before.length else null  // only if the keyword starts the line
        }
        val functionIndents = leaves.filter { it.elementType == MatlabTypes.FUNCTION }
            .mapNotNull { f -> indentOf(f.textOffset)?.let { it to f.textOffset } }
        return leaves.filter { it.elementType == MatlabTypes.END }.any { end ->
            val indent = indentOf(end.textOffset)
            functionIndents.any { (fIndent, fOffset) -> fIndent == indent && fOffset < end.textOffset }
        }
    }
}
