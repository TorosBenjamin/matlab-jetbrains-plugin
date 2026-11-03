package com.github.kornilova203.matlab

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Warns when the original Matlab Support plugin is enabled too. Both register the "Matlab" language and
 * the .m file type, so the IDE mixes parts of the two: highlighting only partly works, navigation and
 * completion behave oddly.
 */
class MatlabConflictCheck : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!isEnabled(ORIGINAL_PLUGIN)) return
        NotificationGroupManager.getInstance().getNotificationGroup("MATLAB")
            .createNotification("Two MATLAB plugins are enabled",
                "Both <i>Matlab Support</i> and <i>Unofficial MATLAB Support</i> handle .m files, so highlighting and " +
                        "navigation only partly work. Disable or uninstall <i>Matlab Support</i> and restart the IDE.",
                NotificationType.WARNING)
            .addAction(NotificationAction.createSimpleExpiring("Open plugins") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Plugins")
            })
            .notify(project)
    }

    companion object {
        /** The plugin this one continues: github.com/kornilova203/matlab-IntelliJ-plugin */
        val ORIGINAL_PLUGIN: PluginId = PluginId.getId("MatlabSupport")

        fun isEnabled(id: PluginId): Boolean = PluginManagerCore.isPluginInstalled(id) && !PluginManagerCore.isDisabled(id)
    }
}
