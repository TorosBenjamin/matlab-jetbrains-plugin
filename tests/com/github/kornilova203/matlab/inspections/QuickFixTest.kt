package com.github.kornilova203.matlab.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class QuickFixTest : BasePlatformTestCase() {
    private fun addGeometry() {
        myFixture.addFileToProject("+geometry/Point.m", "classdef Point\nend\n")
        myFixture.addFileToProject("+geometry/distance.m", "function d = distance(a, b)\nd = 0;\nend\n")
    }

    /** Applies the fix or intention named [fix] at the caret of [code] and returns the file's text. */
    private fun apply(code: String, fix: String, fileName: String = "main.m"): String {
        myFixture.configureByText(fileName, code)
        myFixture.launchAction(myFixture.findSingleIntention(fix))
        return myFixture.file.text
    }

    private fun assertNotOffered(code: String, fix: String) {
        myFixture.configureByText("main.m", code)
        assertEmpty(myFixture.filterAvailableIntentions(fix))
    }

    // Imports

    fun testRemoveOnlyUnusedImport() {
        addGeometry()
        myFixture.enableInspections(MatlabUnusedImportInspection())
        assertEquals("x = 1;\n", apply("import geom<caret>etry.*\nx = 1;\n", "Remove import 'geometry.*'"))
    }

    fun testRemoveOneOfSeveralImports() {
        addGeometry()
        myFixture.enableInspections(MatlabUnusedImportInspection())
        assertEquals("import geometry.Point\np = Point();\n",
            apply("import geometry.dist<caret>ance geometry.Point\np = Point();\n", "Remove import 'geometry.distance'"))
    }

    fun testRemoveLastOfSeveralImports() {
        addGeometry()
        myFixture.enableInspections(MatlabUnusedImportInspection())
        assertEquals("import geometry.Point\np = Point();\n",
            apply("import geometry.Point geometry.dist<caret>ance\np = Point();\n", "Remove import 'geometry.distance'"))
    }

    fun testRemoveUnresolvedImport() {
        addGeometry()
        myFixture.enableInspections(MatlabUnresolvedImportInspection())
        assertEquals("x = 1;\n", apply("import geometry.Poi<caret>ntt\nx = 1;\n", "Remove import 'geometry.Pointt'"))
    }

    // Functions and unreachable code

    fun testRemoveUnusedLocalFunction() {
        myFixture.enableInspections(MatlabUnusedFunctionInspection())
        val result = apply("function main()\ndisp(1)\nend\n\nfunction hel<caret>per()\ndisp(2)\nend\n",
            "Remove local function 'helper'")
        assertEquals("function main()\ndisp(1)\nend", result.trimEnd())
    }

    fun testRemoveUnusedPrivateMethod() {
        myFixture.enableInspections(MatlabUnusedFunctionInspection())
        val code = """
            classdef Shape
                methods (Access = private)
                    function a = sec<caret>ret(obj)
                        a = 1;
                    end
                end
            end
            """.trimIndent() + "\n"
        val result = apply(code, "Remove private method 'secret'", "Shape.m")
        assertFalse(result, result.contains("secret"))
        assertTrue(result, result.contains("methods (Access = private)"))
    }

    fun testRemoveUnreachableCode() {
        myFixture.enableInspections(MatlabUnreachableCodeInspection())
        val result = apply("function f()\ndisp(1)\nreturn\n<caret>x = 2;\nend\n", "Remove unreachable code")
        assertEquals("function f()\ndisp(1)\nreturn\nend\n", result)
    }

    // Creating functions

    fun testCreateFunctionFile() {
        myFixture.configureByText("main.m", "y = sca<caret>leBy(x, 2);\n")
        myFixture.launchAction(myFixture.findSingleIntention("Create function 'scaleBy' in scaleBy.m"))
        val created = myFixture.findFileInTempDir("scaleBy.m")
        assertNotNull(created)
        assertEquals("function y = scaleBy(x, arg2)\n    \nend\n", String(created.contentsToByteArray()))
    }

    fun testCreateLocalFunction() {
        val result = apply("[a, b] = spl<caret>itIt(data);\n", "Create local function 'splitIt'")
        assertEquals("[a, b] = splitIt(data);\n\nfunction [a, b] = splitIt(data)\n    \nend\n", result)
    }

    fun testCreateFunctionWithoutOutputs() {
        val result = apply("show<caret>It(x, x)\n", "Create local function 'showIt'")
        assertEquals("showIt(x, x)\n\nfunction showIt(x, x2)\n    \nend\n", result)
    }

    fun testNotOfferedForKnownFunction() = assertNotOffered("p<caret>lot(1, 2)\n", "Create function")

    fun testNotOfferedForDefinedFunction() {
        myFixture.addFileToProject("helper.m", "function helper()\nend\n")
        assertNotOffered("hel<caret>per()\n", "Create function")
    }

    fun testNotOfferedForMethodCall() = assertNotOffered("s = struct();\ns.fie<caret>ld(1)\n", "Create function")
}
