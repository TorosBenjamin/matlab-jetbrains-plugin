package com.github.kornilova203.matlab.documentation

import com.github.kornilova203.matlab.MatlabDocumentationProvider
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocumentationLinksTest : BasePlatformTestCase() {
    private fun urls(code: String): List<String> {
        val file = myFixture.configureByText("test.m", code)
        val original = file.findElementAt(myFixture.caretOffset)!!
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, original.textOffset, file, original)
        return MatlabDocumentationProvider().getUrlFor(element, original).orEmpty()
    }

    private fun doc(code: String): String {
        val file = myFixture.configureByText("test.m", code)
        val original = file.findElementAt(myFixture.caretOffset)!!
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, original.textOffset, file, original)
        return MatlabDocumentationProvider().generateDoc(element, original)!!
    }

    fun testMovedPage() = assertEquals(
        listOf("https://www.mathworks.com/help/matlab/ref/double.abs.html"), urls("a = <caret>abs(-3)"))

    fun testPackageFunction() = assertEquals(
        listOf("https://www.mathworks.com/help/matlab/ref/matlab.lang.makevalidname.html"),
        urls("n = matlab.lang.<caret>makeValidName('a b')"))

    fun testUnknownFunctionFallsBackToSearch() = assertEquals(
        listOf("https://www.mathworks.com/help/search.html?qdoc=butterworthDesign&submitsearch="),
        urls("y = <caret>butterworthDesign(4, 0.2)"))

    fun testProjectFunctionHasNoMathWorksLink() = assertEquals(
        emptyList<String>(), urls("function r = abs(x)\nr = x;\nend\ny = <caret>abs(-1);\n"))

    fun testLocalVariableHasNoLink() = assertEquals(emptyList<String>(), urls("x = 1;\ny = <caret>x;\n"))

    fun testUnknownFunctionPopupOffersSearch() {
        val html = doc("y = <caret>butterworthDesign(4, 0.2)")
        assertTrue(html, html.contains("Not defined in this project."))
        assertTrue(html, html.contains("search.html?qdoc=butterworthDesign"))
    }
}
