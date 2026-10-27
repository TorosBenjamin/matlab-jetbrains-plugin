package com.github.kornilova203.matlab.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase


class RunConfigurationTest : RunConfigurationTestCase() {

    private val fileContent = """
                |function [] = hello()
                |    fprintf("hello, world\n")
                |end
                """.trimMargin()

    private val interpreterPath = "/usr/local/bin/octave"

    fun testRunConfigurationFromFile() {
        val myFile = myFixture.configureByText("hello.m", fileContent)
        val configuration = createConfiguration(myFile)

        TestCase.assertNotNull(configuration)
        configuration ?: return
        TestCase.assertNull(configuration.getInterpreterPath())
        TestCase.assertEquals(myFile.virtualFile.canonicalPath, configuration.getFilePath())
    }

    private val settings get() = com.github.kornilova203.matlab.settings.MatlabSettings.getInstance()
    private fun resolve(path: String?) = com.github.kornilova203.matlab.settings.MatlabInstallations.resolveInterpreter(path)
    private fun detectNothing() = com.github.kornilova203.matlab.settings.MatlabInstallations
        .setDetectorForTests({ emptyList() }, testRootDisposable)

    override fun tearDown() {
        try {
            settings.matlabPath = null
        } finally {
            super.tearDown()
        }
    }

    fun testSettingIsTheDefault() {
        detectNothing()
        settings.matlabPath = "/opt/MATLAB/R2025b/bin/matlab"
        val configuration = createConfiguration(myFixture.configureByText("hello.m", fileContent))!!
        TestCase.assertNull(configuration.getInterpreterPath())
        TestCase.assertEquals("/opt/MATLAB/R2025b/bin/matlab", resolve(configuration.getInterpreterPath()))
    }

    fun testConfigurationOverridesSetting() {
        settings.matlabPath = "/opt/MATLAB/R2025b/bin/matlab"
        TestCase.assertEquals(interpreterPath, resolve(interpreterPath))
    }

    fun testDetectedWhenNothingIsSet() {
        com.github.kornilova203.matlab.settings.MatlabInstallations
            .setDetectorForTests({ listOf("/usr/local/MATLAB/R2024a/bin/matlab") }, testRootDisposable)
        TestCase.assertEquals("/usr/local/MATLAB/R2024a/bin/matlab", resolve(null))
    }

    fun testRunnerExceptions() {
        detectNothing()
        val myFile = myFixture.configureByText("hello.m", fileContent)
        val configuration = createConfiguration(myFile)!!

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val executionEnvironment = ExecutionEnvironment()
        assertThrows(MatlabCannotRunException::class.java,
                "MATLAB was not found. Set its path in Settings | Languages & Frameworks | MATLAB or in this run configuration.",
                ThrowableRunnable<MatlabCannotRunException> { configuration.getState(executor, executionEnvironment) })

        configuration.setInterpreterPath(interpreterPath)
    }
}
