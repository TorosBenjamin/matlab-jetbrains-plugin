package com.github.kornilova203.matlab.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.util.execution.ParametersListUtil
import java.io.File


class MatlabCommandLineState(private val runConfiguration: MatlabRunConfiguration, environment: ExecutionEnvironment)
    : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val workingDir = ProgramParametersUtil.getWorkingDir(runConfiguration, runConfiguration.project, null)
        val processHandler = KillableColoredProcessHandler(createCommandLine(workingDir, runConfiguration))
        ProcessTerminatedListener.attach(processHandler, environment.project)
        return processHandler
    }

    companion object {
        internal fun createCommandLine(workingDir: String, runConfiguration: MatlabRunConfiguration): GeneralCommandLine {
            val interpreter = runConfiguration.getInterpreterPath()!!
            val cmd = GeneralCommandLine().withExePath(interpreter)

            runConfiguration.getInterpreterOptions()?.let { cmd.addParameters(ParametersListUtil.parse(it)) }

            // Quotes inside a MATLAB char literal are escaped by doubling them.
            val file = runConfiguration.getFilePath()!!.replace("'", "''")
            // Keep the process alive while figure windows are open.
            val code = "run('$file'); if ~isempty(get(0, 'Children')), uiwait; end"
            cmd.addParameters(if (isOctave(interpreter)) "--eval" else "-batch", code)

            return cmd.withWorkDirectory(workingDir)
                .withParentEnvironmentType(
                    if (runConfiguration.isPassParentEnvs) GeneralCommandLine.ParentEnvironmentType.CONSOLE
                    else GeneralCommandLine.ParentEnvironmentType.NONE
                )
                .withEnvironment(runConfiguration.envs)
        }

        private fun isOctave(interpreter: String): Boolean = File(interpreter).name.lowercase().contains("octave")
    }
}
