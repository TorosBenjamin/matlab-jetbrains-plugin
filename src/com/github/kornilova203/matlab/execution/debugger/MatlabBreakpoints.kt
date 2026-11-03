package com.github.kornilova203.matlab.execution.debugger

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpoint

/** A breakpoint as MATLAB gets it: `dbstop in [file] at [line] if [condition]`; [line] counts from 1. */
data class MatlabBreakpointSpec(val file: String, val line: Int, val condition: String?) {
    /** One line of the list `ide_breakpoints__` reads: file, line, condition, separated by tabs. */
    fun toLine(): String = file + "\t" + line + "\t" + condition.orEmpty().replace(Regex("\\s+"), " ").trim() + "\n"
}

/** A frame of the call stack where MATLAB is stopped; [line] counts from 1. */
data class MatlabFrame(val file: String, val name: String, val line: Int) {
    companion object {
        /** Parses what `ide_stack__` prints, leaving out MATLAB's own run() that runs a script. */
        fun parse(text: String): List<MatlabFrame> = text.lineSequence()
            .map { it.trimEnd('\r') }
            .mapNotNull { line ->
                val parts = line.split('\t')
                val number = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                MatlabFrame(parts[0], parts[1], number)
            }
            .filterNot { it.name == "run" && FileUtil.toSystemIndependentName(it.file).contains("/toolbox/matlab/") }
            .toList()
    }
}

/** The project's MATLAB breakpoints, for the console's MATLAB. */
object MatlabBreakpoints {
    private fun breakpoints(project: Project): Collection<XLineBreakpoint<*>> {
        val type = XDebuggerUtil.getInstance().findBreakpointType(MatlabLineBreakpointType::class.java) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return XDebuggerManager.getInstance(project).breakpointManager.getBreakpoints(type) as Collection<XLineBreakpoint<*>>
    }

    /** The enabled breakpoints that stop (not just log), as MATLAB gets them. */
    fun specs(project: Project): List<MatlabBreakpointSpec> = ReadAction.compute<List<MatlabBreakpointSpec>, RuntimeException> {
        breakpoints(project)
            .filter { it.isEnabled && it.suspendPolicy != SuspendPolicy.NONE }
            .map {
                val path = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(it.fileUrl))
                MatlabBreakpointSpec(path, it.line + 1, it.conditionExpression?.expression?.takeIf { c -> c.isNotBlank() })
            }
            .sortedWith(compareBy({ it.file }, { it.line }))
    }

    /**
     * Shows which breakpoints MATLAB refused (what `ide_breakpoints__` answers: file, line, message per line)
     * with an error icon and MATLAB's message; the others look normal again.
     */
    fun showRefused(project: Project, answer: String) {
        val refused = answer.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            val number = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            (FileUtil.toSystemIndependentName(parts[0]) to number) to parts.getOrElse(2) { "" }
        }.toMap()
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            val manager = XDebuggerManager.getInstance(project).breakpointManager
            for (breakpoint in breakpoints(project)) {
                val key = VfsUtilCore.urlToPath(breakpoint.fileUrl) to breakpoint.line + 1
                val message = refused[key]
                manager.updateBreakpointPresentation(breakpoint,
                    if (message != null) AllIcons.Debugger.Db_invalid_breakpoint else null,
                    message?.let { "MATLAB couldn't set this breakpoint: $it" })
            }
        }, project.disposed)
    }
}
