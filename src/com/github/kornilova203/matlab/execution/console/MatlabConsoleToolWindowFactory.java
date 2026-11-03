package com.github.kornilova203.matlab.execution.console;

import com.github.kornilova203.matlab.Icons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * View | Tool Windows | MATLAB: empty until a script is run or a console is opened.
 * In Java, so only createToolWindowContent is overridden (Kotlin would also generate overrides of the
 * interface's other default methods, some of which are internal API).
 */
public final class MatlabConsoleToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AnAction newConsole = ActionManager.getInstance().getAction(MatlabConsolePanel.NEW_CONSOLE_ACTION);
        if (newConsole != null) toolWindow.setTitleActions(List.of(newConsole));
        if (toolWindow instanceof ToolWindowEx) {
            ((ToolWindowEx) toolWindow).getEmptyText().setText("Run a MATLAB script, or open a console with +");
        }
        toolWindow.setIcon(Icons.INSTANCE.getMatlab());
    }
}
