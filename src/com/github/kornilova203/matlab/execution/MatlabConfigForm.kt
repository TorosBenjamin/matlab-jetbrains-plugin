package com.github.kornilova203.matlab.execution

import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.MacroAwareTextBrowseFolderListener
import com.intellij.ui.RawCommandLineEditor
import java.awt.BorderLayout

// The no-arg constructor is deprecated in 2026.x, but its replacement doesn't exist in 2025.2.
// The parent constructor calls addComponents(), so fields are initialized there, not in declarations.
@Suppress("DEPRECATION")
class MatlabConfigForm : CommonProgramParametersPanel() {
    private lateinit var interpreterOptionsComponent: LabeledComponent<RawCommandLineEditor>
    private lateinit var interpreterPathField: TextFieldWithBrowseButton
    private lateinit var filePathField: TextFieldWithBrowseButton

    private fun fileField(title: String): TextFieldWithBrowseButton {
        val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(title)
        return TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(MacroAwareTextBrowseFolderListener(descriptor, project))
        }
    }

    private fun <T : javax.swing.JComponent> labeled(component: T, label: String): LabeledComponent<T> =
        LabeledComponent.create(component, label).apply { labelLocation = BorderLayout.WEST }

    override fun addComponents() {
        filePathField = fileField("Choose MATLAB Script")
        interpreterPathField = fileField("Choose MATLAB or Octave Executable")
        interpreterOptionsComponent = labeled(RawCommandLineEditor(), "Interpreter options:")

        add(labeled(filePathField, "File:"))
        add(labeled(interpreterPathField, "Interpreter path:"))
        add(interpreterOptionsComponent)

        super.addComponents()
        // A script run via run('file.m') can't receive command-line arguments.
        myProgramParametersComponent.isVisible = false
    }

    fun resetForm(configuration: MatlabRunConfiguration) {
        interpreterOptionsComponent.component.text = configuration.getInterpreterOptions()
        interpreterPathField.text = configuration.getInterpreterPath() ?: ""
        filePathField.text = configuration.getFilePath() ?: ""
    }

    fun applyToForm(configuration: MatlabRunConfiguration) {
        configuration.setInterpreterOptions(interpreterOptionsComponent.component.text)
        configuration.setInterpreterPath(interpreterPathField.text)
        configuration.setFilePath(filePathField.text)
    }
}
