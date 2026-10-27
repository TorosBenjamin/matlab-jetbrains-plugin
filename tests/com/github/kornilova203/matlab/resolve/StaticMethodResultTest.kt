package com.github.kornilova203.matlab.resolve

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** `lg = Logger.instance(); lg.log(...)`: a static method without parameters used to crash resolution. */
class StaticMethodResultTest : BasePlatformTestCase() {
    fun testResolveMemberOfStaticMethodResult() {
        myFixture.addFileToProject("Logger.m", """
            classdef Logger < handle
                methods (Static)
                    function obj = instance()
                        persistent inst
                        if isempty(inst)
                            inst = Logger();
                        end
                        obj = inst;
                    end
                end
                methods
                    function log(obj, msg)
                    end
                end
            end
            """.trimIndent())
        myFixture.configureByText("main.m", "lg = Logger.instance();\nlg.lo<caret>g(\"hello\");\n")
        // Must not throw; the result may be null until return types of such methods are inferred.
        myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        myFixture.doHighlighting()
    }
}
