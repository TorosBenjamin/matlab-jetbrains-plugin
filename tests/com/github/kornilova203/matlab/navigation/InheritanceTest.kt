package com.github.kornilova203.matlab.navigation

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabInheritance
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InheritanceTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("Shape.m", """
            classdef Shape < handle
                properties
                    Name = ""
                end
                methods
                    function obj = Shape(name)
                        obj.Name = name;
                    end
                    function a = area(obj)
                        a = 0;
                    end
                    function d = describe(obj)
                        d = obj.Name;
                    end
                end
                methods (Access = private)
                    function s = secret(obj)
                        s = 1;
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("Circle.m", """
            classdef Circle < Shape
                properties
                    R = 1
                end
                methods
                    function obj = Circle(r)
                        obj@Shape("circle");
                        obj.R = r;
                    end
                    function a = area(obj)
                        a = pi * obj.R^2;
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("Ring.m", """
            classdef Ring < Circle
                methods
                    function a = area(obj)
                        a = 1;
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("+geo/Base.m", "classdef Base\n    methods\n        function n = sides(obj)\n            n = 0;\n        end\n    end\nend\n")
        myFixture.addFileToProject("Square.m", "classdef Square < geo.Base\n    methods\n        function n = sides(obj)\n            n = 4;\n        end\n    end\nend\n")
    }

    private fun cls(name: String): MatlabClassDeclaration =
        PsiTreeUtil.findChildOfType(myFixture.findFileInTempDir(if (name.contains('/')) name else "$name.m")
            .let { psiManager.findFile(it)!! }, MatlabClassDeclaration::class.java)!!

    private fun method(className: String, name: String): MatlabFunctionDeclaration =
        MatlabInheritance.ownMethods(cls(className)).first { (it as PsiNamedElement).name == name }

    private fun names(elements: List<PsiElement>) = elements.map { (it as PsiNamedElement).name }

    private fun describe(element: PsiElement?) = element?.let {
        (it as PsiNamedElement).name + " in " + it.containingFile.name
    }

    private fun resolve(code: String): String? {
        myFixture.configureByText("main.m", code)
        return describe(myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve())
    }

    fun testSuperClasses() {
        assertEquals(listOf("Shape"), names(MatlabInheritance.superClasses(cls("Circle"))))
        assertEquals(listOf("Circle", "Shape"), names(MatlabInheritance.allSuperClasses(cls("Ring"))))
        assertEquals(listOf("Base"), names(MatlabInheritance.superClasses(cls("Square"))))
    }

    fun testSubclasses() {
        assertEquals(listOf("Circle"), names(MatlabInheritance.directSubclasses(cls("Shape"))))
        assertSameElements(names(MatlabInheritance.allSubclasses(cls("Shape"))), "Circle", "Ring")
        assertEquals(listOf("Square"), names(MatlabInheritance.directSubclasses(cls("+geo/Base.m"))))
    }

    fun testSuperAndOverridingMethods() {
        assertEquals("area in Shape.m", describe(MatlabInheritance.superMethods(method("Circle", "area")).single()))
        assertEquals("area in Circle.m", describe(MatlabInheritance.superMethods(method("Ring", "area")).single()))
        assertSameElements(MatlabInheritance.overridingMethods(method("Shape", "area")).map(::describe),
            "area in Circle.m", "area in Ring.m")
        assertEmpty(MatlabInheritance.overridingMethods(method("Shape", "describe")))
        assertEmpty(MatlabInheritance.superMethods(method("Circle", "Circle")))  // constructor
    }

    fun testInheritedMethodResolves() = assertEquals("describe in Shape.m", resolve("c = Circle(1);\nd = c.descr<caret>ibe();\n"))

    fun testOverrideResolvesToOwnMethod() = assertEquals("area in Circle.m", resolve("c = Circle(1);\na = c.ar<caret>ea();\n"))

    fun testInheritedProperty() = assertEquals("Name in Shape.m", resolve("c = Circle(1);\nn = c.Na<caret>me;\n"))

    fun testPrivateMethodNotInherited() = assertNull(resolve("c = Circle(1);\ns = c.sec<caret>ret();\n"))

    fun testInheritedThroughTwoLevels() = assertEquals("describe in Shape.m", resolve("r = Ring(1);\nd = r.descr<caret>ibe();\n"))

    fun testInheritedFromPackageClass() = assertEquals("sides in Square.m", resolve("s = Square();\nn = s.si<caret>des();\n"))

    fun testCompletionIncludesInherited() {
        myFixture.configureByText("main.m", "c = Circle(1);\nc.<caret>\n")
        myFixture.complete(CompletionType.BASIC)
        val items = myFixture.lookupElementStrings!!
        assertContainsElements(items, "area", "describe", "Name", "R")
        assertDoesntContain(items, "secret", "Shape")
    }

    fun testGoToImplementation() {
        val implementations = DefinitionsScopedSearch.search(method("Shape", "area")).findAll().map(::describe)
        assertSameElements(implementations, "area in Circle.m", "area in Ring.m")
        assertSameElements(DefinitionsScopedSearch.search(cls("Shape")).findAll().map(::describe), "Circle in Circle.m", "Ring in Ring.m")
    }

    fun testGoToSuper() {
        val circle = method("Circle", "area")
        assertEquals(listOf("area in Shape.m"), MatlabGotoSuperHandler.superTargets(circle.firstChild).map(::describe))
        assertEquals(listOf("Shape in Shape.m"), MatlabGotoSuperHandler.superTargets(cls("Circle").firstChild).map(::describe))
    }

    fun testGutterIcons() {
        myFixture.configureFromTempProjectFile("Circle.m")
        val tooltips = myFixture.findAllGutters().mapNotNull { it.tooltipText }
        for (expected in listOf("Is subclassed by Ring", "Overrides method in Shape", "Is overridden in Ring")) {
            assertTrue("$expected not in $tooltips", tooltips.any { expected in it })
        }
    }
}
