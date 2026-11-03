package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.intellij.execution.configurations.GeneralCommandLine
import com.github.kornilova203.matlab.MatlabLanguage
import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * One running MATLAB (or Octave) that keeps its workspace between runs, shown in a tab of the MATLAB tool
 * window. Commands are sent as lines to its standard input; what the user types in the console goes there too.
 *
 *  - MATLAB on macOS and Linux: `matlab -nodesktop -nosplash`, which reads commands from standard input
 *  - MATLAB on Windows: [MatlabEngineBridge] in a separate Java process, since there `matlab -nodesktop`
 *    opens its own command window instead
 *  - Octave: `octave-cli --interactive`
 */
class MatlabConsoleSession(val project: Project, val name: String, val interpreter: String, workingDirectory: String?) : Disposable {
    private val kind = Kind.of(interpreter)
    val processHandler: ProcessHandler

    /** The session's variables, if this kind of session can list them (MATLAB and Octave). */
    val workspace = MatlabWorkspace()
    private val listsWorkspace = kind != Kind.OTHER

    /** Set when a command was sent: the workspace is listed again once MATLAB is back at its prompt. */
    @Volatile
    private var workspaceChanged = true

    /** MATLAB is at its prompt, waiting for a command (so hidden queries are answered right away). */
    @Volatile
    var isIdle = false
        private set

    private val queryIds = java.util.concurrent.atomic.AtomicInteger()
    private val memberAnswers = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>>()
    /** Members by expression, for the current workspace; cleared when it changes. */
    private val memberCache = java.util.concurrent.ConcurrentHashMap<String, List<MatlabMember>>()

    /**
     * Output above, a MATLAB input line below it, with completion and history (up and down). The plain console,
     * not the builder's: that one adds a gutter with markers and lines between the commands.
     */
    val console: LanguageConsoleView = LanguageConsoleImpl(project, name, MatlabLanguage.INSTANCE)

    /** The prompt MATLAB showed last: `>> `, or `K>> ` in the debugger. */
    @Volatile
    private var currentPrompt = MatlabOutputFilter.PROMPTS[0]
    /** When MATLAB last printed something, to notice it waiting for an answer to input(). */
    @Volatile
    private var lastOutput = 0L

