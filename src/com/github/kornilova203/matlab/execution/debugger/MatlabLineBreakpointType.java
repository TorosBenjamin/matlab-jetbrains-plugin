package com.github.kornilova203.matlab.execution.debugger;

import com.github.kornilova203.matlab.MatlabFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Breakpoints in .m files (click the gutter). They're always on: the MATLAB console stops at them whenever the
 * code runs, as in MATLAB. Conditions become {@code dbstop ... if condition}. Written in Java for the raw
 * properties type the platform's API uses.
 */
@SuppressWarnings("rawtypes")
public final class MatlabLineBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
    public static final String ID = "matlab-line";

    public MatlabLineBreakpointType() {
        super(ID, "MATLAB Breakpoints");
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        if (!MatlabFileType.INSTANCE.equals(file.getFileType())) return false;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null || line < 0 || line >= document.getLineCount()) return false;
        return isCode(document.getText(new com.intellij.openapi.util.TextRange(
            document.getLineStartOffset(line), document.getLineEndOffset(line))));
    }

    /** MATLAB only stops on lines with code: not blank lines or comments. */
    public static boolean isCode(@NotNull String line) {
        String text = line.trim();
        return !text.isEmpty() && !text.startsWith("%") && !text.startsWith("...");
    }

    /** Conditions are MATLAB, with highlighting and completion. */
    @Override
    public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint,
                                                                 @NotNull Project project) {
        return new MatlabDebuggerEditorsProvider(null);
    }

    @Override
    public @Nullable XBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return null;
    }
}
