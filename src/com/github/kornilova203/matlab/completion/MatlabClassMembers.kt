package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.execution.console.MatlabConsoleCode
import com.github.kornilova203.matlab.execution.console.MatlabConsoleManager
import com.github.kornilova203.matlab.execution.console.MatlabOutputFilter
import com.github.kornilova203.matlab.settings.MatlabInstallations
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The public properties and methods of classes, as MATLAB itself describes them (`meta.class.fromName`), so
 * completion knows MATLAB's own classes (inputParser, containers.Map, figures, toolboxes) for the installed
 * version. MATLAB is asked once per class, in the background: through a console that waits at its prompt if
 * there is one, else with `matlab -batch`. Answers are kept on disk for each MATLAB installation.
 * Until an answer comes, or without MATLAB, [MatlabBuiltinClasses.FALLBACK] lists the common classes.
 */
object MatlabClassMembers {
    private val LOG = Logger.getInstance(MatlabClassMembers::class.java)
    /** What MATLAB answered, by class name; empty when it isn't a class. For [installation]. */
    private val known = ConcurrentHashMap<String, Optional<MatlabClassInfo>>()
    private val waiting: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val asking: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var installation: String? = null
    @Volatile private var checkedAt = 0L
    /** No MATLAB to ask (not found, or Octave): until when not to look again. */
    @Volatile private var unavailableUntil = 0L
    @Volatile private var flushScheduled = false

    /** What [className] has: MATLAB's answer, else the fallback list (asking MATLAB in the background). */
    fun get(className: String): MatlabClassInfo? {
        refreshInstallation()
        known[className]?.let { return it.orElse(null) }
        request(className)
        return MatlabBuiltinClasses.FALLBACK[className]
    }

    /** MATLAB is being asked about [className] right now. */
    fun isAsking(className: String) = className in asking || className in waiting

    /** Parses what `ide_class__` prints: per class `c<tab>name`, then `p<tab>property`, `m<tab>method` or `x`. */
    fun parse(text: String): Map<String, MatlabClassInfo?> {
        val result = LinkedHashMap<String, MatlabClassInfo?>()
        var name: String? = null
        val properties = ArrayList<String>()
        val methods = ArrayList<String>()
        var isClass = true
        fun finish() {
            val current = name ?: return
            result[current] = if (isClass) MatlabClassInfo(current, properties.distinct(), methods.distinct().sorted()) else null
        }
        for (line in text.lineSequence().map { it.trimEnd('\r') }) {
            val kind = line.substringBefore('\t')
            val value = line.substringAfter('\t', "")
            when (kind) {
                "c" -> {
                    finish()
                    name = value
                    properties.clear()
                    methods.clear()
                    isClass = true
                }
                "p" -> if (value.isNotEmpty()) properties += value
                "m" -> if (value.isNotEmpty()) methods += value
                "x" -> isClass = false
            }
        }
        finish()
        return result
    }

    @TestOnly
    fun putForTests(name: String, info: MatlabClassInfo?) {
        refreshInstallation()
        known[name] = Optional.ofNullable(info)
    }

    @TestOnly
    fun clearForTests() {
        known.clear()
    }

