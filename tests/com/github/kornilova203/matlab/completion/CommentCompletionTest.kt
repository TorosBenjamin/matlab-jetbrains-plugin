package com.github.kornilova203.matlab.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThreeState

class CommentCompletionTest : BasePlatformTestCase() {
    private fun skipsPopupAtCaret(code: String): ThreeState {
        myFixture.configureByText("main.m", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!
        return MatlabCompletionConfidence().shouldSkipAutopopup(element, myFixture.file, myFixture.caretOffset)
    }

    fun testNoPopupInComment() = assertEquals(ThreeState.YES, skipsPopupAtCaret("x = 1;\n% Align the plo<caret>\n"))
    fun testNoPopupInString() = assertEquals(ThreeState.YES, skipsPopupAtCaret("disp('plo<caret>')\n"))
    fun testPopupInCode() = assertEquals(ThreeState.UNSURE, skipsPopupAtCaret("plo<caret>\n"))

    fun testNoNamesInComment() {
        myFixture.addFileToProject("plotThings.m", "function plotThings()\nend\n")
        myFixture.configureByText("main.m", "% plotT<caret>\n")
        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings.orEmpty())
    }

    fun testNamesInCode() {
        myFixture.addFileToProject("plotThings.m", "function plotThings()\nend\n")
        myFixture.configureByText("main.m", "x = 1;\nplotTh<caret>\n")
        myFixture.completeBasic()
        // The only match is inserted right away
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("plotThings"))
    }
}
