package com.github.kornilova203.matlab.editor.actions

import com.github.kornilova203.matlab.psi.MatlabFile
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Enter in a `%` comment continues it on the next line: when it splits the comment, or at the end of a comment
 * that's part of a block of comment lines (a help text, a description). Enter on an empty `%` line ends the
 * block. Not for `%%` section titles or `%{ ... %}` block comments.
 */
class MatlabEnterInCommentHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>,
                                 dataContext: DataContext, originalHandler: EditorActionHandler?): EnterHandlerDelegate.Result {
        editor.putUserData(CONTINUE_WITH, null)
        if (file !is MatlabFile) return EnterHandlerDelegate.Result.Continue
        val document = editor.document
        val offset = caretOffset.get()
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        val text = document.charsSequence.subSequence(lineStart, document.getLineEndOffset(line)).toString()
        val indent = text.takeWhile { it == ' ' || it == '\t' }
        val comment = text.substring(indent.length)
        if (!isLineComment(comment)) return EnterHandlerDelegate.Result.Continue
        // The caret must be inside the comment's text, after the %
        val column = offset - lineStart
        if (column <= indent.length) return EnterHandlerDelegate.Result.Continue
        val splits = text.substring(column).isNotBlank()
        val previous = if (line > 0) document.charsSequence.subSequence(document.getLineStartOffset(line - 1),
            document.getLineEndOffset(line - 1)).toString().trimStart() else ""
        val inBlock = isLineComment(previous) || previous.startsWith("%%")
        val empty = comment.removePrefix("%").isBlank()
        if (splits || (inBlock && !empty)) editor.putUserData(CONTINUE_WITH, indent + "% ")
        return EnterHandlerDelegate.Result.Continue
    }

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        val prefix = editor.getUserData(CONTINUE_WITH) ?: return EnterHandlerDelegate.Result.Continue
        editor.putUserData(CONTINUE_WITH, null)
        val document = editor.document
        val caret = editor.caretModel.offset
        val lineStart = document.getLineStartOffset(document.getLineNumber(caret))
        val lineEnd = document.getLineEndOffset(document.getLineNumber(caret))
        val text = document.charsSequence.subSequence(lineStart, lineEnd).toString()
        // Already continued (the IDE's own handling of comments)
        if (text.trimStart().startsWith("%")) return EnterHandlerDelegate.Result.Continue
        val textStart = lineStart + (text.length - text.trimStart().length)
        document.replaceString(lineStart, textStart, prefix)
        editor.caretModel.moveToOffset(lineStart + prefix.length)
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        return EnterHandlerDelegate.Result.Continue
    }

    private companion object {
        val CONTINUE_WITH = Key.create<String>("matlab.enter.continueComment")

        /** `% text`, not a `%%` section title or a `%{` `%}` block comment line. */
        fun isLineComment(text: String) = text.startsWith("%") && !text.startsWith("%%") &&
                !text.startsWith("%{") && !text.startsWith("%}")
    }
}
