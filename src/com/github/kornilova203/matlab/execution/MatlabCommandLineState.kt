package com.github.kornilova203.matlab.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.util.execution.ParametersListUtil
import com.github.kornilova203.matlab.settings.MatlabInstallations
import java.io.File


class MatlabCommandLineState(private val runConfiguration: MatlabRunConfiguration, environment: ExecutionEnvironment)
    : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        // A section runs from the script's folder, like the whole script does via run().
        val workingDir = if (runConfiguration.sectionLines != null && runConfiguration.workingDirectory.isNullOrBlank())
            File(runConfiguration.getFilePath()!!).parent
        else ProgramParametersUtil.getWorkingDir(runConfiguration, runConfiguration.project, null)
        val processHandler = KillableColoredProcessHandler(createCommandLine(workingDir, runConfiguration))
        ProcessTerminatedListener.attach(processHandler, environment.project)
        return processHandler
    }

    companion object {
        internal fun createCommandLine(workingDir: String, runConfiguration: MatlabRunConfiguration): GeneralCommandLine {
            val interpreter = MatlabInstallations.resolveInterpreter(runConfiguration.getInterpreterPath())!!
            val cmd = GeneralCommandLine().withExePath(interpreter)

            runConfiguration.getInterpreterOptions()?.let { cmd.addParameters(ParametersListUtil.parse(it)) }

            // Keep the process alive while figure windows are open.
            val waitForFigures = "if ~isempty(get(0, 'Children')), uiwait; end"
            val section = runConfiguration.sectionLines
            val code = if (section == null) {
                // Quotes inside a MATLAB char literal are escaped by doubling them.
                "run('${quote(runConfiguration.getFilePath()!!)}'); $waitForFigures"
            } else {
                // The section's lines become a temporary script, called by name so the working
                // directory stays the script's folder (run() would switch to the temporary folder).
                val script = sectionScript(runConfiguration.getFilePath()!!, section)
                "addpath('${quote(script.parent)}'); ${script.nameWithoutExtension}; $waitForFigures"
            }
            cmd.addParameters(if (isOctave(interpreter)) "--eval" else "-batch", code)

            return cmd.withWorkDirectory(workingDir)
                .withParentEnvironmentType(
                    if (runConfiguration.isPassParentEnvs) GeneralCommandLine.ParentEnvironmentType.CONSOLE
                    else GeneralCommandLine.ParentEnvironmentType.NONE
                )
                .withEnvironment(runConfiguration.envs)
        }

        private fun quote(path: String) = path.replace("'", "''")

        /** The text of [lines] (0-based, inclusive) of the file. */
        internal fun sectionCode(filePath: String, lines: IntRange): String {
            val source = File(filePath).readLines()
            return source.subList(lines.first.coerceIn(0, source.size), (lines.last + 1).coerceIn(0, source.size)).joinToString("\n")
        }

        internal fun sectionScript(filePath: String, lines: IntRange): File {
            val source = File(filePath).readLines()
            val code = source.subList(lines.first.coerceIn(0, source.size), (lines.last + 1).coerceIn(0, source.size))
            val dir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("matlab-section", null, true)
            // A valid MATLAB name: letters, digits and underscores, starting with a letter.
            val name = "section_" + Integer.toHexString((filePath + lines).hashCode())
            return File(dir, "$name.m").apply { writeText(code.joinToString("\n") + "\n") }
        }

        private fun isOctave(interpreter: String): Boolean = File(interpreter).name.lowercase().contains("octave")
    }
}

/** Runs the file or section in the MATLAB tool window's session (started if there's none), which keeps running. */
class MatlabConsoleRunState(private val configuration: MatlabRunConfiguration) : com.intellij.execution.configurations.RunProfileState {
    override fun execute(executor: com.intellij.execution.Executor?, runner: com.intellij.execution.runners.ProgramRunner<*>): com.intellij.execution.ExecutionResult? {
        val path = configuration.getFilePath() ?: return null
        val section = configuration.sectionLines
        val code = if (section == null) java.io.File(path).takeIf { it.isFile }?.readText()
        else MatlabCommandLineState.sectionCode(path, section)
        val command = if (section == null) com.github.kornilova203.matlab.execution.console.MatlabConsoleCode.runFile(path)
        else com.github.kornilova203.matlab.execution.console.MatlabConsoleCode.runCode(code!!)
        com.github.kornilova203.matlab.execution.console.MatlabConsoleManager.getInstance(configuration.project)
            .run(command, configuration.getInterpreterPath(), code)
        // The output is in the MATLAB tool window, not in a Run tab
        return null
    }
}