    private fun request(className: String) {
        if (System.currentTimeMillis() < unavailableUntil || className in asking || !waiting.add(className)) return
        if (flushScheduled) return
        flushScheduled = true
        // Collects what's asked within a moment (typing a dot asks for one class, a file may ask for several)
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            // On a pooled thread: `matlab -batch` takes a while
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread { flush() }
        }, 300, TimeUnit.MILLISECONDS)
    }

    private fun flush() {
        flushScheduled = false
        val names = waiting.toList()
        waiting.removeAll(names.toSet())
        if (names.isEmpty()) return
        asking += names
        try {
            val console = ProjectManager.getInstance().openProjects
                .filterNot { it.isDisposed }
                .firstNotNullOfOrNull { MatlabConsoleManager.getInstance(it).idleSession() }
            val answer = console?.describeClasses(names)
            if (answer != null) {
                answer.whenComplete { text, _ ->
                    if (text != null) store(parse(text))
                    asking.removeAll(names.toSet())
                }
                return
            }
            askBatch(names)
        } catch (e: Exception) {
            LOG.info("Couldn't ask MATLAB about $names", e)
        }
        asking.removeAll(names.toSet())
    }

    /** `matlab -batch`: starts MATLAB without a window, lists the classes, quits. */
    private fun askBatch(names: List<String>) {
        val interpreter = MatlabInstallations.resolveInterpreter(null)
        if (interpreter == null || MatlabInstallations.isOctave(interpreter)) {
            unavailableUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
            return
        }
        val command = GeneralCommandLine(interpreter, "-batch", MatlabConsoleCode.classBatchCommand(names))
            .withWorkDirectory(FileUtil.getTempDirectory())
        val output = CapturingProcessHandler(command).runProcess(TimeUnit.MINUTES.toMillis(2).toInt())
        val text = output.stdout
        val start = text.indexOf(MatlabOutputFilter.BEGIN)
        val end = text.indexOf(MatlabOutputFilter.END, start + 1)
        if (start < 0 || end < 0) {
            LOG.info("MATLAB didn't describe $names: ${output.stderr.take(500)}")
            // Not again soon: MATLAB may not start (license, path)
            unavailableUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
            return
        }
        store(parse(text.substring(start + 1, end).substringAfter('\n', "")))
    }

    private fun store(answers: Map<String, MatlabClassInfo?>) {
        if (answers.isEmpty()) return
        for ((name, info) in answers) known[name] = Optional.ofNullable(info)
        save()
    }

    /** Each MATLAB installation has its own classes; the answers for the current one are loaded from disk. */
    private fun refreshInstallation() {
        val now = System.currentTimeMillis()
        if (now - checkedAt < 60_000 && installation != null) return
        checkedAt = now
        val interpreter = MatlabInstallations.resolveInterpreter(null)
        val current = interpreter?.let { MatlabInstallations.matlabRootOf(it)?.path ?: it } ?: ""
        if (current == installation) return
        installation = current
        known.clear()
        load()
    }

    private fun file(): File? {
        val current = installation?.takeIf { it.isNotEmpty() } ?: return null
        val name = Integer.toHexString(current.hashCode()) + ".txt"
        return File(PathManager.getSystemPath(), "matlab-classes/$name")
    }

    private fun load() {
        val file = file()?.takeIf { it.isFile } ?: return
        try {
            val lines = file.readLines().drop(1)  // the installation's path
            val byClass = lines.groupBy { it.substringBefore('\t') }
            for ((name, entries) in byClass) {
                if (entries.any { it.split('\t').getOrNull(1) == "x" }) {
                    known[name] = Optional.empty()
                    continue
                }
                fun of(kind: String) = entries.mapNotNull { line ->
                    line.split('\t').takeIf { it.getOrNull(1) == kind }?.getOrNull(2)?.takeIf { it.isNotEmpty() }
                }
                known[name] = Optional.of(MatlabClassInfo(name, of("p"), of("m")))
            }
        } catch (e: Exception) {
            LOG.info("Couldn't read $file", e)
        }
    }

    @Synchronized
    private fun save() {
        val file = file() ?: return
        try {
            val text = buildString {
                append(installation).append('\n')
                for ((name, info) in known.toSortedMap()) {
                    val value = info.orElse(null)
                    if (value == null) {
                        append(name).append("\tx\t\n")
                        continue
                    }
                    value.properties.forEach { append(name).append("\tp\t").append(it).append('\n') }
                    value.methods.forEach { append(name).append("\tm\t").append(it).append('\n') }
                    // A class with no public members is still known
                    if (value.properties.isEmpty() && value.methods.isEmpty()) append(name).append("\tp\t\n")
                }
            }
            FileUtil.writeToFile(file, text)
        } catch (e: Exception) {
            LOG.info("Couldn't save $file", e)
        }
    }
}
