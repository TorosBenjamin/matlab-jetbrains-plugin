package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.github.kornilova203.matlab.settings.MatlabSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class MlintTest : BasePlatformTestCase() {
    fun testParseOutput() {
        val problems = MatlabMlintAnnotator.parse("""
            ========== /tmp/x/main.m ==========
            L 2 (C 1-5): NOPRT: Terminate statement with semicolon to suppress output (in scripts).
            L 7 (C 12): NASGU: The value assigned here to 'x' appears to be unused.
            L 9 (C 3-4): A message without an ID.
            """.trimIndent())
        assertEquals(listOf(
            MatlabMlintAnnotator.Problem(2, 1, 5, "NOPRT", "Terminate statement with semicolon to suppress output (in scripts)."),
            MatlabMlintAnnotator.Problem(7, 12, 12, "NASGU", "The value assigned here to 'x' appears to be unused."),
            MatlabMlintAnnotator.Problem(9, 3, 4, null, "A message without an ID."),
        ), problems)
    }

    /** A fake MATLAB installation whose mlint prints fixed warnings. */
    private fun fakeMatlab(vararg mlintOutput: String): File {
        val root = FileUtil.createTempDirectory("fake-matlab", null, true)
        File(root, "bin").mkdirs()
        File(root, "bin/matlab").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        File(root, "bin/glnxa64").mkdirs()
        File(root, "bin/glnxa64/mlint").apply {
            writeText("#!/bin/sh\n" + mlintOutput.joinToString("") { "echo '$it'\n" })
            setExecutable(true)
        }
        MatlabSettings.getInstance().matlabPath = File(root, "bin/matlab").path
        MatlabInstallations.clearMlintCacheForTests()
        return root
    }

    override fun tearDown() {
        try {
            MatlabSettings.getInstance().matlabPath = null
            MatlabSettings.getInstance().useMlint = true
            MatlabInstallations.clearMlintCacheForTests()
        } finally {
            super.tearDown()
        }
    }

    private fun mlintWarnings() = myFixture.doHighlighting(HighlightSeverity.WARNING)
        .filter { it.severity == HighlightSeverity.WARNING }
        .map { myFixture.file.text.substring(it.startOffset, it.endOffset) + " | " + it.description }

    fun testWarningsShownAndSuppressed() {
        if (SystemInfo.isWindows) return
        fakeMatlab("L 2 (C 1-5): NOPRT: Terminate statement with semicolon to suppress output (in scripts).")
        myFixture.configureByText("main.m", "a = 1;\n<caret>b = 2 % note\n")
        assertEquals(listOf("b = 2 | Terminate statement with semicolon to suppress output (in scripts)."), mlintWarnings())

        myFixture.launchAction(myFixture.findSingleIntention("Suppress 'NOPRT'"))
        assertEquals("a = 1;\nb = 2 %#ok<NOPRT> % note\n", myFixture.editor.document.text)
    }

    fun testSuppressAddsToExistingPragma() {
        if (SystemInfo.isWindows) return
        fakeMatlab("L 1 (C 1): NASGU: The value assigned here to 'x' appears to be unused.")
        myFixture.configureByText("main.m", "x = f() %#ok<NOPRT>\n")
        mlintWarnings()
        myFixture.launchAction(myFixture.findSingleIntention("Suppress 'NASGU'"))
        assertEquals("x = f() %#ok<NOPRT,NASGU>\n", myFixture.editor.document.text)
    }

    fun testCanBeTurnedOff() {
        if (SystemInfo.isWindows) return
        fakeMatlab("L 1 (C 1): NOPRT: Terminate statement with semicolon.")
        MatlabSettings.getInstance().useMlint = false
        myFixture.configureByText("main.m", "x = 1\n")
        assertEmpty(mlintWarnings())
    }

    fun testOctaveHasNoMlint() {
        MatlabSettings.getInstance().matlabPath = "/usr/bin/octave-cli"
        MatlabInstallations.clearMlintCacheForTests()
        assertNull(MatlabInstallations.findMlint())
    }
}
