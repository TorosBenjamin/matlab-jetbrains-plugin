package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.execution.console.*
import com.github.kornilova203.matlab.inspections.MatlabUnusedVariableInspection
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class MatlabWorkspaceTest : BasePlatformTestCase() {
    private val hidden = ArrayList<Pair<String, String>>()
    private val prompts = ArrayList<String>()
    private val filter = MatlabOutputFilter({ tag, text -> hidden += tag to text }, { prompts += it })

    fun testPromptIsTakenOut() {
        assertEquals("x =\n     1\n", filter.feed("x =\n     1\n>> "))
        assertEquals(listOf(">> "), prompts)
        assertEmpty(hidden)
    }

    fun testAnswerIsHiddenWithItsPrompt() {
        assertEquals("", filter.feed("\u0002ws\na\t1x1\tdouble\t1\n\u0004>> "))
        assertEquals(listOf("ws" to "a\t1x1\tdouble\t1\n"), hidden)
        assertEmpty(prompts)
        assertEquals("ans = 2\n", filter.feed("ans = 2\n>> "))
        assertEquals(listOf(">> "), prompts)
    }

    fun testAnswerInPieces() {
        assertEquals("out\n", filter.feed("out\n\u0002ws\na\t1x"))
        assertEquals("", filter.feed("1\tdouble\t"))
        assertEquals("", filter.feed("\n\u0004"))
        assertEquals("", filter.feed(">> "))
        assertEquals(listOf("ws" to "a\t1x1\tdouble\t\n"), hidden)
        assertEmpty(prompts)
        assertEquals("b\n", filter.feed("b\n"))
    }

    fun testPromptInPieces() {
        assertEquals("done\n", filter.feed("done\n>"))
        assertEmpty(prompts)
        assertEquals("", filter.feed("> "))
        assertEquals(listOf(">> "), prompts)
    }

    fun testDebuggerPrompt() {
        assertEquals("5   x = 1;\n", filter.feed("5   x = 1;\nK>> "))
        assertEquals(listOf("K>> "), prompts)
    }

    fun testPromptAfterOutputOnTheSameLine() {
        assertEquals("abc\n", filter.feed("abc>> "))
        assertEquals(listOf(">> "), prompts)
    }

    fun testOtherAnglesStay() {
        assertEquals("a >> b\n>x\n", filter.feed("a >> b\n>x\n"))
        assertEmpty(prompts)
    }

    fun testParseMembers() = assertEquals(
        listOf(MatlabMember("a", MatlabMember.Kind.FIELD), MatlabMember("Name", MatlabMember.Kind.PROPERTY),
            MatlabMember("disp", MatlabMember.Kind.METHOD)),
        MatlabMember.parse("f\ta\np\tName\r\nm\tdisp\nm\tName\nx\tjunk\n"))

    fun testParse() {
        val variables = MatlabWorkspace.parse("zeta\t3x4\tdouble\t\nalpha\t1x1\tdouble\t5\r\n\nname\t1x5\tchar\t'hello'\n")
        assertEquals(listOf(
            MatlabVariable("alpha", "1x1", "double", "5"),
            MatlabVariable("name", "1x5", "char", "'hello'"),
            MatlabVariable("zeta", "3x4", "double", "")), variables)
    }

    fun testQueryWritesTheFunction() {
        val query = MatlabConsoleCode.workspaceQuery()
        val dir = Regex("addpath\\('(.*?)'\\)").find(query)!!.groupValues[1].replace("''", "'")
        val function = File(dir, "ide_workspace__.m").readText()
        assertTrue(function.startsWith("function ide_workspace__()"))
        assertTrue(function.contains("fprintf('%sws\\n%s%s', char(2), out, char(4));"))
        val members = MatlabConsoleCode.membersQuery(3, "s.a")
        assertTrue(members, members.contains("ide_members__('3', 's.a')"))
        assertTrue(File(dir, "ide_members__.m").readText().startsWith("function ide_members__(id, expression)"))
    }

    private fun consoleFile(text: String, vararg variables: MatlabVariable): MatlabWorkspace {
        myFixture.configureByText("console.m", text)
        val workspace = MatlabWorkspace().apply { update(variables.toList()) }
        myFixture.file.virtualFile.putUserData(MatlabWorkspace.KEY, workspace)
        return workspace
    }

    fun testCompletesWorkspaceVariables() {
        consoleFile("y = my<caret>", MatlabVariable("myData", "3x3", "double", ""), MatlabVariable("myName", "1x3", "char", "'abc'"),
            MatlabVariable("other", "1x1", "double", "1"))
        myFixture.completeBasic()
        val names = myFixture.lookupElementStrings!!
        assertTrue(names.toString(), names.containsAll(listOf("myData", "myName")))
        assertFalse(names.contains("other"))
    }

    fun testCompletesMembersAfterDot() {
        val workspace = consoleFile("y = s.<caret>", MatlabVariable("s", "1x1", "struct", ""))
        val asked = ArrayList<String>()
        workspace.members = { asked += it; listOf(MatlabMember("alpha", MatlabMember.Kind.FIELD), MatlabMember("beta", MatlabMember.Kind.FIELD)) }
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings.toString(), myFixture.lookupElementStrings!!.containsAll(listOf("alpha", "beta")))
        assertEquals(listOf("s"), asked)
    }

    fun testNoMembersOfFunctionCalls() {
        val workspace = consoleFile("y = f(x).<caret>", MatlabVariable("x", "1x1", "double", "1"))
        val asked = ArrayList<String>()
        workspace.members = { asked += it; emptyList() }
        myFixture.completeBasic()
        assertEmpty(asked)
    }

    fun testNoVariablesAfterDot() {
        consoleFile("y = s.my<caret>", MatlabVariable("myData", "3x3", "double", ""), MatlabVariable("myName", "1x3", "char", ""))
        myFixture.completeBasic()
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("myData"))
    }

    fun testNoUnusedWarningsInConsole() {
        myFixture.enableInspections(MatlabUnusedVariableInspection())
        consoleFile("x = 1;")
        assertEmpty(myFixture.doHighlighting().filter { it.description?.contains("unused", ignoreCase = true) == true })
    }

    /** A pretend MATLAB: prompts, answers the workspace query, and echoes other commands. */
    fun testSessionListsWorkspace() {
        if (SystemInfo.isWindows) return
        val dir = Files.createTempDirectory("fake-matlab").toFile()
        val matlab = File(dir, "matlab")
        matlab.writeText("""
            #!/bin/sh
            printf '>> '
            while IFS= read -r line; do
              case "${'$'}line" in
                try,*ide_workspace__*) printf '\002ws\nx\0111x1\011double\0115\n\004>> ' ;;
                try,*ide_members__*)
                  id=${'$'}(printf '%s' "${'$'}line" | sed -n "s/.*ide_members__('\([0-9]*\)'.*/\1/p")
                  printf '\002m%s\nf\011field1\nm\011disp\n\004>> ' "${'$'}id" ;;
                exit) exit 0 ;;
                *) printf 'ran %s\n>> ' "${'$'}line" ;;
              esac
            done
            """.trimIndent() + "\n")
        matlab.setExecutable(true)
        val session = MatlabConsoleSession(project, "Test", matlab.path, null)
        Disposer.register(testRootDisposable) { session.processHandler.destroyProcess(); Disposer.dispose(session) }
        val deadline = System.currentTimeMillis() + 10_000
        while (session.workspace.variables.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(listOf(MatlabVariable("x", "1x1", "double", "5")), session.workspace.variables)
        assertEquals(listOf(MatlabMember("field1", MatlabMember.Kind.FIELD), MatlabMember("disp", MatlabMember.Kind.METHOD)),
            session.members("x"))
        // Listed again after a command
        var updates = 0
        session.workspace.addListener { updates++ }
        session.send("a = 1")
        val again = System.currentTimeMillis() + 10_000
        while (updates == 0 && System.currentTimeMillis() < again) Thread.sleep(50)
        assertEquals(1, updates)
    }
}
