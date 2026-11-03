package com.github.kornilova203.matlab.execution.debugger

import com.github.kornilova203.matlab.MatlabFileType
import com.github.kornilova203.matlab.execution.console.MatlabConsoleSession
import com.github.kornilova203.matlab.execution.console.MatlabVariable
import com.github.kornilova203.matlab.execution.console.MatlabWorkspace
import com.intellij.execution.ExecutionException
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.LocalTimeCounter
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*

/**
 * The IDE's debugger for one MATLAB console session. There's no separate debug run: when MATLAB stops at a
 * breakpoint (its prompt turns into `K>>`), a debug tab opens with the line, the call stack and the variables,
 * and its buttons send `dbstep`, `dbcont` and so on. When MATLAB leaves the debugger, the tab's session ends.
 */
class MatlabDebugger(private val matlab: MatlabConsoleSession) {
    private var process: MatlabDebugProcess? = null
    /** The last debug tab, reused for the next stop. */
    private var descriptor: RunContentDescriptor? = null

    /** MATLAB stopped (at a breakpoint, after a step, or after a command typed at `K>>`) with [frames]. */
    fun stoppedAt(frames: List<MatlabFrame>) = onEdt {
        val current = process ?: start() ?: return@onEdt
        current.stoppedAt(frames)
    }

    /** MATLAB left the debugger (dbcont to the end, dbquit, an error): the debug session ends. */
    fun left() = onEdt {
        val current = process ?: return@onEdt
        process = null
        current.leaving = true
        current.session.stop()
    }

    internal fun ended(ended: MatlabDebugProcess) {
        if (process === ended) process = null
    }

    private fun start(): MatlabDebugProcess? = try {
        var created: MatlabDebugProcess? = null
        @Suppress("DEPRECATION") // newSessionBuilder() isn't in all supported IDE versions
        val session = XDebuggerManager.getInstance(matlab.project).startSessionAndShowTab(matlab.name, descriptor,
            object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    MatlabDebugProcess(session, matlab, this@MatlabDebugger).also { created = it }
            })
        descriptor = session.runContentDescriptor
        process = created
        created
    } catch (e: ExecutionException) {
        null
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action()
        else application.invokeLater({ if (!matlab.project.isDisposed) action() }, ModalityState.any())
    }
}

class MatlabDebugProcess(session: XDebugSession, private val matlab: MatlabConsoleSession, private val debugger: MatlabDebugger) :
    XDebugProcess(session) {
    /** Set when MATLAB itself left the debugger: stopping the session then sends nothing. */
    @Volatile
    var leaving = false
    private val evaluator = MatlabEvaluator(matlab)

    override fun getEditorsProvider(): XDebuggerEditorsProvider = MatlabDebuggerEditorsProvider(matlab.workspace)
    override fun getEvaluator(): XDebuggerEvaluator = evaluator

    fun stoppedAt(frames: List<MatlabFrame>) {
        session.positionReached(MatlabSuspendContext(frames.mapIndexed { index, frame ->
            MatlabStackFrame(frame, index == 0, matlab, evaluator)
        }))
    }

    override fun startStepOver(context: XSuspendContext?) = matlab.debugCommand("dbstep")
    override fun startStepInto(context: XSuspendContext?) = matlab.debugCommand("dbstep in")
    override fun startStepOut(context: XSuspendContext?) = matlab.debugCommand("dbstep out")
    override fun resume(context: XSuspendContext?) = matlab.debugCommand("dbcont")

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) =
        matlab.runToLine(position.file.path, position.line + 1)

    override fun stop() {
        // The debug tab's stop button: leave the debugger, keep MATLAB running
        if (!leaving && matlab.isInDebugger) matlab.debugCommand("dbquit")
        debugger.ended(this)
    }

    /** The output stays in the MATLAB tool window, where commands can be typed at K>>. */
    override fun createConsole(): ExecutionConsole {
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(session.project).console
        console.print("MATLAB's output and K>> prompt are in the MATLAB tool window (${matlab.name}).\n",
            ConsoleViewContentType.SYSTEM_OUTPUT)
        return console
    }
}

private class MatlabSuspendContext(frames: List<MatlabStackFrame>) : XSuspendContext() {
    private val stack = MatlabExecutionStack(frames)
    override fun getActiveExecutionStack(): XExecutionStack = stack
    override fun getExecutionStacks(): Array<XExecutionStack> = arrayOf(stack)
}

private class MatlabExecutionStack(private val frames: List<MatlabStackFrame>) : XExecutionStack("MATLAB") {
    override fun getTopFrame(): XStackFrame? = frames.firstOrNull()
    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        container.addStackFrames(frames.drop(firstFrameIndex), true)
    }
}

