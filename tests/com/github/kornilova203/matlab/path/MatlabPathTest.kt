package com.github.kornilova203.matlab.path

import com.github.kornilova203.matlab.settings.MatlabProjectSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Folders outside the project on the MATLAB path. */
class MatlabPathTest : BasePlatformTestCase() {
    private lateinit var lib: VirtualFile

    override fun setUp() {
        super.setUp()
        // A folder outside the project's content root (temp:///src)
        val root = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
        lib = WriteAction.computeAndWait<VirtualFile, Exception> {
            root.findChild("matlabLib")?.delete(this)
            root.createChildDirectory(this, "matlabLib")
        }
        VfsTestUtil.createFile(lib, "helper.m", "function y = helper(x)\n% HELPER doubles x\ny = 2 * x;\nend\n")
        VfsTestUtil.createFile(lib, "+tools/scale.m", "function y = scale(x, k)\ny = x * k;\nend\n")
        VfsTestUtil.createFile(lib, "LibShape.m", "classdef LibShape\n    methods\n        function a = area(obj)\n            a = 1;\n        end\n    end\nend\n")
        setPath(listOf(lib.url))
    }

    override fun tearDown() {
        try {
            setPath(emptyList())
            WriteAction.runAndWait<Exception> { lib.delete(this) }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun setPath(folders: List<String>) {
        val old = MatlabPath.roots(project)
        MatlabProjectSettings.getInstance(project).pathFolders = folders
        MatlabPath.rootsChanged(project, old)
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun resolve(code: String): String? {
        myFixture.configureByText("main.m", code)
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve() ?: return null
        val path = target.containingFile.virtualFile.path
        return (target as PsiNamedElement).name + " in " + path.substringAfter("/src/").substringAfter("/matlabLib/")
    }

    fun testFolderIsLibrary() {
        assertTrue(ProjectFileIndex.getInstance(project).isInLibrary(lib.findChild("helper.m")!!))
        assertEquals(listOf(lib), MatlabPath.roots(project))
    }

    fun testFunctionOnPath() = assertEquals("helper in helper.m", resolve("y = hel<caret>per(1);\n"))

    fun testClassOnPath() = assertEquals("LibShape in LibShape.m", resolve("s = Lib<caret>Shape();\n"))

    fun testMethodOfClassOnPath() = assertEquals("area in LibShape.m", resolve("s = LibShape();\na = s.ar<caret>ea();\n"))

    fun testPackageOnPath() = assertEquals("scale in +tools/scale.m", resolve("y = tools.sca<caret>le(1, 2);\n"))

    fun testProjectShadowsPath() {
        myFixture.addFileToProject("helper.m", "function y = helper(x)\ny = x;\nend\n")
        assertEquals("helper in helper.m", resolve("y = hel<caret>per(1);\n"))
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)!!.resolve()!!
        assertTrue(ProjectFileIndex.getInstance(project).isInContent(target.containingFile.virtualFile))
    }

    fun testCompletion() {
        myFixture.configureByText("main.m", "y = helpe<caret>\n")
        val items = myFixture.completeBasic()
        if (items == null) assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("helper"))
        else assertContainsElements(myFixture.lookupElementStrings!!, "helper")
    }

    fun testPackageCompletion() {
        myFixture.configureByText("main.m", "y = tools.<caret>\n")
        val items = myFixture.completeBasic()
        if (items == null) assertTrue(myFixture.editor.document.text.contains("tools.scale"))
        else assertContainsElements(myFixture.lookupElementStrings!!, "scale")
    }

    fun testRemovedFromPath() {
        setPath(emptyList())
        assertNull(resolve("y = hel<caret>per(1);\n"))
    }

    fun testRelativeFolderMissing() {
        setPath(listOf("does/not/exist"))
        assertEmpty(MatlabPath.roots(project))
    }
}
