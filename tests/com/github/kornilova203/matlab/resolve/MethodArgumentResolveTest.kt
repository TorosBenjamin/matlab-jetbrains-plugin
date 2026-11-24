package com.github.kornilova203.matlab.resolve

import com.github.kornilova203.matlab.inspections.MatlabUnusedVariableInspection
import com.github.kornilova203.matlab.psi.MatlabAssignExpr
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabRefExpr
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Names inside the brackets of obj.method(...) and pkg.func(...) are the caller's, not the class's or package's. */
class MethodArgumentResolveTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("Shape.m", """
            classdef Shape
                properties
                    parent
                end
                methods
                    function align(obj, key, parent, target)
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("+geometry/make.m", "function r = make(parent)\nr = parent;\nend\n")
        myFixture.addFileToProject("+geometry/parent.m", "function r = parent()\nr = 1;\nend\n")
    }

    private fun resolveAtCaret(code: String) =
        PsiTreeUtil.getParentOfType(myFixture.configureByText("main.m", code).findElementAt(myFixture.caretOffset), MatlabRefExpr::class.java)!!
            .reference!!.resolve()

    fun testArgumentOfMethodCall() {
        val target = resolveAtCaret("s = Shape();\nparent = 1;\nparent = parent(1);\ns.align(2, par<caret>ent, 3)\n")
        assertTrue(target.toString(), target is MatlabAssignExpr)
        assertEquals(3, myFixture.editor.document.getLineNumber(target!!.textOffset) + 1)
    }

    fun testMethodNameStillResolvesToTheMethod() {
        val target = resolveAtCaret("s = Shape();\nparent = 1;\ns.al<caret>ign(2, parent, 3)\n")
        assertTrue(target.toString(), target is MatlabFunctionDeclaration && (target as PsiNamedElement).name == "align")
    }

    fun testArgumentOfPackageCall() {
        val target = resolveAtCaret("parent = 1;\nr = geometry.make(par<caret>ent);\n")
        assertTrue(target.toString(), target is MatlabAssignExpr)
    }

    fun testUsedInMethodCallIsNotUnused() {
        myFixture.enableInspections(MatlabUnusedVariableInspection())
        myFixture.configureByText("f.m", "function f(s, parent)\nparent = parent(1);\ns = Shape();\ns.align(2, parent, 3)\nend\n")
        val unused = myFixture.doHighlighting().filter { it.description?.contains("never used") == true }
        assertEmpty(unused.map { it.description })
    }
}
