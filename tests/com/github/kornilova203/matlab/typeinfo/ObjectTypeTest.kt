package com.github.kornilova203.matlab.typeinfo

import com.github.kornilova203.matlab.psi.MatlabTypedExpr
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ObjectTypeTest : BasePlatformTestCase() {
    private val code = """
        classdef Shape
            methods
                function obj = Shape(w, h)
                    obj.W = w;
                end
                function a = area(obj, scale)
                    a = obj;
                end
            end
            methods (Static)
                function s = unit(n)
                    s = n;
                end
            end
        end
        """.trimIndent()

    /** Type of the declaration named [name] at its [occurrence]-th appearance in the code. */
    private fun typeAt(name: String, occurrence: Int): String {
        val file = myFixture.configureByText("Shape.m", code)
        var offset = -1
        repeat(occurrence) { offset = code.indexOf(name, offset + 1) }
        val element = file.findElementAt(offset)!!
        val typed = generateSequence(element) { it.parent }.filterIsInstance<MatlabTypedExpr>().first()
        return typed.getType().getName()
    }

    fun testMethodObjectParameter() = assertEquals("Shape", typeAt("obj, scale", 1))
    fun testConstructorOutput() = assertEquals("Shape", typeAt("obj = Shape", 1))
    fun testConstructorInputIsNotTheClass() = assertEquals("unknown", typeAt("w, h", 1))
    fun testOtherParameterIsUnknown() = assertEquals("unknown", typeAt("scale", 1))
    fun testStaticMethodParameterIsUnknown() = assertEquals("unknown", typeAt("n)", 1))
}
