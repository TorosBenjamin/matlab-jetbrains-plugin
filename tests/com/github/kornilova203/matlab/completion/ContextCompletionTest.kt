package com.github.kornilova203.matlab.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ContextCompletionTest : BasePlatformTestCase() {
    private fun variants(code: String): List<String> {
        myFixture.configureByText("test.m", code)
        myFixture.complete(CompletionType.BASIC)
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun classWith(body: String) = "classdef Shape\n$body\nend\n"

    fun testNoSuggestionsForNewParameter() = assertEmpty(variants("function Y = foo(a, <caret>)\nY = 0;\nend\n"))
    fun testNoSuggestionsForReturnValue() = assertEmpty(variants("function [Y, <caret>] = foo(a)\nend\n"))
    fun testNoSuggestionsForPropertyName() = assertEmpty(variants(classWith("properties\n    Wi<caret>\nend")))

    fun testPropertyAttributes() {
        val v = variants(classWith("properties (<caret>)\n    W\nend"))
        assertContainsElements(v, "SetAccess", "Constant", "Dependent", "Hidden")
        assertDoesntContain(v, "Static", "disp", "zeros")
    }

    fun testMethodAttributes() {
        val v = variants(classWith("methods (<caret>)\nend"))
        assertContainsElements(v, "Static", "Access", "Test")
        assertDoesntContain(v, "Constant", "disp")
    }

    fun testAccessValues() = assertSameElements(
        variants(classWith("properties (SetAccess = <caret>)\n    W\nend")), "public", "protected", "private", "immutable")

    fun testBooleanValues() = assertSameElements(
        variants(classWith("properties (Constant = <caret>)\n    W = 1\nend")), "true", "false")

    fun testTypes() {
        myFixture.addFileToProject("Point.m", "classdef Point\nend\n")
        val v = variants(classWith("properties\n    Width (1,1) <caret>\nend"))
        assertContainsElements(v, "double", "string", "logical", "Point")
        assertDoesntContain(v, "disp", "zeros")
    }

    fun testValidators() {
        val v = variants(classWith("properties\n    Width double {mustBe<caret>}\nend"))
        assertContainsElements(v, "mustBePositive", "mustBeMember")
        assertDoesntContain(v, "disp")
    }

    fun testArgumentNames() = assertSameElements(
        variants("function f(alpha, beta)\narguments\n    <caret>\nend\nend\n"), "alpha", "beta")

    fun testNormalCodeStillCompletes() =
        assertContainsElements(variants("value = 1;\nx = val<caret>\n"), "value")

    fun testClassBodyOffersOnlyBlocks() {
        val v = variants("classdef A\n    methods\n    end\n    <caret>\n    methods\n    end\nend\n")
        assertSameElements(v, "properties", "methods", "events", "enumeration", "end")
    }

    fun testStructFields() {
        val v = variants("s.name = 'x';\ns.age = 3;\nt = s.size;\nu = s.<caret>\n")
        assertContainsElements(v, "name", "age", "size")
    }

    fun testStructFunctionFields() =
        assertContainsElements(variants("cfg = struct('Path', 'a', 'Depth', 2);\ncfg.<caret>\n"), "Path", "Depth")

    fun testNestedStructFields() =
        assertSameElements(variants("s.inner.a = 1;\ns.inner.b = 2;\ns.other = 3;\ns.inner.<caret>\n"), "a", "b")
}
