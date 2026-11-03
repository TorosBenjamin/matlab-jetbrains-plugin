package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.execution.console.*
import com.github.kornilova203.matlab.settings.MatlabSettings
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.*

class MatlabConsoleTest : BasePlatformTestCase() {
    private var savedMatlabPath: String? = null

    override fun setUp() {
        super.setUp()
        savedMatlabPath = MatlabSettings.getInstance().matlabPath
    }

    override fun tearDown() {
        try {
            MatlabSettings.getInstance().matlabPath = savedMatlabPath
        } finally {
            super.tearDown()
        }
    }

    // What runs send

    fun testRunFile() = assertEquals("run('/work/o''brien/main.m')", MatlabConsoleCode.runFile("/work/o'brien/main.m"))

    fun testSingleLineRunsAsItIs() = assertEquals("x = 1 + 2;", MatlabConsoleCode.runCode("  x = 1 + 2;\n"))

    fun testSeveralLinesRunAsScript() {
        val command = MatlabConsoleCode.runCode("for i = 1:3\n    disp(i)\nend\n")
        val match = Regex("addpath\\('(.*)'\\); (ide_run_\\d+)").matchEntire(command)
        assertNotNull(command, match)
        val script = File(match!!.groupValues[1].replace("''", "'"), match.groupValues[2] + ".m")
        assertEquals("for i = 1:3\n    disp(i)\nend\n", script.readText())
    }

    fun testCallsClc() {
        for (code in listOf("clc", "clc;", "  clc ;clear", "clear; clc", "clc, close all", "clc()", "clc % clear", "x = 1;\nclc\n"))
            assertTrue(code, MatlabConsoleSession.callsClc(code))
        for (code in listOf("% clc", "disp('clc')", "s.clc", "clc = 3", "myclc", "clcx", "x = 1"))
            assertFalse(code, MatlabConsoleSession.callsClc(code))
    }

    // How each MATLAB is started

    fun testKinds() {
        assertEquals(MatlabConsoleSession.Kind.MATLAB, MatlabConsoleSession.Kind.of("/usr/local/MATLAB/R2025b/bin/matlab", windows = false))
        assertEquals(MatlabConsoleSession.Kind.MATLAB_ENGINE, MatlabConsoleSession.Kind.of("C:\\MATLAB\\R2025b\\bin\\matlab.exe", windows = true))
        assertEquals(MatlabConsoleSession.Kind.OCTAVE, MatlabConsoleSession.Kind.of("/usr/bin/octave-cli", windows = false))
        assertEquals(MatlabConsoleSession.Kind.OTHER, MatlabConsoleSession.Kind.of("/usr/bin/my-wrapper", windows = false))
    }

    fun testMatlabCommandLine() {
        val cmd = MatlabConsoleSession.commandLine(MatlabConsoleSession.Kind.MATLAB, "/opt/matlab/bin/matlab", "/work")
        assertEquals(listOf("/opt/matlab/bin/matlab", "-nodesktop", "-nosplash"), cmd.getCommandLineList(null))
        assertEquals("/work", cmd.workDirectory?.path?.replace('\\', '/'))
    }

    fun testOctaveCommandLine() {
        assertEquals(listOf("/usr/bin/octave-cli", "--interactive"),
            MatlabConsoleSession.commandLine(MatlabConsoleSession.Kind.OCTAVE, "/usr/bin/octave-cli", null).getCommandLineList(null))
        assertEquals(listOf("/usr/bin/octave", "--no-gui", "--interactive"),
            MatlabConsoleSession.commandLine(MatlabConsoleSession.Kind.OCTAVE, "/usr/bin/octave", null).getCommandLineList(null))
    }

    // The Windows bridge, with a stand-in for MATLAB's engine

    class FakeEngine {
        val commands = CopyOnWriteArrayList<String>()
        val started = CountDownLatch(1)
        @Volatile var closed = false

        fun evalAsync(command: String, output: Writer, error: Writer): Future<*> {
            commands += command
            return when (command) {
                "fail" -> CompletableFuture.failedFuture<Any>(RuntimeException("Undefined function 'fail'"))
                "long" -> CompletableFuture<Any>().also { started.countDown() }  // never finishes on its own
                else -> {
                    output.write("ran $command\n")
                    output.flush()
                    CompletableFuture.completedFuture(null)
                }
            }
        }

        fun close() {
            closed = true
        }
    }

    private fun bridge(engine: FakeEngine, input: Reader): Pair<ByteArrayOutputStream, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        MatlabEngineBridge(engine, input, PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8")).run()
        return out to err
    }

    fun testBridgeRunsCommands() {
        val engine = FakeEngine()
        val (out, err) = bridge(engine, StringReader("x = 1\nfail\n\ndisp(x)\nexit\nnot run\n"))
        assertEquals(listOf("x = 1", "fail", "disp(x)"), engine.commands)
        val text = out.toString(StandardCharsets.UTF_8)
        assertTrue(text, text.startsWith(">> ran x = 1\n>> "))
        assertTrue(text, text.contains("ran disp(x)"))
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Undefined function 'fail'"))
        assertTrue(engine.closed)
    }

    fun testBridgeInterrupts() {
        val engine = FakeEngine()
        val input = PipedWriter()
        val reader = PipedReader(input)
        val result = Executors.newSingleThreadExecutor().submit<Pair<ByteArrayOutputStream, ByteArrayOutputStream>> { bridge(engine, reader) }
        input.write("long\n")
        input.flush()
        assertTrue(engine.started.await(10, TimeUnit.SECONDS))
        input.write(MatlabEngineBridge.INTERRUPT + "\nafter\nexit\n")
        input.flush()
        val (out, err) = result.get(30, TimeUnit.SECONDS)
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Interrupted"))
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("ran after"))
        assertTrue(engine.closed)
    }

    // Live sessions, with `cat` standing in for MATLAB (it prints back what it's sent)

    private fun output(session: MatlabConsoleSession): StringBuffer {
        val text = StringBuffer()
        session.processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                text.append(event.text)
            }
        })
        return text
    }

    private fun waitFor(text: StringBuffer, expected: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!text.contains(expected) && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("'$expected' not in '$text'", text.contains(expected))
    }

    fun testSessionSendsCommands() {
        if (SystemInfo.isWindows || !File("/bin/cat").exists()) return
        val session = MatlabConsoleSession(project, "Test", "/bin/cat", null)
        Disposer.register(testRootDisposable) { session.processHandler.destroyProcess() }
        val text = output(session)
        session.send("disp('hello')")
        waitFor(text, "disp('hello')")
        // Typed input (as the console sends it) still reaches the process
        session.processHandler.processInput!!.apply { write("typed clc\n".toByteArray()); flush() }
        waitFor(text, "typed clc")
        assertTrue(session.isAlive)
    }

    fun testManagerReusesSession() {
        if (SystemInfo.isWindows || !File("/bin/cat").exists()) return
        MatlabSettings.getInstance().matlabPath = "/bin/cat"
        val manager = MatlabConsoleManager.getInstance(project)
        val first = manager.run("a = 1")!!
        Disposer.register(testRootDisposable) { first.processHandler.destroyProcess(); Disposer.dispose(first) }
        val text = output(first)
        assertSame(first, manager.run("b = 2"))
        waitFor(text, "b = 2")
        val second = manager.newSession()!!
        Disposer.register(testRootDisposable) { second.processHandler.destroyProcess(); Disposer.dispose(second) }
        assertNotSame(first, second)
    }
}
