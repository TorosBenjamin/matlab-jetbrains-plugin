package com.github.kornilova203.matlab.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlockKeywordHighlightingTest : BasePlatformTestCase() {
    /** Returns the highlighted keyword texts for the caret position in [code]. */
    private fun highlighted(code: String): List<String> {
        myFixture.configureByText("test.m", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return emptyList()
        val text = myFixture.file.text
        return MatlabBlockKeywordHighlighterFactory.findBlockKeywords(element).map { it.substring(text) }
    }

    private val ifBlock = "if a\n  x = 1;\nelseif b\n  x = 2;\nelse\n  x = 3;\nend\n"

    fun testIfFromIf() = assertEquals(listOf("if", "elseif", "else", "end"), highlighted("<caret>$ifBlock"))

    fun testIfFromElse() = assertEquals(listOf("if", "elseif", "else", "end"),
        highlighted(ifBlock.replace("else\n", "<caret>else\n")))

    fun testIfFromEnd() = assertEquals(listOf("if", "elseif", "else", "end"),
        highlighted(ifBlock.replace("end\n", "<caret>end\n")))

    fun testSwitch() = assertEquals(listOf("switch", "case", "case", "otherwise", "end"),
        highlighted("<caret>switch x\n  case 1\n    a = 1;\n  case 2\n    a = 2;\n  otherwise\n    a = 3;\nend\n"))

    fun testTryCatch() = assertEquals(listOf("try", "catch", "end"),
        highlighted("try\n  x = 1;\n<caret>catch e\n  x = 2;\nend\n"))

    fun testForLoop() = assertEquals(listOf("for", "end"), highlighted("<caret>for i = 1:3\n  x = i;\nend\n"))

    fun testFunction() = assertEquals(listOf("function", "end"),
        highlighted("function r = f(x)\n  r = x;\n<caret>end\n"))

    fun testNothingOnIdentifier() = assertEquals(emptyList<String>(), highlighted("for i = 1:3\n  <caret>x = i;\nend\n"))
}
