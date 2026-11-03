package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.psi.MatlabFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

/** Tools | New MATLAB Console, and + in the MATLAB tool window: another MATLAB session in a new tab. */
class MatlabNewConsoleAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        MatlabConsoleManager.getInstance(project).newSession()
    }
}

/** Runs the selected code, or the current line, in the MATLAB console (Alt+Shift+E). */
class MatlabRunSelectionAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null &&
                e.getData(CommonDataKeys.PSI_FILE) is MatlabFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val code = editor.selectionModel.selectedText?.takeIf { it.isNotBlank() } ?: run {
            val line = document.getLineNumber(editor.caretModel.offset)
            document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
        }
        if (code.isBlank()) return
        MatlabConsoleManager.getInstance(project).run(MatlabConsoleCode.runCode(code))
    }
}
