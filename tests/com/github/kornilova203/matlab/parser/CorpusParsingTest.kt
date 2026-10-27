package com.github.kornilova203.matlab.parser

import com.github.kornilova203.matlab.MatlabLanguage
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Parses every .m file under a folder and reports parse errors. Skipped unless a folder is given:
 *
 *     ./gradlew test --tests '*CorpusParsingTest' -PmatlabCorpus=/path/to/matlab/code
 */
class CorpusParsingTest : BasePlatformTestCase() {
    fun testCorpus() {
        val root = System.getProperty("matlab.corpus").orEmpty()
        if (root.isBlank()) return
        val files = File(root).walkTopDown().filter { it.isFile && it.extension == "m" }.toList()
        val factory = PsiFileFactory.getInstance(project)
        val errorsByMessage = HashMap<String, Int>()
        val failedFiles = ArrayList<Pair<File, PsiErrorElement>>()
        var lines = 0L
        val start = System.nanoTime()
        for (file in files) {
            val text = runCatching { file.readText().replace("\r\n", "\n").replace('\r', '\n') }.getOrNull() ?: continue
            lines += text.count { it == '\n' }
            val psi = factory.createFileFromText(file.name, MatlabLanguage.INSTANCE, text)
            val errors = PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java)
            if (errors.isNotEmpty()) failedFiles += file to errors.first()
            for (e in errors) errorsByMessage.merge(e.errorDescription, 1, Int::plus)
        }
        val seconds = (System.nanoTime() - start) / 1e9
        val report = buildString {
            appendLine("Parsed ${files.size} files ($lines lines) in ${"%.1f".format(seconds)} s")
            appendLine("Files with parse errors: ${failedFiles.size}")
            appendLine("Most common errors:")
            errorsByMessage.entries.sortedByDescending { it.value }.take(15).forEach { appendLine("  ${it.value}x  ${it.key}") }
            appendLine("Files with errors (first error each):")
            for ((file, error) in failedFiles) {
                val line = error.containingFile.text.substring(0, error.textOffset).count { it == '\n' } + 1
                appendLine("  ${file.relativeTo(File(root))}:$line  ${error.errorDescription}")
            }
        }
        File(System.getProperty("matlab.corpus.report") ?: "build/corpus-report.txt").writeText(report)
        println(report.lineSequence().take(20).joinToString("\n"))
    }
}
