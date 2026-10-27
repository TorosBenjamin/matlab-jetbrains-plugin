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

    private val shape = """
        classdef Shape < handle
            properties
                Width = 1
            end
            methods
                function obj = Shape(w, h)
                    obj.Width = w;
                end
                function a = area(obj, scale)
                    a = obj.Width * scale;
                    obj.resize(2);
                end
            end
            methods (Static)
                function s = unit(n)
                    s = Shape(n, n);
                end
            end
        end
        """.trimIndent()

    fun testFields() = assertEquals(listOf("Width", "Width"), coloredWith(shape, MatlabAnnotator.FIELD))

    fun testMethodCalls() = assertEquals(listOf("resize"), coloredWith(shape, MatlabAnnotator.METHOD_CALL))

    fun testParameters() = assertEquals(
        listOf("w", "h", "w", "scale", "scale", "n", "n", "n"),
        coloredWith(shape, MatlabAnnotator.PARAMETER)
    )

    fun testObjectReference() = assertEquals(
        listOf("obj", "obj", "obj", "obj", "obj"),
        coloredWith(shape, MatlabAnnotator.OBJECT_REFERENCE)
    )
}
