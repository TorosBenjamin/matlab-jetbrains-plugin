package com.github.kornilova203.matlab.execution.console

import com.github.kornilova203.matlab.execution.debugger.MatlabBreakpointSpec
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

    /** A running session that waits at its prompt, to ask things of (class members for completion). */
    fun idleSession(): MatlabConsoleSession? = sessions.firstOrNull { it.isAlive && it.isIdle }

    /** Pause on Errors changed: every session gets it. */
    fun breakpointsChanged() = sessions.forEach { it.breakpointsChanged() }

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
    fun workspaceQuery(): String = hiddenCall("ide_workspace__", WORKSPACE_TAG)

    /**
     * The hidden command that lists the fields, properties and methods of [expression] (a variable, maybe with
     * fields and indexes: `s.data(2)`), answered under the tag `m<id>`.
     */
    fun membersQuery(id: Int, expression: String): String =
        hiddenCall("ide_members__('$id', '${quote(expression)}')", "m$id")

    /**
     * The hidden command that makes MATLAB's breakpoints exactly [breakpoints] (and `dbstop if error` if
     * [pauseOnErrors]); answered under `bp` with the ones MATLAB refused, one a line: file, line, message.
     */
    fun breakpointsQuery(breakpoints: List<MatlabBreakpointSpec>, pauseOnErrors: Boolean): String {
        val list = File(scriptDirectory, "ide_breakpoints_" + ++breakpointLists + ".txt")
        list.writeText(breakpoints.joinToString("") { it.toLine() })
        return hiddenCall("ide_breakpoints__('${quote(list.path)}', ${if (pauseOnErrors) "true" else "false"})", BREAKPOINTS_TAG)
    }
    private var breakpointLists = 0

    /** The call stack while MATLAB is stopped in the debugger, under `st`: file, function, line; innermost first. */
    fun stackQuery(): String = hiddenCall("ide_stack__", STACK_TAG)

    /** Evaluates [expression] where MATLAB is stopped (or in the workspace), under `e<id>`: class and size, then the value. */
    fun evalQuery(id: Int, expression: String): String =
        hiddenCall("ide_eval__('$id', '${quote(expression.replace('\n', ' '))}')", "e$id")

    /** Lists the public properties and methods of the classes [names], under `cls<id>` (see [CLASS_FUNCTION]). */
    fun classQuery(id: Int, names: List<String>): String = hiddenCall("ide_class__('$id', ${cellOf(names)})", "cls$id")

    /** The same for `matlab -batch`: puts the helpers' folder on the path first. */
    fun classBatchCommand(names: List<String>): String {
        val directory = scriptDirectory
        writeHelpers(directory)
        return "addpath('${quote(directory.path)}'); ide_class__('0', ${cellOf(names)})"
    }

    private fun cellOf(names: List<String>) = names.joinToString(", ", "{", "}") { "'" + quote(it) + "'" }

    private fun writeHelpers(directory: File) {
        for ((name, code) in HELPERS) {
            val file = File(directory, "$name.m")
            if (!file.isFile) file.writeText(code)
        }
    }

    /** Calls a helper function, putting their folder on the path if needed; on error the answer is empty. */
    private fun hiddenCall(call: String, tag: String): String {
        val directory = scriptDirectory
        writeHelpers(directory)
        return "try, $call; catch, try, addpath('${quote(directory.path)}'); $call; " +
                "catch, fprintf('%s$tag\\n%s', char(2), char(4)); end, end"
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
        fprintf('%sws\n%s%s', char(2), out, char(4));
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

    // Struct fields, table variables, object properties and methods
    private val MEMBERS_FUNCTION = """
        function ide_members__(id, expression)
        % Written by the IDE's MATLAB plugin: lists what can follow a dot, for completion
        out = '';
        try
            x = evalin('caller', expression);
            names = {};
            kinds = {};
            if isstruct(x)
                f = fieldnames(x);
                names = [names; f(:)];
                kinds = [kinds; repmat({'f'}, numel(f), 1)];
            end
            try
                if istable(x) || istimetable(x)
                    f = [x.Properties.VariableNames(:); {'Properties'}];
                    names = [names; f];
                    kinds = [kinds; repmat({'f'}, numel(f), 1)];
                end
            catch
            end
            if isobject(x) || isjava(x)
                try
                    p = properties(x);
                    names = [names; p(:)];
                    kinds = [kinds; repmat({'p'}, numel(p), 1)];
                catch
                end
                try
                    m = methods(x);
                    names = [names; m(:)];
                    kinds = [kinds; repmat({'m'}, numel(m), 1)];
                catch
                end
            end
            for k = 1:numel(names)
                out = [out, kinds{k}, char(9), names{k}, char(10)]; %#ok<AGROW>
            end
        catch
        end
        fprintf('%sm%s\n%s%s', char(2), id, out, char(4));
        end
        """.trimIndent() + "\n"

    private val BREAKPOINTS_FUNCTION = """
        function ide_breakpoints__(listFile, pauseOnErrors)
        % Written by the IDE's MATLAB plugin: sets the IDE's breakpoints (file, line, condition; one a line)
        out = '';
        try
            evalc('dbclear all');
            octave = exist('OCTAVE_VERSION', 'builtin') ~= 0;
            lines = regexp(fileread(listFile), '\n', 'split');
            for k = 1:numel(lines)
                parts = regexp(lines{k}, '\t', 'split');
                if numel(parts) < 2 || isempty(parts{1})
                    continue
                end
                where = parts{1};
                if octave
                    [~, where] = fileparts(where);
                end
                try
                    if numel(parts) >= 3 && ~isempty(parts{3})
                        evalc('dbstop(''in'', where, ''at'', parts{2}, ''if'', parts{3})');
                    else
                        evalc('dbstop(''in'', where, ''at'', parts{2})');
                    end
                catch err
                    out = [out, parts{1}, char(9), parts{2}, char(9), regexprep(err.message, '\s+', ' '), char(10)]; %#ok<AGROW>
                end
            end
            if pauseOnErrors
                evalc('dbstop if error');
            end
        catch err
            out = [out, char(9), char(9), regexprep(err.message, '\s+', ' '), char(10)];
        end
        fprintf('%sbp\n%s%s', char(2), out, char(4));
        end
        """.trimIndent() + "\n"

    private val STACK_FUNCTION = """
        function ide_stack__()
        % Written by the IDE's MATLAB plugin: where MATLAB is stopped, for the IDE's debugger
        out = '';
        try
            s = dbstack('-completenames');
            for k = 2:numel(s)
                out = [out, s(k).file, char(9), s(k).name, char(9), sprintf('%d', s(k).line), char(10)]; %#ok<AGROW>
            end
        catch
        end
        fprintf('%sst\n%s%s', char(2), out, char(4));
        end
        """.trimIndent() + "\n"

    private val EVAL_FUNCTION = """
        function ide_eval__(id, expression)
        % Written by the IDE's MATLAB plugin: evaluates an expression for the IDE's debugger
        try
            value = evalin('caller', expression);
            sz = size(value);
            text = strtrim(evalc('disp(value)'));
            if numel(text) > 20000
                text = [text(1:20000), '...'];
            end
            out = [class(value), char(9), sprintf('%d', sz(1)), sprintf('x%d', sz(2:end)), char(10), text];
        catch err
            out = ['error', char(9), char(10), err.message];
        end
        fprintf('%se%s\n%s%s', char(2), id, out, char(4));
        end
        """.trimIndent() + "\n"

    /**
     * Describes classes for completion: for each name a line `c<tab>name`, then `p<tab>property` and
     * `m<tab>method` for what's public and not hidden (not what every handle class has), or `x` if it isn't a class.
     */
    private val CLASS_FUNCTION = """
        function ide_class__(id, names)
        % Written by the IDE's MATLAB plugin: lists the members of classes, for completion
        out = '';
        for k = 1:numel(names)
            out = [out, 'c', char(9), names{k}, char(10)]; %#ok<AGROW>
            try
                mc = meta.class.fromName(names{k});
                if isempty(mc)
                    out = [out, 'x', char(9), char(10)]; %#ok<AGROW>
                    continue
                end
                props = mc.PropertyList;
                for i = 1:numel(props)
                    p = props(i);
                    if ~p.Hidden && ischar(p.GetAccess) && strcmp(p.GetAccess, 'public')
                        out = [out, 'p', char(9), p.Name, char(10)]; %#ok<AGROW>
                    end
                end
                short = regexprep(mc.Name, '.*\.', '');
                meths = mc.MethodList;
                for i = 1:numel(meths)
                    m = meths(i);
                    owner = m.DefiningClass.Name;
                    if m.Hidden || ~ischar(m.Access) || ~strcmp(m.Access, 'public') || strcmp(m.Name, short) || ...
                            strcmp(owner, 'handle') || strncmp(owner, 'matlab.mixin.', 13)
                        continue
                    end
                    out = [out, 'm', char(9), m.Name, char(10)]; %#ok<AGROW>
                end
            catch
                out = [out, 'x', char(9), char(10)]; %#ok<AGROW>
            end
        end
        fprintf('%scls%s\n%s%s', char(2), id, out, char(4));
        end
        """.trimIndent() + "\n"

    const val WORKSPACE_TAG = "ws"
    const val BREAKPOINTS_TAG = "bp"
    const val STACK_TAG = "st"
    private val HELPERS by lazy {
        listOf("ide_workspace__" to WORKSPACE_FUNCTION, "ide_members__" to MEMBERS_FUNCTION,
            "ide_breakpoints__" to BREAKPOINTS_FUNCTION, "ide_stack__" to STACK_FUNCTION, "ide_eval__" to EVAL_FUNCTION,
            "ide_class__" to CLASS_FUNCTION)
    }
}

