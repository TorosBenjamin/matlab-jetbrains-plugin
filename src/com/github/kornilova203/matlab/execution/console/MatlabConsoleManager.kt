package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.icons.AllIcons
import java.io.File

/**
 * The project's MATLAB sessions, one tab each in the MATLAB tool window. Runs go to the selected tab's
 * session; if there's none, one is started.
 */
@Service(Service.Level.PROJECT)
class MatlabConsoleManager(private val project: Project) : Disposable {
    private val sessions = ArrayList<MatlabConsoleSession>()
    private var counter = 0

    private fun toolWindow(): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)

    /** The session runs go to: the selected tab's, else the most recent one still running. */
    fun activeSession(): MatlabConsoleSession? {
        val selected = toolWindow()?.contentManager?.selectedContent?.getUserData(SESSION)
        if (selected != null && selected.isAlive) return selected
        return sessions.lastOrNull { it.isAlive }
    }

    /** Starts a new session in a new tab. Null (after telling the user) if MATLAB can't be started. */
    fun newSession(interpreterPath: String? = null): MatlabConsoleSession? {
        val interpreter = MatlabInstallations.resolveInterpreter(interpreterPath)
        if (interpreter == null) {
            notifyError("MATLAB wasn't found. Choose its executable in Settings | Languages & Frameworks | MATLAB.")
            return null
        }
        val name = "MATLAB " + ++counter
        val session = try {
            // Starts in the project's folder, if it has one on disk
            MatlabConsoleSession(project, name, interpreter, project.basePath?.takeIf { File(it).isDirectory })
        } catch (e: Exception) {
            notifyError("Couldn't start $name: ${e.message}")
            return null
        }
        Disposer.register(this, session)
        sessions += session
        Disposer.register(session) { sessions.remove(session) }
        val toolWindow = toolWindow()
        if (toolWindow != null) {
            val content = ContentFactory.getInstance().createContent(MatlabConsolePanel(session), name, false)
            content.putUserData(SESSION, session)
            content.setDisposer(session)
            toolWindow.contentManager.addContent(content)
            toolWindow.contentManager.setSelectedContent(content)
            toolWindow.activate(null, false)
        }
        return session
    }

    /** Runs [command] in the active session, starting one if needed. */
    fun run(command: String, interpreterPath: String? = null): MatlabConsoleSession? {
        val session = activeSession() ?: newSession(interpreterPath) ?: return null
        session.send(command)
        toolWindow()?.let { window ->
            window.contentManager.contents.firstOrNull { it.getUserData(SESSION) == session }
                ?.let { window.contentManager.setSelectedContent(it) }
            window.show()
        }
        return session
    }

    private fun notifyError(message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("MATLAB")
            .createNotification(message, NotificationType.ERROR).notify(project)
    }

    override fun dispose() {}

    companion object {
        const val TOOL_WINDOW_ID = "MATLAB"
        val SESSION = Key.create<MatlabConsoleSession>("matlab.console.session")

        fun getInstance(project: Project): MatlabConsoleManager = project.service()
    }
}

/** What to send to a session to run a file, a section or a selection. */
object MatlabConsoleCode {
    private var scriptCounter = 0
    private val scriptDirectory: File by lazy { FileUtil.createTempDirectory("matlab-console", null, true) }

    /** `run('file.m')`: MATLAB changes to the file's folder while it runs. */
    fun runFile(path: String): String = "run('${quote(path)}')"

    /**
     * Runs [code] (a section or a selection, maybe several lines) in the session's current folder: the
     * lines become a script in a temporary folder that's added to the path, called by name.
     */
    fun runCode(code: String): String {
        val lines = code.lines().dropLastWhile { it.isBlank() }
        if (lines.size == 1 && !lines[0].trimStart().startsWith("%")) return lines[0].trim()
        val name = "ide_run_" + ++scriptCounter
        File(scriptDirectory, "$name.m").writeText(lines.joinToString("\n") + "\n")
        return "addpath('${quote(scriptDirectory.path)}'); $name"
    }

    private fun quote(text: String) = text.replace("'", "''")
}

/** A session's tab: its console with a toolbar. */
class MatlabConsolePanel(private val session: MatlabConsoleSession) : SimpleToolWindowPanel(false, true) {
    init {
        setContent(session.console.component)
        val group = DefaultActionGroup()
        group.add(InterruptAction())
        group.add(ActionManager.getInstance().getAction(NEW_CONSOLE_ACTION))
        group.addSeparator()
        group.addAll(*session.console.createConsoleActions())
        val toolbar = ActionManager.getInstance().createActionToolbar("MatlabConsole", group, false)
        toolbar.targetComponent = session.console.component
        setToolbar(toolbar.component)
    }

    private inner class InterruptAction : AnAction("Interrupt", "Stop the running command (Ctrl+C)", AllIcons.Actions.Suspend), DumbAware {
        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = session.isAlive
        }

        override fun actionPerformed(e: AnActionEvent) {
            if (!session.interrupt()) {
                NotificationGroupManager.getInstance().getNotificationGroup("MATLAB")
                    .createNotification("This MATLAB can't be interrupted; close the tab to stop it.", NotificationType.WARNING)
                    .notify(session.project)
            }
        }
    }

    companion object {
        const val NEW_CONSOLE_ACTION = "Matlab.NewConsole"
    }
}
