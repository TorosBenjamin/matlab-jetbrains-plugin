package com.github.kornilova203.matlab.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BuiltinClassCompletionTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            MatlabClassMembers.clearForTests()
        } finally {
            super.tearDown()
        }
    }

    private fun complete(code: String): List<String> {
        myFixture.configureByText("main.m", code)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    fun testFallbackWithoutMatlab() {
        val names = complete("p = inputParser;\np.<caret>\n")
        assertTrue(names.toString(), names.containsAll(listOf("addRequired", "addParameter", "parse", "Results", "KeepUnmatched")))
    }

    fun testWhatMatlabAnswered() {
        MatlabClassMembers.putForTests("containers.Map", MatlabClassInfo("containers.Map", listOf("Count", "KeyType"), listOf("isKey", "keys")))
        val names = complete("m = containers.Map('KeyType', 'char', 'ValueType', 'any');\nm.<caret>\n")
        assertSameElements(names, "Count", "KeyType", "isKey", "keys")
    }

    fun testCatchVariable() {
        val names = complete("try\n    x = 1;\ncatch ME\n    disp(ME.<caret>)\nend\n")
        assertTrue(names.toString(), names.containsAll(listOf("message", "identifier", "getReport")))
    }

    fun testFactory() = assertEquals("matlab.ui.Figure", MatlabBuiltinClasses.classMadeBy("figure('Name', 'x')"))

    fun testNotForProjectClasses() {
        myFixture.addFileToProject("inputParser.m", "classdef inputParser\n    properties\n        Mine\n    end\nend\n")
        val names = complete("p = inputParser;\np.<caret>\n")
        assertFalse(names.toString(), names.contains("addRequired"))
    }

    fun testParse() {
        val parsed = MatlabClassMembers.parse("c\tinputParser\np\tResults\nm\tparse\nm\taddRequired\nc\tzeros\nx\t\n")
        assertEquals(MatlabClassInfo("inputParser", listOf("Results"), listOf("addRequired", "parse")), parsed["inputParser"])
        assertTrue(parsed.containsKey("zeros"))
        assertNull(parsed["zeros"])
    }
}
