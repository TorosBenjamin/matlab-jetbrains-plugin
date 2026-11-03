package com.github.kornilova203.matlab.execution.console

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import java.util.concurrent.CopyOnWriteArrayList

/** A variable in a MATLAB session's workspace, as `whos` lists it. [value] is short, or empty if it's big. */
data class MatlabVariable(val name: String, val size: String, val className: String, val value: String)

/**
 * The variables of a running MATLAB session, refreshed after each command. Shown in the Variables view and
 * offered by completion in the console's input.
 */
class MatlabWorkspace {
    @Volatile
    var variables: List<MatlabVariable> = emptyList()
        private set
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** [listener] runs on whichever thread the update comes from. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun update(variables: List<MatlabVariable>) {
        this.variables = variables
        listeners.forEach { it() }
    }

    companion object {
        /** On the console input's file: the workspace of that console's session. */
        val KEY = Key.create<MatlabWorkspace>("matlab.console.workspace")

        /** The workspace of [file] if it's a console's input. */
        fun of(file: PsiFile?): MatlabWorkspace? =
            file?.originalFile?.virtualFile?.getUserData(KEY) ?: file?.virtualFile?.getUserData(KEY)

        /** Parses what `ide_workspace__` prints between the markers: name, size, class, value; one variable a line. */
        fun parse(text: String): List<MatlabVariable> = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split('\t', limit = 4)
                if (fields.size < 3 || fields[0].isBlank()) null
                else MatlabVariable(fields[0], fields[1], fields[2], fields.getOrElse(3) { "" })
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}

/**
 * Takes MATLAB's output apart: the workspace listings the plugin asks for (between [BEGIN] and [END]) go to
 * [onWorkspace] and the prompt MATLAB prints after them is dropped; [feed] returns the rest, to show.
 * [onPrompt] is called when the shown output ends with MATLAB's prompt, i.e. MATLAB waits for a command.
 */
class MatlabOutputFilter(private val onWorkspace: (String) -> Unit, private val onPrompt: () -> Unit) {
    private val hidden = StringBuilder()
    private var inHidden = false
    private var dropPrompt = false
    private var tail = ""

    fun feed(chunk: String): String {
        val visible = StringBuilder()
        var text = chunk
        while (text.isNotEmpty()) {
            if (inHidden) {
                val end = text.indexOf(END)
                if (end < 0) {
                    hidden.append(text)
                    if (hidden.length > MAX_HIDDEN) {
                        // Not a listing after all: show it
                        visible.append(hidden)
                        hidden.setLength(0)
                        inHidden = false
                    }
                    break
                }
                hidden.append(text, 0, end)
                onWorkspace(hidden.toString())
                hidden.setLength(0)
                inHidden = false
                dropPrompt = true
                text = text.substring(end + 1)
                continue
            }
            if (dropPrompt) {
                val trimmed = text.trimStart('\r', '\n')
                if (trimmed.isEmpty()) break
                dropPrompt = false
                text = if (trimmed.startsWith(PROMPT)) trimmed.substring(PROMPT.length) else text
                continue
            }
            val begin = text.indexOf(BEGIN)
            if (begin < 0) {
                visible.append(text)
                break
            }
            visible.append(text, 0, begin)
            inHidden = true
            text = text.substring(begin + 1)
        }
        if (visible.isNotEmpty()) {
            tail = (tail + visible).takeLast(PROMPT.length)
            if (tail == PROMPT && !inHidden) onPrompt()
        }
        return visible.toString()
    }

    companion object {
        const val BEGIN = '\u0002'
        const val END = '\u0004'
        const val PROMPT = ">> "
        private const val MAX_HIDDEN = 2_000_000
    }
}
