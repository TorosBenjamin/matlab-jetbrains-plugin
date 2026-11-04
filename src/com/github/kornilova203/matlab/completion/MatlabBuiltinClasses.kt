package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.MatlabReference
import com.github.kornilova203.matlab.execution.console.MatlabWorkspace
import com.github.kornilova203.matlab.psi.*
import com.github.kornilova203.matlab.psi.types.ownScope
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** A class and the public properties and methods completion offers after `obj.`. */
data class MatlabClassInfo(val name: String, val properties: List<String>, val methods: List<String>)

/**
 * Which class a variable holds when it's one of MATLAB's (or a toolbox's): `p = inputParser`,
 * `m = containers.Map(...)`, `f = figure`, `catch ME`. Its members come from MATLAB itself, see [MatlabClassMembers].
 */
object MatlabBuiltinClasses {
    /** Functions that make an object of another name: MATLAB can't tell without running them. */
    private val FACTORIES: Map<String, String> = buildMap {
        for (name in listOf("figure", "gcf", "uifigure")) put(name, "matlab.ui.Figure")
        for (name in listOf("axes", "gca", "subplot", "nexttile")) put(name, "matlab.graphics.axis.Axes")
        put("uiaxes", "matlab.ui.control.UIAxes")
        for (name in listOf("plot", "plot3", "line", "semilogx", "semilogy", "loglog", "stairs")) put(name, "matlab.graphics.chart.primitive.Line")
        put("scatter", "matlab.graphics.chart.primitive.Scatter")
        put("bar", "matlab.graphics.chart.primitive.Bar")
        put("histogram", "matlab.graphics.chart.primitive.Histogram")
        for (name in listOf("surf", "mesh")) put(name, "matlab.graphics.chart.primitive.Surface")
        for (name in listOf("image", "imagesc", "imshow")) put(name, "matlab.graphics.primitive.Image")
        put("text", "matlab.graphics.primitive.Text")
        put("legend", "matlab.graphics.illustration.Legend")
        put("colorbar", "matlab.graphics.illustration.ColorBar")
        put("tiledlayout", "matlab.graphics.layout.TiledChartLayout")
        put("uicontrol", "matlab.ui.control.UIControl")
        for (name in listOf("readtable", "array2table", "struct2table", "cell2table")) put(name, "table")
        for (name in listOf("readtimetable", "table2timetable", "array2timetable")) put(name, "timetable")
        for (name in listOf("seconds", "minutes", "hours", "days", "years", "milliseconds")) put(name, "duration")
        put("NaT", "datetime")
    }

    /**
     * The class [call] makes (`containers.Map('KeyType', 'char')` -> `containers.Map`): a factory listed above,
     * else the called name itself, which MATLAB is then asked about (it may not be a class at all).
     */
    fun classMadeBy(call: String): String? {
        val callee = call.substringBefore('(').filterNot { it.isWhitespace() }
        if (!CALLEE.matches(callee)) return null
        return FACTORIES[callee] ?: callee
    }

    private val CALLEE = Regex("[A-Za-z]\\w*(\\.[A-Za-z]\\w*)*")

    /**
     * The class variable [name] holds where [context] is: from its first assignment in the function (or script)
     * that calls something not defined in the project, or from `catch name`.
     */
    fun classOfVariable(name: String, context: PsiElement): String? {
        val scope = ownScope(context)
        for (assign in PsiTreeUtil.findChildrenOfType(scope, MatlabAssignExpr::class.java)) {
            if ((assign.left as? MatlabRefExpr)?.text != name || ownScope(assign) != scope) continue
            val value = assign.right ?: continue
            // The project's own classes and functions are typed by the plugin itself
            val callee = (value as? MatlabFunctionExpr)?.firstChild ?: value
            if (callee is MatlabRefExpr && MatlabReference(callee).resolve() != null) continue
            return classMadeBy(value.text) ?: continue
        }
        for (catchBlock in PsiTreeUtil.findChildrenOfType(scope, MatlabCatchBlock::class.java)) {
            if (catchBlock.node.findChildByType(MatlabTypes.IDENTIFIER)?.text == name) return "MException"
        }
        return null
    }

