package com.github.kornilova203.matlab.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
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

        /** MATLAB (or Octave) executable used to run scripts; empty means detect it. */
        var matlabPath by string()
    }

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
class MatlabSettingsConfigurable : BoundConfigurable("MATLAB") {
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
        group("Editor") {
            row {
                checkBox("Insert 'end' automatically")
                    .bindSelected(settings::insertEnd)
                    .comment("Pressing Enter after <code>if</code>, <code>for</code>, <code>function</code>, " +
                            "<code>properties</code>… adds the matching <code>end</code> when the block isn't closed yet.")
            }
        }
    }

    private fun detectedComment(): String {
        val found = MatlabInstallations.detect()
        return if (found.isEmpty()) "Used to run scripts. MATLAB wasn't found automatically; choose its <code>bin/matlab</code>."
        else "Used to run scripts; run configurations can override it. Leave empty to use the detected " +
                "<code>${found.first()}</code>" + (if (found.size > 1) " (${found.size} installations found)" else "") + "."
    }
}
