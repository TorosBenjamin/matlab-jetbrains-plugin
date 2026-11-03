package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.execution.console.MatlabConsoleCode
import com.github.kornilova203.matlab.execution.console.MatlabConsoleSession
import com.github.kornilova203.matlab.execution.console.MatlabOutputFilter
import com.github.kornilova203.matlab.execution.debugger.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import java.io.File
import java.nio.file.Files

class MatlabDebuggerTest : BasePlatformTestCase() {
    fun testParseFrames() = assertEquals(
        listOf(MatlabFrame("/w/inner.m", "inner", 4), MatlabFrame("/w/main.m", "main", 10)),
        MatlabFrame.parse("/w/inner.m\tinner\t4\n/w/main.m\tmain\t10\r\n/opt/matlab/toolbox/matlab/lang/run.m\trun\t91\nbad\n"))

    fun testSpecLine() {
        assertEquals("/w/a.m\t3\t\n", MatlabBreakpointSpec("/w/a.m", 3, null).toLine())
        assertEquals("/w/a.m\t3\tx > 1 && y\n", MatlabBreakpointSpec("/w/a.m", 3, "x > 1\n&& y").toLine())
    }

    fun testIsCode() {
        assertTrue(MatlabLineBreakpointType.isCode("  x = 1;"))
        assertFalse(MatlabLineBreakpointType.isCode("   "))
        assertFalse(MatlabLineBreakpointType.isCode("  % comment"))
    }

    fun testCanPutAt() {
        val file = myFixture.configureByText("f.m", "x = 1;\n% note\n\ny = 2;\n").virtualFile
        val type = MatlabLineBreakpointType()
        assertEquals(listOf(true, false, false, true), (0..3).map { type.canPutAt(file, it, project) })
    }

    fun testExpressionRange() {
        val text = "y = s.data(2) + if_x;"
        fun at(offset: Int) = MatlabEvaluator.expressionRange(text, offset)?.substring(text)
        assertEquals("y", at(0))
        assertEquals("s", at(4))
        assertEquals("s.data", at(7))
        assertEquals("if_x", at(17))
        assertNull(at(1))
        assertNull(at(11))
    }

    fun testDebugPrompts() {
        val prompts = ArrayList<String>()
        val filter = MatlabOutputFilter({ _, _ -> }, { prompts += it })
        assertEquals("5  x = 1;\n", filter.feed("5  x = 1;\nK>> "))
        assertEquals("", filter.feed("debug> "))
        assertEquals(listOf("K>> ", "debug> "), prompts)
        assertTrue(MatlabOutputFilter.isDebugPrompt("K>> "))
        assertFalse(MatlabOutputFilter.isDebugPrompt(">> "))
    }

    fun testHelpersAreWritten() {
        val query = MatlabConsoleCode.breakpointsQuery(listOf(MatlabBreakpointSpec("/w/a.m", 3, "x > 1")), true)
        assertTrue(query, query.contains("ide_breakpoints__('") && query.contains(", true)"))
        val list = Regex("ide_breakpoints__\\('(.*?)'").find(query)!!.groupValues[1].replace("''", "'")
        assertEquals("/w/a.m\t3\tx > 1\n", File(list).readText())
        val dir = File(list).parentFile
        for (name in listOf("ide_breakpoints__", "ide_stack__", "ide_eval__"))
            assertTrue(name, File(dir, "$name.m").readText().startsWith("function $name("))
        assertTrue(MatlabConsoleCode.evalQuery(4, "a'\nb").contains("ide_eval__('4', 'a'' b')"))
    }

    /** A pretend MATLAB that logs what it gets, stops at "go" and continues at "dbcont". */
    private fun fakeMatlab(log: File, script: File): File {
        val dir = Files.createTempDirectory("fake-matlab").toFile()
        val matlab = File(dir, "matlab")
        matlab.writeText("""
            #!/bin/sh
            printf '>> '
            while IFS= read -r line; do
              printf '%s\n' "${'$'}line" >> '${log.path}'
              case "${'$'}line" in
                try,*ide_workspace__*) printf '\002ws\n\004%s' "${'$'}{prompt:->> }" ;;
                try,*ide_breakpoints__*) printf '\002bp\n\004%s' "${'$'}{prompt:->> }" ;;
                try,*ide_stack__*) printf '\002st\n${script.path}\tmain\t2\n\004K>> ' ;;
                go) prompt='K>> '; printf '2   y = 2;\nK>> ' ;;
                dbcont) prompt='>> '; printf '>> ' ;;
                exit) exit 0 ;;
                *) printf '%s' "${'$'}{prompt:->> }" ;;
              esac
            done
            """.trimIndent() + "\n")
        matlab.setExecutable(true)
        return matlab
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(20)
        }
        assertTrue(what, condition())
    }

    fun testBreakpointsReachMatlabAndDebuggerFollowsIt() {
        if (SystemInfo.isWindows) return
        val log = Files.createTempFile("matlab-log", ".txt").toFile()
        // On disk: the debugger finds the stopped line's file there
        val scriptFile = File(Files.createTempDirectory("matlab-debug").toFile(), "main.m").apply { writeText("x = 1;\ny = 2;\n") }
        val script = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scriptFile)!!
        val session = MatlabConsoleSession(project, "Test", fakeMatlab(log, scriptFile).path, null)
        Disposer.register(testRootDisposable) { session.processHandler.destroyProcess(); Disposer.dispose(session) }
        waitUntil("MATLAB started") { session.isIdle }

        // A new breakpoint goes to MATLAB right away
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        val type = XDebuggerUtil.getInstance().findBreakpointType(MatlabLineBreakpointType::class.java)
        val breakpoint = WriteAction.computeAndWait<com.intellij.xdebugger.breakpoints.XLineBreakpoint<*>, RuntimeException> {
            manager.addLineBreakpoint(type, script.url, 1, null)
        }
        Disposer.register(testRootDisposable) { WriteAction.runAndWait<RuntimeException> { manager.removeBreakpoint(breakpoint) } }
        waitUntil("breakpoints sent") { log.readText().lines().count { it.contains("ide_breakpoints__") } >= 2 }
        val list = Regex("ide_breakpoints__\\('(.*?)'").findAll(log.readText()).last().groupValues[1]
        assertEquals("${scriptFile.path}\t2\t\n", File(list).readText())

        // MATLAB stops: the debugger gets where (the Debug tool window itself isn't opened in tests)
        val events = java.util.Collections.synchronizedList(ArrayList<String>())
        MatlabDebugger.testEvents = events
        Disposer.register(testRootDisposable) { MatlabDebugger.testEvents = null }
        session.send("go")
        waitUntil("stopped") { session.isInDebugger && events.isNotEmpty() }
        assertEquals("stopped main:2", events.first())

        // Resume continues MATLAB, which leaves the debugger
        session.debugCommand("dbcont")
        waitUntil("dbcont sent") { log.readText().lines().contains("dbcont") }
        waitUntil("left the debugger") { !session.isInDebugger && events.contains("left") }
    }
}
