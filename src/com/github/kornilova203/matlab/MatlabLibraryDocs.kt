package com.github.kornilova203.matlab

import java.util.Properties

/**
 * Bundled data about MATLAB's built-in functions, loaded once on first use.
 */
object MatlabLibraryDocs {
    /** Function name → one-line description. */
    val descriptions: Properties by lazy { loadProperties("docs/function_descriptions.properties") }

    /** Function name → page path under https://www.mathworks.com/help/matlab/ */
    val urls: Properties by lazy { loadProperties("docs/function_url.properties") }

    /** Built-in names, possibly package-qualified (e.g. `matlab.lang.makeValidName`). */
    val functionNames: List<String> by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("docs/function_list") ?: return@lazy emptyList()
        stream.bufferedReader().use { reader -> reader.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList() }
    }

    private val helpBase = java.net.URI("https://www.mathworks.com/help/matlab/")

    fun isKnown(name: String): Boolean = urls.containsKey(name) || descriptions.containsKey(name)

    /** Page of a built-in function. Targets are relative to /help/matlab/ (some use ../toolbox/...). */
    fun pageUrl(name: String): String? = urls.getProperty(name)?.let { helpBase.resolve(it).toString() }

    /** Always-valid fallback for functions without a known page (or whose page moved). */
    fun searchUrl(name: String): String =
        "https://www.mathworks.com/help/search.html?qdoc=" + java.net.URLEncoder.encode(name, Charsets.UTF_8) + "&submitsearch="

    private fun loadProperties(path: String): Properties {
        val properties = Properties()
        javaClass.classLoader.getResourceAsStream(path)?.use { properties.load(it) }
        return properties
    }
}
