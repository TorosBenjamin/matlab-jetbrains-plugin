package com.github.kornilova203.matlab.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ObjectMemberCompletionTest : BasePlatformTestCase() {
    fun testMembersOfObjectVariable() {
        myFixture.configureByText("Shape.m", """
            classdef Shape
                properties
                    Width = 1
                end
                properties (Constant)
                    Version = 2
                end
                methods
                    function a = area(obj)
                        a = obj.<caret>
                    end
                    function describe(obj)
                    end
                end
            end
            """.trimIndent())
        myFixture.complete(CompletionType.BASIC)
        assertContainsElements(myFixture.lookupElementStrings!!, "Width", "Version", "area", "describe")
    }
}
