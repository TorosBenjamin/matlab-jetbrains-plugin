package com.github.kornilova203.matlab.refactoring.rename

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RenameUsagesTest : BasePlatformTestCase() {
    fun testLocalFunctionInSameFile() {
        myFixture.configureByText("main.m", """
            x = hel<caret>per(1);
            h = @helper;
            function r = helper(n)
                r = n;
            end
            """.trimIndent())
        myFixture.renameElementAtCaretUsingHandler("assist")
        myFixture.checkResult("""
            x = assist(1);
            h = @assist;
            function r = assist(n)
                r = n;
            end
            """.trimIndent())
    }

    fun testFunctionInOtherFile() {
        val caller = myFixture.addFileToProject("caller.m", "y = add_numbers(1, 2);\nz = add_numbers(3, 4);\n")
        myFixture.configureByText("add_numbers.m", "function r = add_<caret>numbers(a, b)\nr = a + b;\nend\n")
        myFixture.renameElementAtCaretUsingHandler("sum_two")
        assertEquals("y = sum_two(1, 2);\nz = sum_two(3, 4);\n", caller.text)
        assertEquals("function r = sum_two(a, b)\nr = a + b;\nend\n", myFixture.file.text)
    }

    fun testFromCallSite() {
        myFixture.addFileToProject("add_numbers.m", "function r = add_numbers(a, b)\nr = a + b;\nend\n")
        myFixture.configureByText("caller.m", "y = add_num<caret>bers(1, 2);\n")
        myFixture.renameElementAtCaretUsingHandler("sum_two")
        assertEquals("y = sum_two(1, 2);\n", myFixture.file.text)
    }

    fun testMethod() {
        val caller = myFixture.addFileToProject("main.m", "s = Shape();\na = s.area();\n")
        myFixture.configureByText("Shape.m", """
            classdef Shape
                methods
                    function a = ar<caret>ea(obj)
                        a = 1;
                    end
                    function b = twice(obj)
                        b = 2 * obj.area();
                    end
                end
            end
            """.trimIndent())
        myFixture.renameElementAtCaretUsingHandler("surface")
        assertTrue(myFixture.file.text, myFixture.file.text.contains("obj.surface()"))
        assertEquals("s = Shape();\na = s.surface();\n", caller.text)
    }

    private val shape = """
        classdef Shape < handle
            methods
                function obj = Shape(w)
                end
            end
            methods (Static)
                function s = unit()
                    s = Shape(1);
                end
            end
        end
        """.trimIndent()

    fun testRenamingConstructorRenamesClassAndFile() {
        val caller = myFixture.addFileToProject("main.m", "s = Shape(2);\n")
        myFixture.configureByText("Shape.m", shape.replace("function obj = Shape(w)", "function obj = Sha<caret>pe(w)"))
        myFixture.renameElementAtCaretUsingHandler("Figure")
        val text = myFixture.file.text
        assertTrue(text, text.startsWith("classdef Figure < handle"))
        assertTrue(text, text.contains("function obj = Figure(w)"))
        assertTrue(text, text.contains("s = Figure(1);"))
        assertEquals("s = Figure(2);\n", caller.text)
        assertEquals("Figure.m", myFixture.file.name)
    }

    fun testRenamingClassRenamesConstructor() {
        myFixture.configureByText("Shape.m", shape.replace("classdef Shape", "classdef Sha<caret>pe"))
        myFixture.renameElementAtCaretUsingHandler("Figure")
        assertTrue(myFixture.file.text.contains("function obj = Figure(w)"))
        assertEquals("Figure.m", myFixture.file.name)
    }

    fun testRenamingMainFunctionRenamesFile() {
        myFixture.configureByText("add_numbers.m", "function r = add_<caret>numbers(a, b)\nr = a + b;\nend\n")
        myFixture.renameElementAtCaretUsingHandler("sum_two")
        assertEquals("sum_two.m", myFixture.file.name)
    }

    fun testRenamingLocalFunctionKeepsFile() {
        myFixture.configureByText("main_entry.m", "function main_entry()\nhelper();\nend\nfunction hel<caret>per()\nend\n")
        myFixture.renameElementAtCaretUsingHandler("assist")
        assertEquals("main_entry.m", myFixture.file.name)
        assertTrue(myFixture.file.text.contains("assist();"))
    }
}
