package com.github.kornilova203.matlab.inspections

import com.intellij.testFramework.TestDataPath

@TestDataPath("inspections/unusedfunction")
class UnusedFunctionTest : MatlabInspectionTest() {
    fun testScript() = doTest()
    fun testFunctionFile() = doTest()
    fun testClassMethods() = doTest()
    fun testNested() = doTest()

    private fun doTest() = super.doTest(false, MatlabUnusedFunctionInspection::class.java)
}
