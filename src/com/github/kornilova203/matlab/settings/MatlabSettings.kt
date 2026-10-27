package com.github.kornilova203.matlab.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** IDE-wide MATLAB plugin settings, stored in matlab.xml. */
@Service(Service.Level.APP)
@State(name = "MatlabSettings", storages = [Storage("matlab.xml")])
class MatlabSettings : SimplePersistentStateComponent<MatlabSettings.State>(State()) {
    class State : BaseState() {
        /** Pressing Enter after `if x`, `for ...`, `function ...` inserts the matching `end`. */
        var insertEnd by property(true)
    }

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
        group("Editor") {
            row {
                checkBox("Insert 'end' automatically")
                    .bindSelected(settings::insertEnd)
                    .comment("Pressing Enter after <code>if</code>, <code>for</code>, <code>function</code>, " +
                            "<code>properties</code>… adds the matching <code>end</code> when the block isn't closed yet.")
            }
        }
    }
}