private class MatlabStackFrame(
    private val frame: MatlabFrame,
    private val current: Boolean,
    private val matlab: MatlabConsoleSession,
    private val evaluator: XDebuggerEvaluator,
) : XStackFrame() {
    private val position: XSourcePosition? by lazy {
        val path = com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(frame.file)
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: return@lazy null
        XDebuggerUtil.getInstance().createPosition(file, frame.line - 1)
    }

    override fun getSourcePosition(): XSourcePosition? = position
    override fun getEqualityObject(): Any = frame.file + "#" + frame.name
    // Only the current function's workspace can be asked
    override fun getEvaluator(): XDebuggerEvaluator? = if (current) evaluator else null

    override fun customizePresentation(component: ColoredTextContainer) {
        component.append(frame.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        component.append(":${frame.line}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        component.append("  " + frame.file.substringAfterLast('/').substringAfterLast('\\'), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        component.setIcon(AllIcons.Debugger.Frame)
    }

    override fun computeChildren(node: XCompositeNode) {
        val children = XValueChildrenList()
        // The Variables view's list is the current function's workspace (MATLAB lists it at each K>>)
        if (current) matlab.workspace.variables.forEach { children.add(MatlabVariableValue(it)) }
        node.addChildren(children, true)
    }
}

private class MatlabVariableValue(private val variable: MatlabVariable) : XNamedValue(variable.name) {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val value = variable.value.ifEmpty { "[${variable.size} ${variable.className}]" }
        node.setPresentation(AllIcons.Nodes.Variable, "${variable.className} ${variable.size}", value, false)
    }
}

/** A value from Evaluate Expression or hovering a name: MATLAB's disp() of it, in full on demand. */
private class MatlabEvaluatedValue(private val className: String, private val size: String, private val text: String) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val lines = text.lines()
        val short = if (lines.size == 1 && text.length <= 200) text else "[$size $className]"
        node.setPresentation(AllIcons.Nodes.Variable, "$className $size", short, false)
        if (short != text) {
            node.setFullValueEvaluator(object : XFullValueEvaluator() {
                override fun startEvaluation(callback: XFullValueEvaluationCallback) = callback.evaluated(text)
            })
        }
    }
}

/** Evaluates in MATLAB, where it's stopped. */
class MatlabEvaluator(private val matlab: MatlabConsoleSession) : XDebuggerEvaluator() {
    override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
        val answer = matlab.evaluate(expression)
        if (answer == null) {
            callback.errorOccurred("MATLAB is busy")
            return
        }
        answer.whenComplete { text, error ->
            if (text == null) {
                callback.errorOccurred(if (error != null) "MATLAB didn't answer" else "MATLAB is busy")
                return@whenComplete
            }
            val header = text.substringBefore('\n')
            val body = text.substringAfter('\n', "")
            val className = header.substringBefore('\t')
            if (className == "error") callback.errorOccurred(body.ifBlank { "Error" })
            else callback.evaluated(MatlabEvaluatedValue(className, header.substringAfter('\t', ""), body))
        }
    }

    /** Hovering a name (`x`, `s.data`) while stopped shows its value. */
    override fun getExpressionRangeAtOffset(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): TextRange? =
        expressionRange(document.charsSequence, offset)

    companion object {
        private val KEYWORDS = setOf("if", "else", "elseif", "end", "for", "while", "function", "return", "switch", "case",
            "otherwise", "try", "catch", "break", "continue", "global", "persistent", "classdef", "parfor", "spmd")

        /** The name (with fields: `s.data`) at [offset], up to the field under it. */
        fun expressionRange(text: CharSequence, offset: Int): TextRange? {
            fun part(c: Char) = c.isLetterOrDigit() || c == '_'
            if (offset >= text.length || !part(text[offset])) return null
            var end = offset
            while (end < text.length && part(text[end])) end++
            var start = offset
            while (true) {
                while (start > 0 && part(text[start - 1])) start--
                if (start > 1 && text[start - 1] == '.' && part(text[start - 2])) start-- else break
            }
            val expression = text.subSequence(start, end).toString()
            if (!expression[0].isLetter() || expression in KEYWORDS) return null
            return TextRange(start, end)
        }
    }
}

/** MATLAB in Evaluate Expression and breakpoint conditions; with the session's variables in completion. */
class MatlabDebuggerEditorsProvider(private val workspace: MatlabWorkspace?) : XDebuggerEditorsProviderBase() {
    override fun getFileType(): FileType = MatlabFileType

    override fun createExpressionCodeFragment(project: Project, text: String, context: PsiElement?, isPhysical: Boolean): PsiFile {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("expression.m", MatlabFileType, text, LocalTimeCounter.currentTime(), isPhysical)
        if (workspace != null) file.viewProvider.virtualFile.putUserData(MatlabWorkspace.KEY, workspace)
        return file
    }
}
