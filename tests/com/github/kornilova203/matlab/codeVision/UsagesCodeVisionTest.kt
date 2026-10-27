package com.github.kornilova203.matlab.codeVision

import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabProperty
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UsagesCodeVisionTest : BasePlatformTestCase() {
    fun testCounts() {
        myFixture.addFileToProject("main.m", "s = Shape();\na = s.area();\nb = s.area();\nw = s.Width;\n")
        val file = myFixture.configureByText("Shape.m", """
            classdef Shape
                properties
                    Width = 1
                end
                methods
                    function a = area(obj)
                        a = obj.Width;
                    end
                    function unused(obj)
                    end
                end
            end
            """.trimIndent())
        val provider = MatlabUsagesCodeVisionProvider()
        fun hintFor(name: String): String? {
            val element = PsiTreeUtil.findChildrenOfAnyType(file, MatlabFunctionDeclaration::class.java, MatlabProperty::class.java)
                .first { (it as PsiNamedElement).name == name }
            return provider.getHint(element, file)
        }
        assertEquals("2 usages", hintFor("area"))
        assertEquals("2 usages", hintFor("Width"))
        assertEquals("no usages", hintFor("unused"))
    }
}
