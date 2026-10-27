package com.github.kornilova203.matlab.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AnnotatorTest : BasePlatformTestCase() {
    /** Text ranges colored with [key] by the annotator. */
    private fun coloredWith(code: String, key: TextAttributesKey): List<String> {
        myFixture.configureByText("test.m", code)
        return myFixture.doHighlighting()
            .filter { it.forcedTextAttributesKey == key }
            .sortedBy { it.startOffset }
            .map { code.substring(it.startOffset, it.endOffset) }
    }

    fun testCommandArguments() = assertEquals(
        listOf("on", "../lib/common"),
        coloredWith("hold on\naddpath ../lib/common\nx = 1;\n", MatlabAnnotator.COMMAND_ARGUMENT)
    )

    fun testNewKeywords() = assertEquals(
        listOf("arguments", "persistent"),
        coloredWith("function f(a)\narguments\n  a double\nend\npersistent n\nend\n", MatlabAnnotator.KEYWORD)
    )

    fun testCommandWordIsNotIdentifier() = assertEquals(
        listOf("hold", "x"),
        coloredWith("hold on\nx = 1;\n", MatlabAnnotator.ID)
    )
}
