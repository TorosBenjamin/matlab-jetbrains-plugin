package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.editor.templates.MatlabSurroundDescriptor
import com.github.kornilova203.matlab.highlighting.MatlabSectionSeparatorProvider
import com.github.kornilova203.matlab.inspections.MatlabUnusedImportInspection
import com.github.kornilova203.matlab.navigation.MatlabGotoClassContributor
import com.github.kornilova203.matlab.navigation.MatlabGotoSymbolContributor
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.FindSymbolParameters

class PolishTest : BasePlatformTestCase() {
    fun testGotoClassAndSymbol() {
        myFixture.addFileToProject("Shape.m", "classdef Shape\nmethods\nfunction a = area(obj, k)\nend\nend\nend\n")
        myFixture.addFileToProject("+geometry/distance.m", "function d = distance(a, b)\nend\n")
        val scope = GlobalSearchScope.projectScope(project)
        fun names(contributor: com.intellij.navigation.ChooseByNameContributorEx) =
            CommonProcessors.CollectProcessor<String>().also { contributor.processNames(it, scope, null) }.results.toSet()
        assertTrue(names(MatlabGotoClassContributor()).contains("Shape"))
        assertTrue(names(MatlabGotoSymbolContributor()).containsAll(listOf("area", "distance")))

        val items = CommonProcessors.CollectProcessor<NavigationItem>()
        MatlabGotoSymbolContributor().processElementsWithName("distance", items, FindSymbolParameters.wrap("distance", project, false))
        val presentation = items.results.single().presentation!!
        assertEquals("distance(a, b)", presentation.presentableText)
        assertEquals("(geometry)", presentation.locationString)
    }

    fun testSectionSeparators() {
        myFixture.configureByText("s.m", "%% First\nx = 1;\n%% Second\ny = 2;\n%%%%%%\n")
        val provider = MatlabSectionSeparatorProvider()
        val withSeparator = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiComment::class.java)
            .filter { provider.getLineMarkerInfo(it) != null }.map { it.text }
        assertEquals(listOf("%% Second"), withSeparator)  // none above the first line or for %%%% banners
    }

    fun testTodo() {
        myFixture.configureByText("t.m", "x = 1; % TODO handle empty input\n% just a note\n")
        val todos = PsiTodoSearchHelper.getInstance(project).findTodoItems(myFixture.file)
        assertEquals(1, todos.size)
    }

    fun testLiveTemplate() {
        myFixture.configureByText("t.m", "for<caret>")
        myFixture.type('\t')
        assertEquals("for i = 1:n\n    \nend", myFixture.editor.document.text)
    }

    fun testSurroundWithIf() {
        myFixture.configureByText("t.m", "a = 1;\n<selection>b = 2;\nc = 3;</selection>\n")
        val surrounder = MatlabSurroundDescriptor().surrounders.first { it.templateDescription == "if" }
        SurroundWithHandler.invoke(project, myFixture.editor, myFixture.file, surrounder)
        assertEquals("a = 1;\nif condition\n    b = 2;\n    c = 3;\nend\n", myFixture.editor.document.text)
    }

    fun testUnusedImport() {
        myFixture.addFileToProject("+geometry/Point.m", "classdef Point\nend\n")
        myFixture.addFileToProject("+geometry/distance.m", "function d = distance(a, b)\nend\n")
        myFixture.addFileToProject("+other/thing.m", "function thing()\nend\n")
        myFixture.enableInspections(MatlabUnusedImportInspection::class.java)
        myFixture.configureByText("main.m",
            "import geometry.Point <weak_warning descr=\"Unused import 'geometry.distance'\">geometry.distance</weak_warning>\n" +
                    "import <weak_warning descr=\"Unused import 'other.*'\">other.*</weak_warning>\nimport java.io.*\np = Point();\n")
        myFixture.checkHighlighting(false, false, true)
    }
}
