package com.github.kornilova203.matlab.foreign

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** References from MATLAB code into Python, Java and C. */
class ForeignReferenceTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("helpers.py", """
            import math

            def add(a, b):
                return a + b

            class Greeter:
                def __init__(self, name):
                    self.name = name

                def greet(self):
                    return "hi " + self.name

            LIMIT = 10
            """.trimIndent())
        myFixture.addFileToProject("pkg/__init__.py", "")
        myFixture.addFileToProject("pkg/tools.py", "def scale(x):\n    return 2 * x\n")
        myFixture.addFileToProject("com/example/Util.java", """
            package com.example;

            public class Util {
                public static final int MAX = 5;

                public static int twice(int x) {
                    return x * 2;
                }
            }
            """.trimIndent())
        myFixture.addFileToProject("mymex.c", """
            #include "mex.h"

            void mexFunction(int nlhs, mxArray *plhs[], int nrhs, const mxArray *prhs[])
            {
            }
            """.trimIndent())
        myFixture.addFileToProject("mymath.h", """
            #ifndef MYMATH_H
            #define MYMATH_H

            double add(double a, double b);
            int count_items(const char *s);

            #endif
            """.trimIndent())
    }

    /** Targets of the reference at the caret, as "name in file:line". */
    private fun targets(code: String): List<String> {
        myFixture.configureByText("main.m", code)
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset) ?: return emptyList()
        val elements = (ref as? PsiPolyVariantReference)?.multiResolve(false)?.mapNotNull { it.element } ?: listOfNotNull(ref.resolve())
        return elements.map(::describe)
    }

    private fun describe(element: PsiElement): String {
        val file = element as? PsiFile ?: element.containingFile
        val line = ForeignFiles.text(file).subSequence(0, element.textOffset).count { it == '\n' } + 1
        return "${(element as PsiNamedElement).name} in ${file.name}:$line"
    }

    // Python

    fun testPythonFunction() = assertEquals(listOf("add in helpers.py:3"), targets("y = py.helpers.ad<caret>d(1, 2);\n"))

    fun testPythonClass() = assertEquals(listOf("Greeter in helpers.py:6"), targets("g = py.helpers.Gree<caret>ter('x');\n"))

    fun testPythonMethod() = assertEquals(listOf("greet in helpers.py:10"), targets("f = py.helpers.Greeter.gre<caret>et;\n"))

    fun testPythonVariable() = assertEquals(listOf("LIMIT in helpers.py:13"), targets("n = py.helpers.LIM<caret>IT;\n"))

    fun testPythonModule() = assertEquals(listOf("helpers.py in helpers.py:1"), targets("y = py.help<caret>ers.add(1, 2);\n"))

    fun testPythonPackageModule() = assertEquals(listOf("scale in tools.py:1"), targets("y = py.pkg.tools.sca<caret>le(2);\n"))

    fun testPythonUnknown() = assertEmpty(targets("y = py.helpers.nothi<caret>ng(2);\n"))

    fun testPythonModuleCompletion() {
        myFixture.configureByText("main.m", "x = py.<caret>\n")
        myFixture.complete(CompletionType.BASIC)
        assertContainsElements(myFixture.lookupElementStrings!!, "helpers", "pkg", "list")
    }

    fun testPythonMemberCompletion() {
        myFixture.configureByText("main.m", "x = py.helpers.<caret>\n")
        myFixture.complete(CompletionType.BASIC)
        val items = myFixture.lookupElementStrings!!
        assertContainsElements(items, "add", "Greeter")
        assertDoesntContain(items, "math", "__init__")
    }

    // Java

    fun testJavaClass() = assertEquals(listOf("Util in Util.java:3"), targets("u = com.example.Ut<caret>il;\n"))

    fun testJavaMethod() = assertEquals(listOf("twice in Util.java:6"), targets("x = com.example.Util.twi<caret>ce(3);\n"))

    fun testJavaField() = assertEquals(listOf("MAX in Util.java:4"), targets("x = com.example.Util.MA<caret>X;\n"))

    fun testJavaImport() = assertEquals(listOf("Util in Util.java:3"), targets("import com.example.*\nu = Ut<caret>il();\n"))

    fun testJavaObject() = assertEquals(listOf("Util in Util.java:3"), targets("u = javaObject('com.example.Ut<caret>il');\n"))

    fun testJavaMethodFunction() = assertEquals(listOf("twice in Util.java:6"),
        targets("x = javaMethod('twi<caret>ce', 'com.example.Util', 3);\n"))

    fun testStructFieldIsNotJava() = assertEmpty(targets("s = struct();\ny = s.Ut<caret>il;\n"))

    // C

    fun testMexFunction() = assertEquals(listOf("mexFunction in mymex.c:3"), targets("y = mym<caret>ex(1);\n"))

    fun testVariableIsNotMex() = assertEmpty(targets("mymex = 1;\ny = mym<caret>ex;\n").filter { "mymex.c" in it })

    fun testCalllib() = assertEquals(listOf("add in mymath.h:4"),
        targets("loadlibrary('mymath', 'mymath.h');\ny = calllib('mymath', 'ad<caret>d', 1, 2);\n"))

    fun testCalllibHeaderByLibraryName() = assertEquals(listOf("count_items in mymath.h:5"),
        targets("n = calllib('mymath', 'count_it<caret>ems', 'abc');\n"))

    fun testLoadlibraryHeader() = assertEquals(listOf("mymath.h in mymath.h:1"),
        targets("loadlibrary('mymath', 'mym<caret>ath.h');\n"))

    fun testMexSourceString() = assertEquals(listOf("mymex.c in mymex.c:1"), targets("mex('mym<caret>ex.c');\n"))

    fun testMexCommand() = assertEquals(listOf("mymex.c in mymex.c:1"), targets("mex -O mym<caret>ex.c\n"))

    fun testRenameHeaderUpdatesLoadlibrary() {
        myFixture.configureByText("main.m", "loadlibrary('mymath', 'mymath.h');\n")
        val header = myFixture.findFileInTempDir("mymath.h")
        myFixture.renameElement(psiManager.findFile(header)!!, "othermath.h")
        assertEquals("loadlibrary('mymath', 'othermath.h');\n", myFixture.editor.document.text)
    }
}
