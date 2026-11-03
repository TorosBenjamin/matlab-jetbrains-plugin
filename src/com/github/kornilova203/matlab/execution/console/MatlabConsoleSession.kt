package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
    val console: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    private val kind = Kind.of(interpreter)
    val processHandler: ProcessHandler

    init {
        Disposer.register(this, console)
        val commandLine = commandLine(kind, interpreter, workingDirectory)
        processHandler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler, project, "\n$name exited with code \$EXIT_CODE$\n")
        console.attachToProcess(processHandler)
        processHandler.startNotify()
    }

    val isAlive: Boolean get() = !processHandler.isProcessTerminating && !processHandler.isProcessTerminated

    /** Runs [command] (one line), shown in the console as if typed. */
    fun send(command: String) {
        console.print(command + "\n", ConsoleViewContentType.USER_INPUT)
        write(command)
    }

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
        val process = (processHandler as? KillableColoredProcessHandler)?.process ?: return false
        // SIGINT, as Ctrl+C in a terminal
        return try {
            ProcessBuilder("kill", "-INT", process.pid().toString()).start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** Ends MATLAB: asks it to exit (so MATLAB started by the engine closes too), then stops the process. */
    override fun dispose() {
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
            return GeneralCommandLine(java.path, "-cp", classPath, MatlabEngineBridge::class.java.name)
                .withEnvironment(pathVariable, path)
        }

        private fun nativeFolder(): String = when {
            SystemInfo.isWindows -> "win64"
            SystemInfo.isMac -> if (System.getProperty("os.arch") == "aarch64") "maca64" else "maci64"
            else -> "glnxa64"
        }
    }
}
