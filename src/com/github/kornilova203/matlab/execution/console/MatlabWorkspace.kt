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

    /** Asks the session for the members of an expression (see [MatlabConsoleSession.members]). */
    @Volatile
    var members: (String) -> List<MatlabMember> = { emptyList() }

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

/** A field, property or method of a value in the workspace, for completion after `x.`. */
data class MatlabMember(val name: String, val kind: Kind) {
    enum class Kind { FIELD, PROPERTY, METHOD }

    companion object {
        /** Parses what `ide_members__` prints: a kind letter (f, p, m), a tab and the name, one a line. */
        fun parse(text: String): List<MatlabMember> = text.lineSequence()
            .map { it.trimEnd('\r') }
            .mapNotNull { line ->
                val kind = when (line.substringBefore('\t')) {
                    "f" -> Kind.FIELD
                    "p" -> Kind.PROPERTY
                    "m" -> Kind.METHOD
                    else -> return@mapNotNull null
                }
                line.substringAfter('\t').takeIf { it.isNotBlank() }?.let { MatlabMember(it, kind) }
            }
            .distinctBy { it.name }
            .toList()
    }
}

/**
 * Takes MATLAB's output apart:
 *  - answers to the plugin's hidden queries (between [BEGIN] and [END]; the first line is a tag saying which
 *    query) go to [onHidden], and the prompt MATLAB prints after them is dropped
 *  - MATLAB's prompts (`>> `, `K>> ` in the debugger) are taken out, since the console's input line shows the
 *    prompt; [onPrompt] gets the prompt when MATLAB waits for a command
 *
 * [feed] returns the rest, to show.
 */
class MatlabOutputFilter(private val onHidden: (tag: String, text: String) -> Unit, private val onPrompt: (String) -> Unit) {
    private val hidden = StringBuilder()
    private var inHidden = false
    /** After a hidden answer: its prompt is dropped without [onPrompt]. */
    private var afterHidden = false
    /** The start of the output that may be the start of a prompt; held back until the rest comes. */
    private var pending = ""
    private var atLineStart = true
    /** The prompt the shown output ends with, if it does; null once more output follows. */
    private var waiting: String? = null
    private var newPrompt = false

    fun feed(chunk: String): String {
        val visible = StringBuilder()
        newPrompt = false
        var text = pending + chunk
        pending = ""
        while (text.isNotEmpty()) {
            if (inHidden) {
                val end = text.indexOf(END)
                if (end < 0) {
                    hidden.append(text)
                    if (hidden.length > MAX_HIDDEN) {
                        // Not an answer after all: show it
                        visible.append(hidden)
                        hidden.setLength(0)
                        inHidden = false
                    }
                    break
                }
                hidden.append(text, 0, end)
                val answer = hidden.toString()
                hidden.setLength(0)
                inHidden = false
                onHidden(answer.substringBefore('\n'), answer.substringAfter('\n', ""))
                afterHidden = true
                atLineStart = true
                text = text.substring(end + 1)
                continue
            }
            if (afterHidden) {
                val trimmed = text.trimStart('\r', '\n')
                if (trimmed.isEmpty()) break
                text = trimmed
            }
            val begin = text.indexOf(BEGIN)
            val part = if (begin < 0) text else text.substring(0, begin)
            stripPrompts(part, visible)
            if (begin < 0) break
            inHidden = true
            text = text.substring(begin + 1)
        }
        val prompt = waiting
        if (newPrompt && prompt != null && !inHidden && pending.isEmpty()) onPrompt(prompt)
        return visible.toString()
    }

    /** Copies [text] to [out] without the prompts at the start of a line (or at the very end). */
    private fun stripPrompts(text: String, out: StringBuilder) {
        var i = 0
        while (i < text.length) {
            val prompt = PROMPTS.firstOrNull { text.startsWith(it, i) }
            if (prompt != null && (atLineStart || i + prompt.length == text.length)) {
                // A prompt after output on the same line (fprintf('a')): end that line instead
                if (!atLineStart) out.append('\n')
                i += prompt.length
                atLineStart = true
                if (afterHidden) {
                    afterHidden = false
                } else {
                    waiting = prompt
                    newPrompt = true
                }
                continue
            }
            if (atLineStart && PROMPTS.any { it.length > text.length - i && it.startsWith(text.substring(i)) }) {
                pending = text.substring(i)
                break
            }
            afterHidden = false
            val c = text[i]
            out.append(c)
            atLineStart = c == '\n'
            waiting = null
            i++
        }
    }

    companion object {
        const val BEGIN = '\u0002'
        const val END = '\u0004'
        val PROMPTS = listOf(">> ", "K>> ")
        private const val MAX_HIDDEN = 2_000_000
    }
}
