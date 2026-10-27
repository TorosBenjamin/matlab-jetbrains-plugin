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

    fun testFields() = assertEquals(listOf("Width", "Width", "Width"), coloredWith(shape, MatlabAnnotator.FIELD))

    fun testMethodCalls() = assertEquals(listOf("resize"), coloredWith(shape, MatlabAnnotator.METHOD_CALL))

    fun testParameters() = assertEquals(
        listOf("w", "h", "w", "scale", "scale", "n", "n", "n"),
        coloredWith(shape, MatlabAnnotator.PARAMETER)
    )

    fun testObjectReference() = assertEquals(
        listOf("obj", "obj", "obj", "obj", "obj"),
        coloredWith(shape, MatlabAnnotator.OBJECT_REFERENCE)
    )

    private val classBody = """
        classdef Shape < handle & matlab.mixin.Copyable
            properties
                Width (1,1) double = 1
            end
            properties (SetAccess = private, ~Hidden)
                Kind string = "rect"
            end
            properties (Constant)
                Version = 2
            end
            events
                Resized
            end
            methods
                function a = area(obj)
                    a = obj.Width * obj.Version;
                    obj.describe;
                    k = obj.Kind;
                end
                function describe(obj)
                end
            end
        end
        """.trimIndent()

    fun testPropertyDeclarationsAndUses() = assertEquals(
        listOf("Width", "Kind", "Width", "Kind"), coloredWith(classBody, MatlabAnnotator.FIELD))

    fun testConstantProperty() = assertEquals(
        listOf("Version", "Version"), coloredWith(classBody, MatlabAnnotator.CONSTANT))

    fun testTypes() = assertEquals(
        listOf("handle", "matlab", "mixin", "Copyable", "double", "string"), coloredWith(classBody, MatlabAnnotator.TYPE))

    fun testAttributes() = assertEquals(
        listOf("SetAccess", "Hidden", "Constant"), coloredWith(classBody, MatlabAnnotator.ATTRIBUTE))

    fun testAttributeValues() = assertEquals(listOf("private"), coloredWith(classBody, MatlabAnnotator.ATTRIBUTE_VALUE))

    fun testEvents() = assertEquals(listOf("Resized"), coloredWith(classBody, MatlabAnnotator.EVENT))

    /** obj.describe without parentheses is a method call, found by looking up describe in the class. */
    fun testMethodCalledWithoutParentheses() = assertEquals(
        listOf("describe"), coloredWith(classBody, MatlabAnnotator.METHOD_CALL))

    /** `end` inside indexing is parsed as a name but is still the keyword. A field named end is not. */
    fun testEndAsIndexIsKeyword() = assertEquals(
        listOf("end", "end", "end"),
        coloredWith("z = 1:10;\nlast = z(end);\nmid = z(end/2:end-1);\ns.end = 1;\n", MatlabAnnotator.KEYWORD)
            .take(3)
    )

    fun testEndAsIndexIsNotIdentifier() = assertEquals(
        emptyList<String>(),
        coloredWith("z = 1:10;\nlast = z(end);\n", MatlabAnnotator.ID).filter { it == "end" }
    )
}
