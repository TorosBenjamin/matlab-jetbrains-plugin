package com.github.kornilova203.matlab.imports

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ImportsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("+geometry/Point.m", "classdef Point\nend\n")
        myFixture.addFileToProject("+geometry/distance.m", "function d = distance(a, b)\nd = 0;\nend\n")
        myFixture.addFileToProject("+shapes/Point.m", "classdef Point\nend\n")
        myFixture.enableInspections(MatlabUnimportedPackageMemberInspection())
    }

    private fun apply(code: String, fix: String, fileName: String = "main.m"): String {
        myFixture.configureByText(fileName, code)
        myFixture.launchAction(myFixture.findSingleIntention(fix))
        return myFixture.file.text
    }

    private fun fixes(code: String): List<String> {
        myFixture.configureByText("main.m", code)
        return myFixture.availableIntentions.map { it.text }.filter { it.startsWith("Import ") || it.startsWith("Qualify ") }
    }

    fun testOffersEveryPackage() = assertSameElements(fixes("p = Po<caret>int(1);\n"),
        "Import geometry.Point", "Qualify as geometry.Point", "Import shapes.Point", "Qualify as shapes.Point")

    fun testImportInScript() = assertEquals("% My script\nimport geometry.distance\nx = 1;\nd = distance(x, 2);\n",
        apply("% My script\nx = 1;\nd = dist<caret>ance(x, 2);\n", "Import geometry.distance"))

    fun testImportAfterHelpAndArguments() {
        val code = """
            function r = f(a)
                % F does things
                arguments
                    a double
                end
                r = Po<caret>int(a);
            end
            """.trimIndent() + "\n"
        val expected = """
            function r = f(a)
                % F does things
                arguments
                    a double
                end
                import geometry.Point
                r = Point(a);
            end
            """.trimIndent() + "\n"
        assertEquals(expected, apply(code, "Import geometry.Point", "f.m"))
    }

    fun testImportAfterExistingImports() = assertEquals(
        "import geometry.distance\nimport geometry.Point\nd = distance(1, 2);\np = Point(1);\n",
        apply("import geometry.distance\nd = distance(1, 2);\np = Po<caret>int(1);\n", "Import geometry.Point"))

    fun testImportGoesIntoTheMethod() {
        val code = "classdef C\n    methods\n        function m(obj)\n            p = Po<caret>int(1);\n        end\n    end\nend\n"
        val expected = "classdef C\n    methods\n        function m(obj)\n            import geometry.Point\n            p = Point(1);\n        end\n    end\nend\n"
        assertEquals(expected, apply(code, "Import geometry.Point", "C.m"))
    }

    fun testQualify() = assertEquals("p = geometry.Point(1);\n", apply("p = Po<caret>int(1);\n", "Qualify as geometry.Point"))

    fun testNotReportedWhenImported() = assertEmpty(fixes("import geometry.*\np = Po<caret>int(1);\n"))

    fun testNotReportedWhenQualified() = assertEmpty(fixes("p = geometry.Po<caret>int(1);\n"))

    fun testNotReportedForLocalFunction() = assertEmpty(fixes("p = Po<caret>int(1);\nfunction r = Point(x)\nr = x;\nend\n"))

    fun testNotReportedAfterDot() = assertEmpty(fixes("s = struct();\nx = s.Po<caret>int;\n"))

    fun testNotReportedForVariable() = assertEmpty(fixes("Point = 3;\ny = Po<caret>int + 1;\n"))

    fun testOptimizeImports() {
        myFixture.configureByText("main.m", "import geometry.distance geometry.Point\np = Point(1);\n")
        WriteCommandAction.runWriteCommandAction(project) { MatlabImportOptimizer().processFile(myFixture.file).run() }
        assertEquals("import geometry.Point\np = Point(1);\n", myFixture.editor.document.text)
    }

    fun testOptimizeRemovesWholeStatement() {
        myFixture.configureByText("main.m", "import geometry.*\nx = 1;\n")
        WriteCommandAction.runWriteCommandAction(project) { MatlabImportOptimizer().processFile(myFixture.file).run() }
        assertEquals("x = 1;\n", myFixture.editor.document.text)
    }

    fun testCompletionAddsImport() {
        myFixture.configureByText("main.m", "x = 1;\np = Poi<caret>\n")
        myFixture.complete(CompletionType.BASIC)
        val item = myFixture.lookupElements!!.first { it.`object` == "geometry.Point" }
        myFixture.lookup.currentItem = item
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertEquals("import geometry.Point\nx = 1;\np = Point\n", myFixture.editor.document.text)
    }
}