    /** For when MATLAB can't be asked (not installed): the members of common classes. */
    val FALLBACK: Map<String, MatlabClassInfo> = listOf(
        of("inputParser",
            "FunctionName CaseSensitive KeepUnmatched PartialMatching StructExpand Parameters Results Unmatched UsingDefaults",
            "addRequired addOptional addParameter addParamValue parse createCopy"),
        of("containers.Map", "Count KeyType ValueType", "keys values isKey remove length"),
        of("dictionary", "", "keys values isKey remove entries insert lookup numEntries isConfigured types"),
        of("MException", "identifier message stack cause Correction", "getReport throw rethrow throwAsCaller addCause addCorrection"),
        of("datetime", "Year Month Day Hour Minute Second Format TimeZone SystemTimeZone", ""),
        of("duration", "Format", ""),
        of("table", "Properties Variables Row", ""),
        of("timetable", "Properties Variables Time", ""),
        of("VideoReader", "Name Path Duration FrameRate Height Width NumFrames BitsPerPixel VideoFormat CurrentTime Tag UserData",
            "readFrame hasFrame read"),
        of("VideoWriter", "Filename Path FileFormat FrameRate Quality Duration FrameCount Height Width VideoCompressionMethod",
            "open writeVideo close"),
        of("timer", "Name Tag Period ExecutionMode BusyMode TasksToExecute TasksExecuted StartDelay TimerFcn StartFcn StopFcn ErrorFcn Running UserData",
            "start stop startat wait delete"),
        of("matlab.ui.Figure", "Name Number NumberTitle Position Units Color Visible WindowState MenuBar ToolBar Children CurrentAxes Colormap Tag UserData CloseRequestFcn SizeChangedFcn KeyPressFcn WindowButtonDownFcn", ""),
        of("matlab.graphics.axis.Axes", "Title XLabel YLabel ZLabel XLim YLim ZLim CLim XScale YScale ZScale XGrid YGrid ZGrid Box FontSize Position Children ColorOrder NextPlot Legend View DataAspectRatio Colormap XTick YTick XTickLabel YTickLabel Tag UserData", ""),
        of("matlab.graphics.chart.primitive.Line", "Color LineStyle LineWidth Marker MarkerSize MarkerFaceColor MarkerEdgeColor XData YData ZData DisplayName Visible Tag UserData", ""),
        of("serialport", "Port BaudRate NumBytesAvailable NumBytesWritten Terminator Timeout ByteOrder DataBits StopBits Parity FlowControl UserData",
            "read write readline writeline flush configureTerminator configureCallback getpinstatus setRTS setDTR"),
        of("tcpclient", "Address Port Timeout ConnectTimeout NumBytesAvailable NumBytesWritten Terminator ByteOrder UserData",
            "read write readline writeline flush configureTerminator configureCallback"),
        of("audioplayer", "SampleRate BitsPerSample NumChannels TotalSamples CurrentSample Running Tag UserData",
            "play playblocking pause resume stop isplaying"),
    ).associateBy { it.name }

    private fun of(name: String, properties: String, methods: String) =
        MatlabClassInfo(name, properties.split(' ').filter { it.isNotEmpty() }, methods.split(' ').filter { it.isNotEmpty() })
}

/** After `p.` where `p = inputParser` (or another class from MATLAB): its properties and methods. */
class MatlabBuiltinClassCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val ref = parameters.position.parent as? MatlabRefExpr ?: return
        val node = (ref.parent as? MatlabFunctionExpr)?.takeIf { it.firstChild == ref } ?: ref
        val above = node.parent as? MatlabQualifiedExpr ?: return
        if (above.firstChild == node) return
        val qualifier = above.firstChild as? MatlabRefExpr ?: return
        val name = qualifier.text
        // In the console, a variable that's already in the workspace is asked from MATLAB as it is
        if (MatlabWorkspace.of(parameters.originalFile)?.variables?.any { it.name == name } == true) return
        val className = MatlabBuiltinClasses.classOfVariable(name, qualifier) ?: return
        val info = MatlabClassMembers.get(className)
        if (info == null) {
            if (MatlabClassMembers.isAsking(className)) result.addLookupAdvertisement("Asking MATLAB about $className…")
            return
        }
        for (property in info.properties) {
            result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(property).withIcon(AllIcons.Nodes.Property).withTypeText(info.name), 100.0))
        }
        for (method in info.methods) {
            result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(method).withIcon(AllIcons.Nodes.Method).withTailText("()", true).withTypeText(info.name), 90.0))
        }
    }
}
