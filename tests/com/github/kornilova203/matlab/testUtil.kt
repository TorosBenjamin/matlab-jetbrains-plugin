package com.github.kornilova203.matlab

import com.intellij.testFramework.TestDataPath
import java.nio.file.Paths

fun getTestDataRoot(clazz: Class<*>): String {
    val annotation = clazz.getAnnotation(TestDataPath::class.java)!!
    return Paths.get("testData", annotation.value).toAbsolutePath().toString()
}

/** The MATLAB reference among the references at an offset (names can also refer to Python, Java or C code). */
fun matlabReference(reference: com.intellij.psi.PsiReference?): MatlabReference = when (reference) {
    is MatlabReference -> reference
    is com.intellij.psi.PsiMultiReference -> reference.references.filterIsInstance<MatlabReference>().single()
    else -> throw AssertionError("Not a MATLAB reference: $reference")
}
