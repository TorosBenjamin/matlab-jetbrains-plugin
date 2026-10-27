package com.github.kornilova203.matlab.execution

import com.intellij.ide.util.PropertiesComponent
import junit.framework.TestCase

class CommandLineTest : RunConfigurationTestCase() {
    override fun tearDown() {
        try {
            // setInterpreterPath() also remembers the path as the default for new configurations.
            PropertiesComponent.getInstance().unsetValue(MatlabRunConfiguration.MATLAB_INTERPRETER)
            PropertiesComponent.getInstance(project).unsetValue(MatlabRunConfiguration.MATLAB_INTERPRETER)
        } finally {
            super.tearDown()
        }
    }

    private fun configuration(interpreter: String, file: String, options: String = ""): MatlabRunConfiguration {
        val config = createConfiguration(myFixture.configureByText("script.m", "x = 1;"))!!
        config.setInterpreterPath(interpreter)
        config.setFilePath(file)
        config.setInterpreterOptions(options)
        return config
    }

    fun testMatlabUsesBatch() {
        val cmd = MatlabCommandLineState.createCommandLine("/work", configuration("/opt/matlab/bin/matlab", "/work/main.m"))
        TestCase.assertEquals("/opt/matlab/bin/matlab", cmd.exePath)
        TestCase.assertEquals("-batch", cmd.parametersList.list[0])
        TestCase.assertTrue(cmd.parametersList.list[1].startsWith("run('/work/main.m');"))
    }

    fun testOctaveUsesEval() {
        val cmd = MatlabCommandLineState.createCommandLine("/work", configuration("/usr/bin/octave-cli", "/work/main.m"))
        TestCase.assertEquals("--eval", cmd.parametersList.list[0])
    }

    fun testQuoteInPathIsEscaped() {
        val cmd = MatlabCommandLineState.createCommandLine("/work", configuration("/opt/matlab/bin/matlab", "/home/o'brien/main.m"))
        TestCase.assertTrue(cmd.parametersList.list[1].startsWith("run('/home/o''brien/main.m');"))
    }

    fun testOptionsWithQuotesStayTogether() {
        val cmd = MatlabCommandLineState.createCommandLine("/work",
            configuration("/opt/matlab/bin/matlab", "/work/main.m", "-sd \"/my dir\" -nojvm"))
        TestCase.assertEquals(listOf("-sd", "/my dir", "-nojvm", "-batch"), cmd.parametersList.list.take(4))
    }
}
