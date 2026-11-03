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

    /** Runs [command] in the active session, starting one if needed. [code]: what it runs, see [MatlabConsoleSession.send]. */
    fun run(command: String, interpreterPath: String? = null, code: String? = null): MatlabConsoleSession? {
        val session = activeSession() ?: newSession(interpreterPath) ?: return null
        session.send(command, code)
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
    private val tempDirectory: File by lazy { FileUtil.createTempDirectory("matlab-console", null, true) }

    /** Where run scripts and the workspace helper go; made again if something cleaned up the temp folder. */
    private val scriptDirectory: File get() = tempDirectory.also { it.mkdirs() }

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

    /**
     * The hidden command that lists the workspace: `ide_workspace__` (written next to the run scripts) prints
     * the variables between [MatlabOutputFilter.BEGIN] and [MatlabOutputFilter.END]. The first time, its folder
     * is put on the path. Errors are swallowed, so nothing of it shows in the console.
     */
    fun workspaceQuery(): String {
        val function = File(scriptDirectory, "ide_workspace__.m")
        if (!function.isFile) function.writeText(WORKSPACE_FUNCTION)
        return "try, ide_workspace__; catch, try, addpath('${quote(scriptDirectory.path)}'); ide_workspace__; " +
                "catch, fprintf('%s', char([2 4])); end, end"
    }

    private fun quote(text: String) = text.replace("'", "''")

    // Works in MATLAB and Octave. Builds everything first, so the end marker is always printed.
    private val WORKSPACE_FUNCTION = """
        function ide_workspace__()
        % Written by the IDE's MATLAB plugin: lists the workspace for its Variables view
        out = '';
        try
            vars = evalin('caller', 'whos');
            for k = 1:numel(vars)
                v = vars(k);
                value = '';
                try
                    if v.bytes <= 100000
                        value = ide_value__(evalin('caller', v.name));
                    end
                catch
                end
                sz = [sprintf('%d', v.size(1)), sprintf('x%d', v.size(2:end))];
                out = [out, v.name, char(9), sz, char(9), v.class, char(9), value, char(10)]; %#ok<AGROW>
            end
        catch
        end
        fprintf('%s%s%s', char(2), out, char(4));
        end

        function s = ide_value__(x)
        s = '';
        if (isnumeric(x) || islogical(x)) && ismatrix(x) && numel(x) <= 12
            s = mat2str(x, 5);
        elseif ischar(x) && size(x, 1) <= 1
            s = [, x, ];
        elseif isstring(x) && numel(x) == 1
            s = ['"', char(x), '"'];
        end
        s = regexprep(s, '[\t\r\n]', ' ');
        if numel(s) > 80
            s = [s(1:77), '...'];
        end
        end
        """.trimIndent() + "\n"
}

/** A session's tab: its console with a toolbar, and its variables on the right. */
class MatlabConsolePanel(private val session: MatlabConsoleSession) : SimpleToolWindowPanel(false, true) {
    init {
        val splitter = com.intellij.ui.JBSplitter(false, "matlab.console.variables", 0.7f)
        splitter.firstComponent = session.console.component
        splitter.secondComponent = MatlabVariablesView(session)
        setContent(splitter)
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
