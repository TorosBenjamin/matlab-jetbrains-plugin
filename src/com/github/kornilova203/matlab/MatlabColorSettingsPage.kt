package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.highlighting.MatlabAnnotator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/** Settings | Editor | Color Scheme | MATLAB */
class MatlabColorSettingsPage : ColorSettingsPage {
    private val descriptors = arrayOf(
        AttributesDescriptor("Keyword", MatlabSyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Block keyword (properties, methods, events, enumeration)", MatlabAnnotator.KEYWORD),
        AttributesDescriptor("Comment", MatlabSyntaxHighlighter.COMMENT),
        AttributesDescriptor("String", MatlabSyntaxHighlighter.STRING),
        AttributesDescriptor("String escape//Valid", MatlabSyntaxHighlighter.STRING_ESCAPE),
        AttributesDescriptor("String escape//Invalid", MatlabSyntaxHighlighter.INVALID_ESCAPE),
        AttributesDescriptor("Number", MatlabSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Semicolon", MatlabSyntaxHighlighter.SEMICOLON),
        AttributesDescriptor("Identifier", MatlabAnnotator.ID),
        AttributesDescriptor("Command argument (hold on)", MatlabAnnotator.COMMAND_ARGUMENT),
        AttributesDescriptor("Function declaration", MatlabAnnotator.FUNCTION_DECLARATION),
        AttributesDescriptor("Class declaration", MatlabAnnotator.CLASS_DECLARATION),
        AttributesDescriptor("Anonymous function @(...)", MatlabAnnotator.LAMBDA_PARENTH),
        AttributesDescriptor("Bad character", MatlabSyntaxHighlighter.BAD_CHARACTER),
    )

    private val tags = mapOf(
        "fn" to MatlabAnnotator.FUNCTION_DECLARATION,
        "cls" to MatlabAnnotator.CLASS_DECLARATION,
        "blk" to MatlabAnnotator.KEYWORD,
        "at" to MatlabAnnotator.LAMBDA_PARENTH,
        "id" to MatlabAnnotator.ID,
        "cmd" to MatlabAnnotator.COMMAND_ARGUMENT,
    )

    override fun getDisplayName(): String = "MATLAB"
    override fun getIcon(): Icon = Icons.Matlab
    override fun getHighlighter(): SyntaxHighlighter = MatlabSyntaxHighlighter()
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tags

    override fun getDemoText(): String = """
        classdef <cls>Counter</cls> < handle
            % Counts things.
            <blk>properties</blk>
                <id>Count</id> = 0
            end
            <blk>methods</blk>
                function <fn>increment</fn>(<id>obj</id>, <id>step</id>)
                    if nargin < 2
                        <id>step</id> = 1;
                    end
                    <id>obj</id>.<id>Count</id> = <id>obj</id>.<id>Count</id> + <id>step</id>;
                end
            end
        end

        <id>sq</id> = <at>@(</at><id>x</id><at>)</at> <id>x</id>.^2;
        <id>msg</id> = sprintf('total: %d\n', 42);
        <id>s</id> = "a string";
        <id>m</id> = [1 2; 3 4]';
        disp(<id>msg</id>)  % trailing comment
        hold <cmd>on</cmd>
    """.trimIndent()
}
