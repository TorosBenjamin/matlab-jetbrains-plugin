package com.github.kornilova203.matlab.structure

import com.github.kornilova203.matlab.psi.MatlabFile
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StructureViewTest : BasePlatformTestCase() {
    private fun tree(code: String): String {
        val file = myFixture.configureByText("test.m", code) as MatlabFile
        val sb = StringBuilder()
        fun walk(element: StructureViewTreeElement, depth: Int) {
            val p = element.presentation
            sb.append("  ".repeat(depth)).append(p.presentableText)
            p.locationString?.let { sb.append(" [").append(it).append("]") }
            sb.append('\n')
            element.children.forEach { walk(it as StructureViewTreeElement, depth + 1) }
        }
        MatlabStructureElement(file).children.forEach { walk(it as StructureViewTreeElement, 0) }
        return sb.toString()
    }

    fun testClass() = assertEquals("""
        Shape [< handle]
          Width [double]
          Height
          Shape(w, h)
          area(obj)
          set.Width(obj, v)
          describe(obj)
          Resized [event]
          Red
        """.trimIndent() + "\n", tree("""
        classdef Shape < handle
            properties
                Width (1,1) double = 1
                Height = 1
            end
            methods
                function obj = Shape(w, h)
                end
                function a = area(obj)
                    a = 1;
                end
                function set.Width(obj, v)
                end
            end
            methods (Access = protected)
                h = describe(obj)
            end
            events
                Resized
            end
            enumeration
                Red
            end
        end
        """.trimIndent()))

    fun testScriptWithSectionsAndLocalFunctions() = assertEquals("""
        Setup
        Run it
        helper(n)
          inner(m)
        """.trimIndent() + "\n", tree("""
        %% Setup
        x = 1;
        %%%%%%%%%%%%%%%%
        %% Run it
        y = helper(x);
        %not a section
        function r = helper(n)
            r = inner(n);
            function out = inner(m)
                out = m;
            end
        end
        """.trimIndent()))
}
