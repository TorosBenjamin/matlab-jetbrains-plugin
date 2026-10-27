package com.github.kornilova203.matlab.inspections

import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.github.kornilova203.matlab.settings.MatlabSettings
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Shows warnings from MATLAB's code analyzer. `mlint` is a standalone program in the MATLAB installation,
 * so this works without starting MATLAB. The current (possibly unsaved) text is checked from a temporary
 * copy with the same file name, since some checks compare the function name with the file name.
 */
class MatlabMlintAnnotator : ExternalAnnotator<MatlabMlintAnnotator.Input, List<MatlabMlintAnnotator.Problem>>() {
    class Input(val fileName: String, val text: String, val mlint: String)
    data class Problem(val line: Int, val startColumn: Int, val endColumn: Int, val id: String?, val message: String)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? = collect(file)
    override fun collectInformation(file: PsiFile): Input? = collect(file)

    private fun collect(file: PsiFile): Input? {
        if (file !is MatlabFile || !MatlabSettings.getInstance().useMlint) return null
        val mlint = MatlabInstallations.findMlint() ?: return null
        return Input(file.name, file.text, mlint)
    }

    override fun doAnnotate(input: Input): List<Problem> {
        val dir = FileUtil.createTempDirectory("matlab-mlint", null, true)
        try {
            val file = File(dir, input.fileName)
            file.writeText(input.text, StandardCharsets.UTF_8)
            val output = CapturingProcessHandler(GeneralCommandLine(input.mlint, "-id", file.path))
                .runProcess(TIMEOUT_MS)
            if (output.isTimeout) {
                LOG.info("mlint timed out on ${input.fileName}")
                return emptyList()
            }
            return parse(output.stdout + "\n" + output.stderr)
        } finally {
            FileUtil.delete(dir)
        }
    }

    override fun apply(file: PsiFile, problems: List<Problem>, holder: AnnotationHolder) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        // The plugin's own unused-variable check already greys these out and offers a fix.
        val ownUnusedCheck = InspectionProjectProfileManager.getInstance(file.project).currentProfile
            .isToolEnabled(HighlightDisplayKey.find("MatlabUnusedVariable"), file)
        for (problem in problems) {
            if (ownUnusedCheck && problem.id in UNUSED_IDS) continue
            val range = rangeOf(problem, document) ?: continue
            val builder = holder.newAnnotation(HighlightSeverity.WARNING, problem.message)
                .range(range)
                .tooltip("${problem.message}" + (problem.id?.let { " <i>(mlint $it)</i>" } ?: ""))
            if (problem.id != null) builder.withFix(SuppressFix(problem.id, problem.line))
            builder.create()
        }
    }

    private fun rangeOf(problem: Problem, document: Document): TextRange? {
        val line = problem.line - 1
        if (line < 0 || line >= document.lineCount) return null
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val start = (lineStart + problem.startColumn - 1).coerceIn(lineStart, lineEnd)
        val end = (lineStart + problem.endColumn).coerceIn(start, lineEnd)
        // Some messages point at a whole line or past its end; highlight the line then.
        return if (end > start) TextRange(start, end) else TextRange(lineStart, lineEnd).takeIf { !it.isEmpty }
    }

    /** Adds MATLAB's `%#ok<ID>` pragma to the line, which tells mlint (and MATLAB's editor) to ignore it. */
    private class SuppressFix(private val id: String, private val line: Int) : IntentionAction {
        override fun getText() = "Suppress '$id' on this line (%#ok)"
        override fun getFamilyName() = "Suppress MATLAB code analyzer warning"
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true
        override fun startInWriteAction() = true

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            file ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
            val lineStart = document.getLineStartOffset(line - 1)
            val lineEnd = document.getLineEndOffset(line - 1)
            val text = document.charsSequence.subSequence(lineStart, lineEnd).toString()
            val existing = Regex("%#ok<([^>]*)>").find(text)
            when {
                // Already has a pragma: add the ID to it, %#ok<A,B>
                existing != null -> document.insertString(lineStart + existing.groups[1]!!.range.last + 1,
                    if (existing.groupValues[1].isEmpty()) id else ",$id")
                else -> {
                    // The pragma must come first in the line's comment.
                    val comment = (lineStart until lineEnd).firstOrNull { file.findElementAt(it)?.elementType == MatlabTypes.COMMENT }
                    if (comment != null) document.insertString(comment, "%#ok<$id> ")
                    else document.insertString(lineEnd, " %#ok<$id>")
                }
            }
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    companion object {
        private val LOG = logger<MatlabMlintAnnotator>()
        private const val TIMEOUT_MS = 15_000

        /** mlint's unused variable/input warnings, covered by MatlabUnusedVariableInspection. */
        private val UNUSED_IDS = setOf("NASGU", "INUSD", "INUSL")

        // L 12 (C 5-9): NASGU: The value assigned here to 'x' appears to be unused.
        // L 3 (C 1): Terminate statement with semicolon to suppress output.
        private val LINE = Regex("""^L (\d+) \(C (\d+)(?:-(\d+))?\):\s*(?:([A-Za-z][A-Za-z0-9_]*):\s*)?(?:\d+\s*:\s*)?(.*)$""")

        fun parse(output: String): List<Problem> = output.lineSequence().mapNotNull { line ->
            val m = LINE.find(line.trim()) ?: return@mapNotNull null
            val (lineNo, start, end, id, message) = m.destructured
            Problem(lineNo.toInt(), start.toInt(), (end.ifEmpty { start }).toInt(), id.ifEmpty { null }, message.trim())
        }.toList()
    }
}
