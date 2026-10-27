package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.Icons
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

class MatlabConfigurationType
    : SimpleConfigurationType("MatlabApplication", "Matlab", null, NotNullLazyValue.createConstantValue(Icons.Matlab)) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return MatlabRunConfiguration(project, this)
    }
}