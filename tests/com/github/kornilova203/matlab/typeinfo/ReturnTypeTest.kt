package com.github.kornilova203.matlab.typeinfo

import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.psi.MatlabProperty
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReturnTypeTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("AppConfig.m", """
            classdef AppConfig < handle
                properties
                    Verbose = false
                end
                methods (Access = private)
                    function obj = AppConfig()
                    end
                end
                methods (Static)
                    function obj = getInstance()
                        persistent instance
                        if isempty(instance) || ~isvalid(instance)
                            instance = AppConfig();
                        end
                        obj = instance;
                    end
                end
                methods
                    function enableVerbose(obj)
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("Logger.m", """
            classdef Logger < handle
                methods (Static)
                    function obj = instance()
                        arguments (Output)
                            obj Logger
                        end
                        obj = makeIt();
                    end
                end
                methods
                    function log(obj, msg)
                    end
                end
            end
            """.trimIndent())
        myFixture.addFileToProject("Point.m", "classdef Point\nmethods\nfunction obj = move(obj, d)\nend\nend\nend\n")
        myFixture.addFileToProject("make_point.m", "function p = make_point(x, y)\np = Point(x, y);\nend\n")
    }

    /** Name of the declaration the reference at the caret resolves to. */
    private fun resolve(code: String): String? {
        myFixture.configureByText("main.m", code)
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve() ?: return null
        val kind = when (target) { is MatlabFunctionDeclaration -> "method"; is MatlabProperty -> "property"; else -> "other" }
        return "$kind ${(target as PsiNamedElement).name}"
    }

    fun testSingletonWithPersistentVariable() =
        assertEquals("method enableVerbose", resolve("cfg = AppConfig.getInstance();\ncfg.enable<caret>Verbose();\n"))

    fun testSingletonProperty() =
        assertEquals("property Verbose", resolve("cfg = AppConfig.getInstance();\nv = cfg.Ver<caret>bose;\n"))

    fun testDeclaredOutputType() =
        assertEquals("method log", resolve("lg = Logger.instance();\nlg.lo<caret>g(\"hello\");\n"))

    fun testFactoryFunction() =
        assertEquals("method move", resolve("mp = make_point(1, 2);\nmp = mp.mo<caret>ve(1);\n"))

    fun testRecursiveFunctionsDoNotHang() {
        // A cycle can't be cached; the test framework reports that unless told it's expected.
        com.intellij.openapi.util.RecursionManager.disableMissedCacheAssertions(testRootDisposable)
        myFixture.addFileToProject("f.m", "function r = f()\nr = g();\nend\n")
        myFixture.addFileToProject("g.m", "function r = g()\nr = f();\nend\n")
        assertNull(resolve("x = f();\nx.fie<caret>ld\n"))
    }

    fun testSelfAssignmentDoesNotHang() {
        com.intellij.openapi.util.RecursionManager.disableMissedCacheAssertions(testRootDisposable)
        assertNull(resolve("function r = h()\nr = r;\nend\nx = h();\nx.a<caret>b\n"))
    }

    fun testCompletionOnSingleton() {
        myFixture.configureByText("main.m", "cfg = AppConfig.getInstance();\ncfg.<caret>\n")
        myFixture.complete(com.intellij.codeInsight.completion.CompletionType.BASIC)
        assertContainsElements(myFixture.lookupElementStrings!!, "Verbose", "enableVerbose")
    }
}