/** A session's tab: its console with a toolbar, and its variables on the right. */
class MatlabConsolePanel(private val session: MatlabConsoleSession) : SimpleToolWindowPanel(false, true) {
    init {
        // Console and variables on one surface: the divider has the console's color, so no dark line between them
        val splitter = object : com.intellij.ui.OnePixelSplitter(false, "matlab.console.variables", 0.7f) {
            override fun createDivider() = super.createDivider().also { it.background = MatlabVariablesView.BACKGROUND }
        }
        splitter.firstComponent = session.console.component
        splitter.secondComponent = MatlabVariablesView(session)
        setContent(splitter)
        val group = DefaultActionGroup()
        group.add(InterruptAction())
        group.add(ActionManager.getInstance().getAction(NEW_CONSOLE_ACTION))
        group.add(PauseOnErrorsAction())
        group.addSeparator()
        group.addAll(*session.console.createConsoleActions())
        val toolbar = ActionManager.getInstance().createActionToolbar("MatlabConsole", group, false)
        toolbar.targetComponent = session.console.component
        setToolbar(toolbar.component)
    }

    private inner class InterruptAction : AnAction("Interrupt", "Stop the running command (Ctrl+C)", AllIcons.Actions.Suspend), DumbAware {
        init {
            // Ctrl+C in the console, as in MATLAB; with text selected it copies (this action is off then)
            registerCustomShortcutSet(com.intellij.openapi.actionSystem.CustomShortcutSet(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK)),
                session.console.component)
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            val console = session.console
            val selected = console.historyViewer.selectionModel.hasSelection() || console.consoleEditor.selectionModel.hasSelection()
            // Only while MATLAB is busy: at its prompt there's nothing to stop
            e.presentation.isEnabled = session.isAlive && !session.isIdle && !selected
        }

        override fun actionPerformed(e: AnActionEvent) {
            if (!session.interrupt()) {
                NotificationGroupManager.getInstance().getNotificationGroup("MATLAB")
                    .createNotification("This MATLAB can't be interrupted; close the tab to stop it.", NotificationType.WARNING)
                    .notify(session.project)
            }
        }
    }

    /** `dbstop if error`: MATLAB stops in the debugger where an error isn't caught. */
    private inner class PauseOnErrorsAction : com.intellij.openapi.actionSystem.ToggleAction(
        "Pause on Errors", "Stop in the debugger where an error isn't caught (dbstop if error)",
        AllIcons.Debugger.Db_exception_breakpoint), DumbAware {
        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
        override fun isSelected(e: AnActionEvent) = com.github.kornilova203.matlab.settings.MatlabSettings.getInstance().pauseOnErrors
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            com.github.kornilova203.matlab.settings.MatlabSettings.getInstance().pauseOnErrors = state
            MatlabConsoleManager.getInstance(session.project).breakpointsChanged()
        }
    }

    companion object {
        const val NEW_CONSOLE_ACTION = "Matlab.NewConsole"
    }
}
