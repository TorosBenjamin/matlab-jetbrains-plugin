package com.github.kornilova203.matlab.execution

import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.structure.MatlabSections
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

/** Run the whole file: from the project tree, the editor's context menu or the gutter. */
class MatlabRunConfigurationProducer : LazyRunConfigurationProducer<MatlabRunConfiguration>() {

    override fun isConfigurationFromContext(configuration: MatlabRunConfiguration, context: ConfigurationContext): Boolean {
        val file = context.location?.psiElement?.containingFile as? MatlabFile ?: return false
        return configuration.sectionLines == null && configuration.getFilePath() == file.virtualFile?.canonicalPath
    }

    override fun setupConfigurationFromContext(configuration: MatlabRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val file = sourceElement.get()?.containingFile as? MatlabFile ?: return false
        val path = file.virtualFile?.canonicalPath ?: return false
        configuration.setFilePath(path)
        configuration.name = file.virtualFile.name
        return true
    }

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(MatlabConfigurationType::class.java)
}

/** Run only the `%%` code section around the caret (or the one whose gutter icon was clicked). */
class MatlabSectionRunConfigurationProducer : LazyRunConfigurationProducer<MatlabRunConfiguration>() {

    private fun section(element: PsiElement?): Pair<String, MatlabSections.Section>? {
        val file = element?.containingFile as? MatlabFile ?: return null
        val path = file.virtualFile?.canonicalPath ?: return null
        return MatlabSections.sectionAt(file, element.textRange.startOffset)?.let { path to it }
    }

    override fun isConfigurationFromContext(configuration: MatlabRunConfiguration, context: ConfigurationContext): Boolean {
        val (path, section) = section(context.location?.psiElement) ?: return false
        return configuration.getFilePath() == path && configuration.sectionLines == section.firstLine..section.lastLine
    }

    override fun setupConfigurationFromContext(configuration: MatlabRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val (path, section) = section(sourceElement.get()) ?: return false
        configuration.setFilePath(path)
        configuration.sectionLines = section.firstLine..section.lastLine
        configuration.name = "${sourceElement.get().containingFile.name}: ${section.title}"
        return true
    }

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(MatlabConfigurationType::class.java)
}
