package com.github.kornilova203.matlab.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ParameterInfoTest : BasePlatformTestCase() {
    /** The parameter info text at the caret, with the current parameter in [brackets]; null if none is shown. */
    private fun info(code: String): String? {
        myFixture.configureByText("main.m", code)
        val offset = myFixture.caretOffset
        val arguments = MatlabParameterInfoHandler.argumentsAt(myFixture.file, offset) ?: return null
        val signature = MatlabParameterInfoHandler.signatureOf(arguments) ?: return null
        val p = signature.presentation(MatlabParameterInfoHandler.currentArgument(arguments, offset))
        if (p.highlightStart < 0) return p.text
        return p.text.substring(0, p.highlightStart) + "[" + p.text.substring(p.highlightStart, p.highlightEnd) + "]" +
                p.text.substring(p.highlightEnd)
    }

    private val scale = """
        function r = scale(x, k, options)
        arguments
            x double
            k (1,1) double = 2
            options.Mode string = "fast"
        end
        r = x * k;
        end
        """.trimIndent()

    fun testLocalFunction() = assertEquals("a, [b]", info("x = add(1, <caret>2);\n\nfunction r = add(a, b)\nr = a + b;\nend\n"))

    fun testFirstArgument() = assertEquals("[a], b", info("x = add(<caret>1, 2);\n\nfunction r = add(a, b)\nr = a + b;\nend\n"))

    fun testArgumentsBlock() = assertEquals("x double, [k (1,1) double = 2], Name=Value: Mode string = \"fast\"",
        info("y = scale(1, <caret>3);\n\n$scale\n"))

    fun testNameValueArguments() = assertEquals("x double, k (1,1) double = 2, [Name=Value: Mode string = \"fast\"]",
        info("y = scale(1, 2, <caret>'Mode', \"slow\");\n\n$scale\n"))

    fun testVarargin() = assertEquals("a, [varargin]", info("f(1, 2, <caret>3);\n\nfunction f(a, varargin)\nend\n"))

    fun testNoParameters() = assertEquals("<no parameters>", info("t = stamp(<caret>);\n\nfunction t = stamp()\nt = 1;\nend\n"))

    fun testMethodSkipsObject() {
        addShape()
        assertEquals("[factor]", info("s = Shape(1);\ns = s.resize(<caret>2);\n"))
    }

    fun testConstructor() {
        addShape()
        assertEquals("[w]", info("s = Shape(<caret>1);\n"))
    }

    fun testIndexingIsNotACall() = assertNull(info("v = [1 2 3];\nx = v(<caret>1);\n"))

    private fun addShape() {
        myFixture.addFileToProject("Shape.m", """
            classdef Shape
                properties
                    W
                end
                methods
                    function obj = Shape(w)
                        obj.W = w;
                    end
                    function obj = resize(obj, factor)
                        obj.W = obj.W * factor;
                    end
                end
            end
            """.trimIndent())
    }
}
