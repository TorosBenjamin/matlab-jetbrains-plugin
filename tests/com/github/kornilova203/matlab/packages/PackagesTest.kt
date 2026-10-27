package com.github.kornilova203.matlab.packages

import com.github.kornilova203.matlab.MatlabFolderIconProvider
import com.github.kornilova203.matlab.inspections.MatlabUnresolvedImportInspection
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackagesTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("+geometry/Point.m", """
            classdef Point
                properties
                    X = 0
                end
                methods
                    function obj = Point(x)
                        obj.X = x;
                    end
                    function obj = move(obj, d)
                        obj.X = obj.X + d;
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("+geometry/distance.m", "function d = distance(p1, p2)\nd = p2.X - p1.X;\nend\n")
        myFixture.addFileToProject("+geometry/@Legacy/Legacy.m", "function obj = Legacy(v)\nobj = class(struct('v', v), 'geometry.Legacy');\nend\n")
        myFixture.addFileToProject("+geometry/+shapes/Circle.m", "classdef Circle\nend\n")
    }

    private fun resolve(code: String): String? {
        myFixture.configureByText("main.m", code)
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve() ?: return null
        return (target as PsiNamedElement).name + " in " + target.containingFile.virtualFile.path.substringAfter("/src/")
    }

    private fun complete(code: String): List<String> {
        myFixture.configureByText("main.m", code)
        myFixture.complete(CompletionType.BASIC)
        return myFixture.lookupElementStrings.orEmpty()
    }

    fun testQualifiedFunction() = assertEquals("distance in +geometry/distance.m",
        resolve("d = geometry.dist<caret>ance(a, b);\n"))

    fun testQualifiedClass() = assertEquals("Point in +geometry/Point.m",
        resolve("p = geometry.Po<caret>int(1);\n"))

    fun testSubpackage() = assertEquals("Circle in +geometry/+shapes/Circle.m",
        resolve("c = geometry.shapes.Cir<caret>cle();\n"))

    fun testMethodOfPackageClassInstance() = assertEquals("move in +geometry/Point.m",
        resolve("p = geometry.Point(1);\np = p.mo<caret>ve(2);\n"))

    fun testPlainNameNeedsImport() = assertNull(resolve("p = Po<caret>int(1);\n"))

    fun testWildcardImport() = assertEquals("Point in +geometry/Point.m",
        resolve("import geometry.*\np = Po<caret>int(1);\n"))

    fun testNamedImport() = assertEquals("distance in +geometry/distance.m",
        resolve("import geometry.distance\nd = dist<caret>ance(a, b);\n"))

    fun testCompletionAfterPackage() = assertContainsElements(
        complete("x = geometry.<caret>\n"), "Point", "distance", "Legacy", "shapes")

    fun testImportKeywordIsSuggested() = assertContainsElements(complete("imp<caret>\n"), "import")

    fun testCompletionAfterImport() {
        val v = complete("import geometry.<caret>\n")
        assertContainsElements(v, "Point", "distance", "shapes", "*")
        assertDoesntContain(v, "disp", "zeros")
    }

    fun testCompletionOfImportedPackageName() {
        // The only match is inserted directly.
        val variants = complete("import geo<caret>\n")
        assertTrue(variants.contains("geometry") || myFixture.editor.document.text == "import geometry\n")
    }

    fun testUnresolvedImport() {
        myFixture.enableInspections(MatlabUnresolvedImportInspection::class.java)
        myFixture.configureByText("main.m",
            "import geometry.*\nimport <warning descr=\"Cannot find 'geometry.Pointt'\">geometry.Pointt</warning>\n" +
                    "import <warning descr=\"Cannot find 'geometry.nope'\">geometry.nope.*</warning>\nimport java.io.*\n")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testFolderIcons() {
        val provider = MatlabFolderIconProvider()
        val geometry = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("+geometry"))!!
        val legacy = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("+geometry/@Legacy"))!!
        assertEquals(AllIcons.Nodes.Package, provider.getIcon(geometry, 0))
        assertEquals(AllIcons.Nodes.Class, provider.getIcon(legacy, 0))
    }
}
