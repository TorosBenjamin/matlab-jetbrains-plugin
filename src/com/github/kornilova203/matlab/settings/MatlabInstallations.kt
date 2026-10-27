package com.github.kornilova203.matlab.settings

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.TestOnly
import java.io.File

/** Finds the program that runs MATLAB code: MATLAB, or GNU Octave as a fallback. */
object MatlabInstallations {
    /** Key under which the original plugin remembered the last interpreter path. */
    private const val LEGACY_INTERPRETER_KEY = "MATLAB_INTERPRETER"

    private var detector: () -> List<String> = ::detect

    /**
     * The interpreter to run with: the run configuration's own path, else the MATLAB setting,
     * else the path the old plugin remembered, else the first one detected.
     */
    fun resolveInterpreter(configurationPath: String?): String? =
        configurationPath?.takeIf { it.isNotBlank() }
            ?: MatlabSettings.getInstance().matlabPath?.takeIf { it.isNotBlank() }
            ?: PropertiesComponent.getInstance().getValue(LEGACY_INTERPRETER_KEY)?.takeIf { it.isNotBlank() }
            ?: detector().firstOrNull()

    private val mlintCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<String>>()

    /**
     * MATLAB's code analyzer, `<matlabroot>/bin/<arch>/mlint`, for the interpreter that would run scripts.
     * It runs on its own, without starting MATLAB. Null for Octave or when MATLAB isn't found.
     */
    fun findMlint(): String? {
        val interpreter = resolveInterpreter(null) ?: return null
        return mlintCache.computeIfAbsent(interpreter) { java.util.Optional.ofNullable(mlintNextTo(it)) }.orElse(null)
    }

    private fun mlintNextTo(interpreter: String): String? {
        // bin/matlab may be a symlink, e.g. /usr/local/bin/matlab -> /usr/local/MATLAB/R2025b/bin/matlab
        val bin = File(interpreter).canonicalFile.parentFile ?: return null
        if (bin.name != "bin") return null
        val archDirs = bin.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        return archDirs.map { File(it, executable("mlint")) }.firstOrNull { it.isFile }?.path
    }

    /** MATLAB installations (newest release first), then Octave. */
    fun detect(): List<String> {
        val matlab = LinkedHashSet<String>()
        PathEnvironmentVariableUtil.findInPath(executable("matlab"))?.let { matlab += it.path }
        for (root in installRoots()) {
            val releases = root.listFiles { f -> f.isDirectory && f.name.contains("R20") }.orEmpty()
                .sortedByDescending { it.name }  // R2025b before R2024a
            for (release in releases) {
                val exe = File(release, "bin/" + executable("matlab"))
                if (exe.isFile) matlab += exe.path
            }
        }
        val octave = listOf("octave-cli", "octave").mapNotNull { PathEnvironmentVariableUtil.findInPath(executable(it))?.path }
        return (matlab + octave).distinct()
    }

    private fun installRoots(): List<File> {
        val home = System.getProperty("user.home")
        val paths = when {
            SystemInfo.isWindows -> listOf("C:\\Program Files\\MATLAB", "C:\\Program Files (x86)\\MATLAB")
            SystemInfo.isMac -> listOf("/Applications")  // /Applications/MATLAB_R2025a.app
            else -> listOf("/usr/local/MATLAB", "/opt/MATLAB", "$home/MATLAB", "$home/.local/MATLAB")
        }
        return paths.map(::File).filter { it.isDirectory }
    }

    private fun executable(name: String) = if (SystemInfo.isWindows) "$name.exe" else name

    @TestOnly
    fun clearMlintCacheForTests() = mlintCache.clear()

    @TestOnly
    fun setDetectorForTests(detect: () -> List<String>, disposable: com.intellij.openapi.Disposable) {
        detector = detect
        com.intellij.openapi.util.Disposer.register(disposable) { detector = ::detect }
    }
}
