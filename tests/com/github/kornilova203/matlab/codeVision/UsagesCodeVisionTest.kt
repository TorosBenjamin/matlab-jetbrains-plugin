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

    /** Hint texts the editor would show, by line. */
    private fun hintsByLine(code: String): Map<Int, String> {
        myFixture.configureByText("test.m", code)
        val doc = myFixture.editor.document
        return MatlabUsagesCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file)
            .associate { (range, entry) -> doc.getLineNumber(range.startOffset) + 1 to entry.longPresentation }
    }

    fun testNestedAndLocalFunctionsGetHints() {
        val hints = hintsByLine("""
            function r = nested_outer(n)
                base = 10;
                r = inner(n);
                function out = inner(m)
                    out = base + m;
                end
            end
            """.trimIndent())
        assertEquals(hints.toString(), setOf(1, 4), hints.keys)
        assertEquals("1 usage", hints[4])
    }

    fun testMethodsGetHints() {
        val hints = hintsByLine("classdef C\n    methods\n        function a(obj)\n        end\n    end\nend\n")
        assertTrue(hints.toString(), hints.containsKey(1) && hints.containsKey(3))
    }
}
