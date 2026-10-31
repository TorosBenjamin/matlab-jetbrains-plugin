package com.github.kornilova203.matlab.settings

import com.github.kornilova203.matlab.path.MatlabPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/** IDE-wide MATLAB plugin settings, stored in matlab.xml. */
@Service(Service.Level.APP)
@State(name = "MatlabSettings", storages = [Storage("matlab.xml")])
class MatlabSettings : SimplePersistentStateComponent<MatlabSettings.State>(State()) {
    class State : BaseState() {
        /** Pressing Enter after `if x`, `for ...`, `function ...` inserts the matching `end`. */
        var insertEnd by property(true)

        /** Show warnings from MATLAB's code analyzer (mlint) in the editor. */
        var useMlint by property(true)

        /** MATLAB (or Octave) executable used to run scripts; empty means detect it. */
        var matlabPath by string()

        /** Index `<matlabroot>/toolbox/matlab`, so MATLAB's own functions open in the editor. */
        var indexMatlabToolbox by property(false)
    }

    var indexMatlabToolbox: Boolean
        get() = state.indexMatlabToolbox
        set(value) { state.indexMatlabToolbox = value }

    var useMlint: Boolean
        get() = state.useMlint
        set(value) { state.useMlint = value }

    var matlabPath: String?
        get() = state.matlabPath
        set(value) { state.matlabPath = value?.trim()?.ifEmpty { null } }

    var insertEnd: Boolean
        get() = state.insertEnd
        set(value) { state.insertEnd = value }

    companion object {
        fun getInstance(): MatlabSettings = ApplicationManager.getApplication().service()
    }
}

/** Settings | Languages & Frameworks | MATLAB */
class MatlabSettingsConfigurable(private val project: Project) : BoundConfigurable("MATLAB") {
    override fun createPanel(): DialogPanel = panel {
        val settings = MatlabSettings.getInstance()
        group("MATLAB") {
            lateinit var path: Cell<TextFieldWithBrowseButton>
            row("Executable:") {
                path = textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile().withTitle("Choose MATLAB or Octave Executable"))
                    .bindText({ settings.matlabPath.orEmpty() }, { settings.matlabPath = it })
                    .align(AlignX.FILL)
                    .comment(detectedComment())
                button("Detect") {
                    MatlabInstallations.detect().firstOrNull()?.let { path.component.text = it }
                }
            }
        }
        group("MATLAB Path") {
            if (!project.isDefault) {
                row {
                    textArea()
                        .label("Folders of this project's code outside the project:", LabelPosition.TOP)
                        .rows(4)
                        .align(AlignX.FILL)
                        .bindText({ MatlabProjectSettings.getInstance(project).pathFolders.joinToString("\n") },
                            { MatlabProjectSettings.getInstance(project).pathFolders = it.lines() })
                        .comment("One folder per line, like <code>addpath</code> in MATLAB; relative paths start at the " +
                                "project folder. Their functions, classes and packages are found by go to definition " +
                                "and completion, and are listed under External Libraries.")
                }
            }
            row {
                val toolbox = MatlabPath.toolboxFolder()
                checkBox("Include MATLAB's own code (toolbox/matlab)")
                    .bindSelected(settings::indexMatlabToolbox)
                    .comment(if (toolbox != null) "Opens functions such as <code>linspace</code> in <code>$toolbox</code>. " +
                            "Indexing it takes a while the first time."
                    else "Needs a MATLAB installation; none was found.")
            }
        }
        group("Code Analysis") {
            row {
                checkBox("Show MATLAB code analyzer (mlint) warnings")
                    .bindSelected(settings::useMlint)
                    .comment(MatlabInstallations.findMlint()?.let { "Uses <code>$it</code>. It runs without starting MATLAB." }
                        ?: "Needs a MATLAB installation (not Octave); none with mlint was found.")
            }
        }
        group("Editor") {
            row {
                checkBox("Insert 'end' automatically")
                    .bindSelected(settings::insertEnd)
                    .comment("Pressing Enter after <code>if</code>, <code>for</code>, <code>function</code>, " +
                            "<code>properties</code>… adds the matching <code>end</code> when the block isn't closed yet.")
            }
        }
    }

    override fun apply() {
        // The executable and toolbox settings are IDE-wide, so other open projects' paths may change too
        MatlabPath.changeForAllProjects { super.apply() }
    }

    private fun detectedComment(): String {
        val found = MatlabInstallations.detect()
        return if (found.isEmpty()) "Used to run scripts. MATLAB wasn't found automatically; choose its <code>bin/matlab</code>."
        else "Used to run scripts; run configurations can override it. Leave empty to use the detected " +
                "<code>${found.first()}</code>" + (if (found.size > 1) " (${found.size} installations found)" else "") + "."
    }
}
