package com.github.kornilova203.matlab.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/** MATLAB settings of one project, stored in .idea/matlab.xml. */
@Service(Service.Level.PROJECT)
@State(name = "MatlabProjectSettings", storages = [Storage("matlab.xml")])
class MatlabProjectSettings : SimplePersistentStateComponent<MatlabProjectSettings.State>(State()) {
    class State : BaseState() {
        /** Folders outside the project that the code uses, like MATLAB's addpath. */
        var pathFolders by list<String>()
    }

    /** Absolute paths, paths relative to the project folder, or VFS URLs. */
    var pathFolders: List<String>
        get() = state.pathFolders.toList()
        set(value) {
            state.pathFolders = value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        }

    companion object {
        fun getInstance(project: Project): MatlabProjectSettings = project.service()
    }
}
