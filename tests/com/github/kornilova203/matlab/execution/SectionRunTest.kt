package com.github.kornilova203.matlab.execution

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import java.io.File

class SectionRunTest : RunConfigurationTestCase() {
    private val script = "x = 1;\n%% Setup\ny = 2;\nz = 3;\n%% Plot\nplot(y, z)\n"

    override fun tearDown() {
        try {
            com.github.kornilova203.matlab.settings.MatlabSettings.getInstance().matlabPath = null
        } finally {
            super.tearDown()
        }
    }

    private fun sectionHeader(title: String): PsiComment {
        val file = myFixture.configureByText("analysis.m", script)
        return PsiTreeUtil.collectElementsOfType(file, PsiComment::class.java).first { it.text == "%% $title" }
    }

    fun testSectionConfiguration() {
        val header = sectionHeader("Setup")
        val configs = configurationsFor(header)
        val section = configs.firstOrNull { it.sectionLines != null }
        TestCase.assertNotNull(configs.toString(), section)
        TestCase.assertEquals(1..3, section!!.sectionLines)
        TestCase.assertEquals("analysis.m: Setup", section.name)
        TestCase.assertTrue("whole-file run is offered too", configs.any { it.sectionLines == null })
    }

    fun testLastSectionRunsToEnd() {
        val section = configurationsFor(sectionHeader("Plot")).first { it.sectionLines != null }
        TestCase.assertEquals(4..6, section.sectionLines)
    }

    fun testSectionScript() {
        val file = File.createTempFile("analysis", ".m").apply { writeText(script); deleteOnExit() }
        val section = MatlabCommandLineState.sectionScript(file.path, 1..3)
        TestCase.assertEquals("%% Setup\ny = 2;\nz = 3;\n", section.readText())
        TestCase.assertTrue(section.nameWithoutExtension.matches(Regex("[A-Za-z]\\w*")))
    }

    fun testSectionCommandLine() {
        val file = File.createTempFile("analysis", ".m").apply { writeText(script); deleteOnExit() }
        val config = createConfiguration(myFixture.configureByText("x.m", "a = 1;"))!!
        config.setInterpreterPath("/opt/matlab/bin/matlab")
        config.setFilePath(file.path)
        config.sectionLines = 1..3
        val cmd = MatlabCommandLineState.createCommandLine(file.parent, config)
        TestCase.assertEquals("-batch", cmd.parametersList.list[0])
        TestCase.assertTrue(cmd.parametersList.list[1], cmd.parametersList.list[1].matches(Regex("addpath\\('.*'\\); section_\\w+; .*")))
    }

    fun testGutterIcons() {
        val file = myFixture.configureByText("analysis.m", script)
        val contributor = MatlabRunLineMarkerContributor()
        val withIcon = PsiTreeUtil.collectElements(file) { contributor.getInfo(it) != null }.map { it.text }
        TestCase.assertEquals(listOf("%% Setup", "%% Plot"), withIcon)

        val fnFile = myFixture.configureByText("tool.m", "function tool()\nhelper();\nend\nfunction helper()\nend\n")
        val fnIcons = PsiTreeUtil.collectElements(fnFile) { contributor.getInfo(it) != null }
        TestCase.assertEquals(1, fnIcons.size)  // only the main function
    }
}
