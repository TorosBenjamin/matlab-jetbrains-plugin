package com.github.kornilova203.matlab.structure

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BreadcrumbsTest : BasePlatformTestCase() {
    fun testCrumbs() {
        val file: PsiFile = myFixture.configureByText("Shape.m", """
            classdef Shape
                methods
                    function a = area(obj, scale)
                        if scale > 1
                            a = <caret>scale;
                        end
                    end
                end
            end
            """.trimIndent())
        val provider = MatlabBreadcrumbsProvider()
        val crumbs = generateSequence(file.findElementAt(myFixture.caretOffset)) { it.parent }
            .filter { provider.acceptElement(it) }.map { provider.getElementInfo(it) }.toList().reversed()
        assertEquals(listOf("Shape", "methods", "area(obj, scale)", "if scale > 1"), crumbs)
    }
}
