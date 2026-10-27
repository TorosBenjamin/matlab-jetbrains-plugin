package com.github.kornilova203.matlab.editor.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EndInsertionTest : BasePlatformTestCase() {
    private fun enter(before: String): String {
        myFixture.configureByText("test.m", before)
        myFixture.type('\n')
        return myFixture.editor.document.text.lines().joinToString("\n") { it.trimEnd() }
    }

    fun testIf() = assertEquals("x = 1;\nif x\n\nend", enter("x = 1;\nif x<caret>"))

    fun testFor() = assertEquals("for i = 1:3\n\nend", enter("for i = 1:3<caret>"))

    fun testKeepsIndent() = assertEquals(
        "function f\n    while true\n\n    end\nend",
        enter("function f\n    while true<caret>\nend"))

    fun testAlreadyClosed() = assertEquals("if x\n\n    y = 1;\nend", enter("if x<caret>\n    y = 1;\nend"))

    fun testOneLineBlockIsClosed() = assertEquals("if x, y = 1; end\n", enter("if x, y = 1; end<caret>"))

    fun testClassBlocks() = assertEquals(
        "classdef A\n    properties\n\n    end\nend",
        enter("classdef A\n    properties<caret>\nend"))

    fun testFunctionInFileWithoutEnds() = assertEquals(
        "function a\nx = 1;\n\nfunction b\n",
        enter("function a\nx = 1;\n\nfunction b<caret>"))

    fun testFunctionInScript() = assertEquals(
        "x = 1;\nfunction b\n\nend",
        enter("x = 1;\nfunction b<caret>"))

    fun testIndexEndDoesNotCount() = assertEquals(
        "y = x(end);\nif y\n\nend",
        enter("y = x(end);\nif y<caret>"))

    fun testNotAtEndOfLine() = assertFalse(enter("if x<caret> y").contains("end"))
}
