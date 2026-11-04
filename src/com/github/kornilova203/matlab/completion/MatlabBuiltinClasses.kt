package com.github.kornilova203.matlab.completion

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

/** A class that comes with MATLAB, with the properties and methods completion offers after `obj.`. */
data class MatlabBuiltinClass(val name: String, val properties: List<String>, val methods: List<String>)

/**
 * MATLAB's own classes that code often keeps in a variable, and the calls that make them. The plugin can't read
 * them from MATLAB's installation (they're compiled), so the common ones are listed here.
 */
object MatlabBuiltinClasses {
    private fun of(name: String, properties: String, methods: String) =
        MatlabBuiltinClass(name, properties.split(' ').filter { it.isNotEmpty() }, methods.split(' ').filter { it.isNotEmpty() })

    private val INPUT_PARSER = of("inputParser",
        "FunctionName CaseSensitive KeepUnmatched PartialMatching StructExpand Parameters Results Unmatched UsingDefaults",
        "addRequired addOptional addParameter addParamValue parse createCopy")
    private val MAP = of("containers.Map", "Count KeyType ValueType", "keys values isKey remove length")
    private val DICTIONARY = of("dictionary", "", "keys values isKey remove entries insert lookup numEntries isConfigured types")
    private val EXCEPTION = of("MException", "identifier message stack cause Correction",
        "getReport throw rethrow throwAsCaller addCause addCorrection")
    private val DATETIME = of("datetime", "Year Month Day Hour Minute Second Format TimeZone SystemTimeZone", "")
    private val DURATION = of("duration", "Format", "")
    private val TABLE = of("table", "Properties Variables Row", "")
    private val TIMETABLE = of("timetable", "Properties Variables Time", "")
    private val VIDEO_READER = of("VideoReader",
        "Name Path Duration FrameRate Height Width NumFrames BitsPerPixel VideoFormat CurrentTime Tag UserData",
        "readFrame hasFrame read")
    private val VIDEO_WRITER = of("VideoWriter",
        "Filename Path FileFormat FrameRate Quality Duration FrameCount Height Width VideoCompressionMethod",
        "open writeVideo close")
    private val TIMER = of("timer",
        "Name Tag Period ExecutionMode BusyMode TasksToExecute TasksExecuted StartDelay TimerFcn StartFcn StopFcn ErrorFcn Running UserData",
        "start stop startat wait delete")
    private val FIGURE = of("matlab.ui.Figure",
        "Name Number NumberTitle Position Units Color Visible WindowState MenuBar ToolBar Children CurrentAxes Colormap Tag UserData CloseRequestFcn SizeChangedFcn KeyPressFcn WindowButtonDownFcn",
        "")
    private val AXES = of("matlab.graphics.axis.Axes",
        "Title XLabel YLabel ZLabel XLim YLim ZLim CLim XScale YScale ZScale XGrid YGrid ZGrid Box FontSize Position Children ColorOrder NextPlot Legend View DataAspectRatio Colormap XTick YTick XTickLabel YTickLabel Tag UserData",
        "")
    private val LINE = of("matlab.graphics.chart.primitive.Line",
        "Color LineStyle LineWidth Marker MarkerSize MarkerFaceColor MarkerEdgeColor XData YData ZData DisplayName Visible Tag UserData",
        "")
    private val SERIAL_PORT = of("serialport", "Port BaudRate NumBytesAvailable NumBytesWritten Terminator Timeout ByteOrder DataBits StopBits Parity FlowControl UserData",
        "read write readline writeline flush configureTerminator configureCallback getpinstatus setRTS setDTR")
    private val TCP_CLIENT = of("tcpclient", "Address Port Timeout ConnectTimeout NumBytesAvailable NumBytesWritten Terminator ByteOrder UserData",
        "read write readline writeline flush configureTerminator configureCallback")
    private val AUDIO_PLAYER = of("audioplayer", "SampleRate BitsPerSample NumChannels TotalSamples CurrentSample Running Tag UserData",
        "play playblocking pause resume stop isplaying")

    /** The class made by each call: `p = inputParser`, `m = containers.Map(...)`, `f = figure`. */
    private val CONSTRUCTORS: Map<String, MatlabBuiltinClass> = buildMap {
        put("inputParser", INPUT_PARSER)
        put("containers.Map", MAP)
        put("dictionary", DICTIONARY)
        put("MException", EXCEPTION)
        for (name in listOf("datetime", "NaT")) put(name, DATETIME)
        for (name in listOf("duration", "seconds", "minutes", "hours", "days", "years", "milliseconds")) put(name, DURATION)
        for (name in listOf("table", "readtable", "array2table", "struct2table", "cell2table")) put(name, TABLE)
        for (name in listOf("timetable", "readtimetable", "table2timetable", "array2timetable")) put(name, TIMETABLE)
        put("VideoReader", VIDEO_READER)
        put("VideoWriter", VIDEO_WRITER)
        put("timer", TIMER)
        for (name in listOf("figure", "gcf", "uifigure")) put(name, FIGURE)
        for (name in listOf("axes", "gca", "subplot", "nexttile", "uiaxes")) put(name, AXES)
        for (name in listOf("plot", "plot3", "line", "semilogx", "semilogy", "loglog")) put(name, LINE)
        put("serialport", SERIAL_PORT)
        put("tcpclient", TCP_CLIENT)
        put("audioplayer", AUDIO_PLAYER)
    }

    /** The class made by [call] (`containers.Map('KeyType', 'char', ...)`), if it's one of MATLAB's listed here. */
    fun madeBy(call: String): MatlabBuiltinClass? =
        CONSTRUCTORS[call.substringBefore('(').filterNot { it.isWhitespace() }]

    /**
     * The built-in class variable [name] holds where [context] is: from its first assignment in the function (or
     * script) whose value is one of the calls listed here, or from `catch name`.
     */
    fun classOfVariable(name: String, context: PsiElement): MatlabBuiltinClass? {
        val scope = ownScope(context)
        for (assign in PsiTreeUtil.findChildrenOfType(scope, MatlabAssignExpr::class.java)) {
            if ((assign.left as? MatlabRefExpr)?.text != name || ownScope(assign) != scope) continue
            madeBy(assign.right?.text ?: continue)?.let { return it }
        }
        for (catchBlock in PsiTreeUtil.findChildrenOfType(scope, MatlabCatchBlock::class.java)) {
            if (catchBlock.node.findChildByType(MatlabTypes.IDENTIFIER)?.text == name) return EXCEPTION
        }
        return null
    }
}

/** After `p.` where `p = inputParser` (or another of MATLAB's classes): its properties and methods. */
class MatlabBuiltinClassCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val ref = parameters.position.parent as? MatlabRefExpr ?: return
        val node = (ref.parent as? MatlabFunctionExpr)?.takeIf { it.firstChild == ref } ?: ref
        val above = node.parent as? MatlabQualifiedExpr ?: return
        if (above.firstChild == node) return
        val qualifier = above.firstChild as? MatlabRefExpr ?: return
        val name = qualifier.text
        // In the console, a variable that's already in the workspace is asked from MATLAB instead
        if (MatlabWorkspace.of(parameters.originalFile)?.variables?.any { it.name == name } == true) return
        val type = MatlabBuiltinClasses.classOfVariable(name, qualifier) ?: return
        for (property in type.properties) {
            result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(property).withIcon(AllIcons.Nodes.Property).withTypeText(type.name), 100.0))
        }
        for (method in type.methods) {
            result.addElement(PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(method).withIcon(AllIcons.Nodes.Method).withTailText("()", true).withTypeText(type.name), 90.0))
        }
    }
}
