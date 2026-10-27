package com.github.kornilova203.matlab.packages

import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabProperty
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** @Shape/Shape.m holds the classdef; @Shape/area.m defines a method in its own file. */
class ClassFolderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("@Shape/Shape.m", """
            classdef Shape
                properties
                    Width = 1
                end
                methods
                    a = area(obj)
                end
            end
            """.trimIndent())
    }

    private fun kindOf(target: Any?) = when (target) {
        is MatlabFunctionDeclaration -> "function " + (target as PsiNamedElement).name
        is MatlabProperty -> "property " + (target as PsiNamedElement).name
        else -> null
    }

    fun testMethodFileSeesClassMembers() {
        val file = myFixture.addFileToProject("@Shape/area.m", "function a = area(obj)\na = obj.Width;\nend\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val offset = file.text.indexOf("Width")
        assertEquals("property Width", kindOf(myFixture.file.findReferenceAt(offset)?.resolve()))
    }

    fun testCallerFindsMethodFile() {
        myFixture.addFileToProject("@Shape/area.m", "function a = area(obj)\na = 1;\nend\n")
        myFixture.configureByText("main.m", "s = Shape();\nx = s.ar<caret>ea();\n")
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertEquals("function area", kindOf(target))
        assertEquals("area.m", (target as MatlabFunctionDeclaration).containingFile.name)
    }
}
