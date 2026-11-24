package com.github.kornilova203.matlab.editor.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EnterInCommentTest : BasePlatformTestCase() {
    private fun enter(before: String, after: String) {
        myFixture.configureByText("main.m", before)
        myFixture.type('\n')
        myFixture.checkResult(after)
    }

    fun testContinuesABlockOfComments() = enter(
        "% Aligns the child\n% to its parent<caret>\nx = 1;\n",
        "% Aligns the child\n% to its parent\n% <caret>\nx = 1;\n")

    fun testKeepsTheIndent() = enter(
        "function f()\n    % first\n    % second<caret>\nend\n",
        "function f()\n    % first\n    % second\n    % <caret>\nend\n")

    fun testSplitsAComment() = enter(
        "% Aligns the <caret>child\n",
        "% Aligns the \n% <caret>child\n")

    fun testAfterSectionTitle() = enter(
        "%% Setup\n% Loads the data<caret>\n",
        "%% Setup\n% Loads the data\n% <caret>\n")

    fun testSingleCommentAtItsEndIsLeft() {
        myFixture.configureByText("main.m", "x = 1;\n% one line<caret>\n")
        myFixture.type('\n')
        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.contains("% one line\n%"))
    }

    fun testEmptyCommentLineEndsTheBlock() {
        myFixture.configureByText("main.m", "% first\n%<caret>\n")
        myFixture.type('\n')
        assertEquals("% first\n%\n", myFixture.editor.document.text.lines().take(2).joinToString("\n") + "\n")
        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.lines().getOrNull(2)?.startsWith("%") == true)
    }

    fun testNotAfterCode() {
        myFixture.configureByText("main.m", "% note\nx = 1; % value<caret>\n")
        myFixture.type('\n')
        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.lines()[2].startsWith("%"))
    }

    fun testNotInSectionTitle() {
        myFixture.configureByText("main.m", "% note\n%% Setup<caret>\n")
        myFixture.type('\n')
        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.lines()[2].trimStart().startsWith("%"))
    }
}
