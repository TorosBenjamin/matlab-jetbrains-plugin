package com.github.kornilova203.matlab.path

import com.github.kornilova203.matlab.Icons
import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.github.kornilova203.matlab.settings.MatlabProjectSettings
import com.github.kornilova203.matlab.settings.MatlabSettings
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File
import javax.swing.Icon

/**
 * Folders outside the project that MATLAB code uses (what `addpath` does in MATLAB), plus MATLAB's own
 * `toolbox/matlab` if that setting is on. The IDE indexes them as a library, so their functions, classes
 * and packages resolve and complete like the project's own, but aren't inspected or refactored.
 */
object MatlabPath {
    const val LIBRARY_NAME = "MATLAB Path"

    /** The folders as written in the settings, plus the toolbox folder when it's enabled and found. */
    fun folders(project: Project): List<String> {
        val folders = ArrayList<String>()
        if (!project.isDefault) folders += MatlabProjectSettings.getInstance(project).pathFolders
        if (MatlabSettings.getInstance().indexMatlabToolbox) toolboxFolder()?.let { folders += it }
        return folders
    }

    /** Existing folders of [folders]. */
    fun roots(project: Project): List<VirtualFile> = folders(project).mapNotNull { find(it, project) }.distinct()

    /** `<matlabroot>/toolbox/matlab`: the source of MATLAB's own functions written in MATLAB. */
    fun toolboxFolder(): String? =
        MatlabInstallations.findMatlabRoot()?.let { File(it, "toolbox/matlab") }?.takeIf { it.isDirectory }?.path

    private fun find(folder: String, project: Project, refresh: Boolean = false): VirtualFile? {
        val file = if (folder.contains("://")) {
            VirtualFileManager.getInstance().findFileByUrl(folder)
        } else {
            val path = FileUtil.toSystemIndependentName(folder)
            val absolute = if (File(path).isAbsolute || project.basePath == null) path else "${project.basePath}/$path"
            // A refresh can't run inside a read action, where resolving and indexing ask for the roots.
            if (refresh) LocalFileSystem.getInstance().refreshAndFindFileByPath(absolute)
            else LocalFileSystem.getInstance().findFileByPath(absolute)
        }
        return file?.takeIf { it.isDirectory }
    }

    /**
     * Re-indexes after the settings changed. [oldRoots] are the roots before the change, from [roots].
     */
    fun rootsChanged(project: Project, oldRoots: List<VirtualFile>) {
        // Folders just typed into the settings may not be known to the IDE yet
        folders(project).forEach { find(it, project, refresh = true) }
        val newRoots = roots(project)
        if (newRoots == oldRoots) return
        ApplicationManager.getApplication().runWriteAction {
            AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(project, LIBRARY_NAME, oldRoots, newRoots, LIBRARY_NAME)
        }
    }

    /** Runs [change] to an IDE-wide setting that affects the path, then re-indexes every open project. */
    fun changeForAllProjects(change: () -> Unit) {
        val projects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        val old = projects.associateWith { roots(it) }
        change()
        for ((project, roots) in old) rootsChanged(project, roots)
    }
}

class MatlabPathLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val roots = MatlabPath.roots(project)
        return if (roots.isEmpty()) emptyList() else listOf(MatlabPathLibrary(roots))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> = MatlabPath.roots(project)
}

/** Shown under External Libraries in the Project view. */
class MatlabPathLibrary(private val roots: List<VirtualFile>) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = roots
    override fun equals(other: Any?): Boolean = other is MatlabPathLibrary && other.roots == roots
    override fun hashCode(): Int = roots.hashCode()
    override fun getPresentableText(): String = MatlabPath.LIBRARY_NAME
    override fun getIcon(unused: Boolean): Icon = Icons.Matlab
}