    init {
        Disposer.register(this, console)
        LanguageConsoleBuilder.registerExecuteAction(console, { typed(it) }, "matlab", null) { isAlive }
        // MATLAB's own prompts are taken out of the output; the input line shows the current one
        console.prompt = currentPrompt
        // Like MATLAB's command window: no prompt and no typing while MATLAB is busy, starting up included
        if (listsWorkspace) console.isEditable = false
        console.virtualFile.putUserData(MatlabWorkspace.KEY, workspace)
        workspace.members = { expression -> members(expression) }
        val commandLine = commandLine(kind, interpreter, workingDirectory)
        // Not the colored handler: its escape-code decoding mustn't touch the workspace markers
        processHandler = KillableProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler, project, "\n$name exited with code \$EXIT_CODE$\n")
        val filter = MatlabOutputFilter(
            onHidden = { tag, text ->
                if (tag == MatlabConsoleCode.WORKSPACE_TAG) {
                    memberCache.clear()
                    workspace.update(MatlabWorkspace.parse(text))
                } else {
                    memberAnswers.remove(tag)?.complete(text)
                }
            },
            onPrompt = { prompt ->
                isIdle = true
                currentPrompt = prompt
                showInput(prompt)
                if (workspaceChanged) {
                    workspaceChanged = false
                    try {
                        write(MatlabConsoleCode.workspaceQuery())
                    } catch (e: java.io.IOException) {
                        // No Variables then; the console itself works on
                    }
                }
            })
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val type = ConsoleViewContentType.getConsoleViewType(outputType)
                val shown = if (listsWorkspace && outputType != ProcessOutputTypes.SYSTEM && !ProcessOutputType.isStderr(outputType))
                    filter.feed(event.text) else event.text
                if (shown.isNotEmpty()) {
                    console.print(shown, type)
                    val at = System.nanoTime()
                    lastOutput = at
                    if (listsWorkspace && !isIdle && !shown.endsWith('\n')) waitingForAnswer(at)
                }
            }
        })
        processHandler.startNotify()
    }

    val isAlive: Boolean get() = !processHandler.isProcessTerminating && !processHandler.isProcessTerminated

    /**
     * Runs [command] (one line), shown in the console as if typed. [code] is what it runs (a script's text),
     * if that's not [command] itself; if either calls `clc`, the console is cleared first.
     */
    fun send(command: String, code: String? = null) {
        if (callsClc(command) || code != null && callsClc(code)) clearConsole()
        console.print(currentPrompt, console.promptAttributes)
        console.print(command + "\n", ConsoleViewContentType.USER_INPUT)
        execute(command)
    }

    /** What the user entered in the console's input (already shown in the output). */
    private fun typed(text: String) {
        if (callsClc(text)) clearConsole()
        val lines = text.lines().dropLastWhile { it.isBlank() }
        // Several lines (a loop, say) run as a script: the Windows engine takes one line per command
        execute(if (lines.size <= 1) text.trim() else MatlabConsoleCode.runCode(text))
    }

    private fun execute(command: String) {
        workspaceChanged = true
        isIdle = false
        if (listsWorkspace) onEdt { console.isEditable = false }
        memberCache.clear()
        write(command)
    }

    /** Lets the user type again, after [prompt] (`>> `, or `K>> ` while MATLAB is stopped in the debugger). */
    private fun showInput(prompt: String) = onEdt {
        console.prompt = prompt
        console.isEditable = true
    }

    /**
     * Output that ends mid-line and then nothing more: MATLAB waits for an answer (`input('Name: ')`), so the
     * input line opens, without a prompt (the question is the prompt).
     */
    private fun waitingForAnswer(at: Long) {
        com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().schedule({
            if (lastOutput == at && !isIdle && isAlive) onEdt {
                console.prompt = ""
                console.isEditable = true
            }
        }, 400, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        val run = { if (!disposed) action() }
        if (application.isDispatchThread) run() else application.invokeLater(run, com.intellij.openapi.application.ModalityState.any())
    }

    /**
     * The fields, properties and methods of [expression] (`s`, `s.data(2)`), asked from MATLAB. Empty if MATLAB
     * is busy or doesn't answer within a second or two. Waits, so call it in the background (completion does).
     */
    fun members(expression: String): List<MatlabMember> {
        memberCache[expression]?.let { return it }
        if (!isAlive || !isIdle || !listsWorkspace) return emptyList()
        val id = queryIds.incrementAndGet()
        val answer = java.util.concurrent.CompletableFuture<String>()
        memberAnswers["m$id"] = answer
        try {
            write(MatlabConsoleCode.membersQuery(id, expression))
        } catch (e: java.io.IOException) {
            memberAnswers.remove("m$id")
            return emptyList()
        }
        val deadline = System.currentTimeMillis() + 2_000
        while (!answer.isDone && System.currentTimeMillis() < deadline) {
            // Typing cancels completion: don't hold it up
            try {
                com.intellij.openapi.progress.ProgressManager.checkCanceled()
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                // The answer still comes and is dropped; keep it for the next try
                answer.thenAccept { memberCache[expression] = MatlabMember.parse(it) }
                throw e
            }
            try {
                answer.get(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
            }
        }
        val text = answer.getNow(null)
        if (text == null) {
            memberAnswers.remove("m$id")
            return emptyList()
        }
        return MatlabMember.parse(text).also { memberCache[expression] = it }
    }

    /**
     * MATLAB's `clc` can't clear this console (it isn't MATLAB's command window), so the console is cleared
     * when a command or script calls it, before it runs.
     */
    private fun clearConsole() {
        console.clear()
        // Keep the input line as it was (prompt and all)
        val prompt = currentPrompt
        onEdt { if (console.isEditable) console.prompt = prompt }
    }

    @Synchronized
    private fun write(line: String) {
        val input = processHandler.processInput ?: return
        try {
            input.write((line + "\n").toByteArray(kind.charset))
            input.flush()
        } catch (e: java.io.IOException) {
            console.print("Couldn't send the command: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    /** Stops the running command (like Ctrl+C in MATLAB) without ending the session. Returns false if it can't. */
    fun interrupt(): Boolean {
        if (!isAlive) return false
        if (kind == Kind.MATLAB_ENGINE) {
            write(MatlabEngineBridge.INTERRUPT)
            return true
        }
        if (SystemInfo.isWindows) return false
        val process = (processHandler as? BaseOSProcessHandler)?.process ?: return false
        // SIGINT, as Ctrl+C in a terminal
        return try {
            ProcessBuilder("kill", "-INT", process.pid().toString()).start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** Ends MATLAB: asks it to exit (so MATLAB started by the engine closes too), then stops the process. */
    @Volatile
    private var disposed = false

    override fun dispose() {
        disposed = true
        if (!isAlive) return
        write("exit")
        val handler = processHandler
        ApplicationManager.getApplication().executeOnPooledThread {
            if (!handler.waitFor(10_000)) handler.destroyProcess()
        }
    }

    enum class Kind(val charset: java.nio.charset.Charset) {
        MATLAB(java.nio.charset.Charset.defaultCharset()),
        MATLAB_ENGINE(StandardCharsets.UTF_8),
        OCTAVE(java.nio.charset.Charset.defaultCharset()),
        /** Anything else: started as it is, e.g. a wrapper script. */
        OTHER(java.nio.charset.Charset.defaultCharset());

        companion object {
            fun of(interpreter: String, windows: Boolean = SystemInfo.isWindows): Kind {
                // Either separator: a Windows path is recognized wherever this runs
                val name = interpreter.substringAfterLast('/').substringAfterLast('\\').lowercase()
                return when {
                    MatlabInstallations.isOctave(interpreter) -> OCTAVE
                    name.startsWith("matlab") -> if (windows) MATLAB_ENGINE else MATLAB
                    else -> OTHER
                }
            }
        }
    }

    companion object {
        private val CLC = Regex("""(?<![\w.'"])clc\s*(\(\s*\))?\s*(?=[;,]|$)""")

        /** Whether [code] calls `clc` as a statement (not in a comment, not `x.clc` or `clc = 1`). */
        fun callsClc(code: String): Boolean = code.lineSequence().any { line ->
            CLC.containsMatchIn(line.substringBefore('%'))
        }

        fun commandLine(kind: Kind, interpreter: String, workingDirectory: String?): GeneralCommandLine {
            val commandLine = when (kind) {
                Kind.MATLAB -> GeneralCommandLine(interpreter, "-nodesktop", "-nosplash")
                Kind.OCTAVE -> {
                    val cli = File(interpreter).name.lowercase().startsWith("octave-cli")
                    if (cli) GeneralCommandLine(interpreter, "--interactive")
                    else GeneralCommandLine(interpreter, "--no-gui", "--interactive")
                }
                Kind.MATLAB_ENGINE -> engineCommandLine(interpreter)
                Kind.OTHER -> GeneralCommandLine(interpreter)
            }
            workingDirectory?.let { commandLine.withWorkDirectory(it) }
            return commandLine.withCharset(kind.charset)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }

        /**
         * `java -cp <this plugin>;<matlabroot>/extern/engines/java/jar/engine.jar MatlabEngineBridge`, run by the
         * IDE's own Java, with MATLAB's native libraries on PATH as the Engine API requires.
         */
        private fun engineCommandLine(interpreter: String): GeneralCommandLine {
            val root = MatlabInstallations.matlabRootOf(interpreter)
                ?: throw IllegalStateException("Can't find the MATLAB installation of $interpreter")
            val engineJar = File(root, "extern/engines/java/jar/engine.jar")
            if (!engineJar.isFile) throw IllegalStateException("MATLAB's Java engine isn't in this installation: $engineJar")
            val java = File(System.getProperty("java.home"), "bin/" + if (SystemInfo.isWindows) "java.exe" else "java")
            val classPath = listOfNotNull(PathManager.getJarPathForClass(MatlabEngineBridge::class.java), engineJar.path)
                .joinToString(File.pathSeparator)
            val native = File(root, "bin/" + nativeFolder()).path
            val pathVariable = if (SystemInfo.isWindows) "PATH" else if (SystemInfo.isMac) "DYLD_LIBRARY_PATH" else "LD_LIBRARY_PATH"
            val path = listOfNotNull(native, System.getenv(pathVariable)).joinToString(File.pathSeparator)
            // The engine loads MATLAB's native libraries; without this, Java 22+ warns about it on every run
            return GeneralCommandLine(java.path, "--enable-native-access=ALL-UNNAMED", "-cp", classPath, MatlabEngineBridge::class.java.name)
                .withEnvironment(pathVariable, path)
        }

        private fun nativeFolder(): String = when {
            SystemInfo.isWindows -> "win64"
            SystemInfo.isMac -> if (System.getProperty("os.arch") == "aarch64") "maca64" else "maci64"
            else -> "glnxa64"
        }
    }
}
