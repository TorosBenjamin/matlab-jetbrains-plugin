package com.github.kornilova203.matlab

import com.intellij.psi.stubs.StubTextInconsistencyException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Stubs built from text must match the parsed tree, or IntelliJ fails when it loads the file. */
class StubConsistencyTest : BasePlatformTestCase() {
    fun testAllTestFiles() {
        val files = listOf("testData/parser", "testData/stubs")
            .flatMap { dir -> File(dir).listFiles { f -> f.extension == "m" }!!.toList() }.sorted()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val psi = myFixture.configureByText(file.name, file.readText().replace("\r\n", "\n"))
            try {
                StubTextInconsistencyException.checkStubTextConsistency(psi)
            } catch (e: Throwable) {
                throw AssertionError("Stub mismatch in ${file.name}", e)
            }
        }
    }
}
