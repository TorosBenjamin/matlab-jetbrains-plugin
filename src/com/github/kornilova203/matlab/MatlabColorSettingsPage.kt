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
        AttributesDescriptor("Parameter", MatlabAnnotator.PARAMETER),
        AttributesDescriptor("Object reference (obj in methods)", MatlabAnnotator.OBJECT_REFERENCE),
        AttributesDescriptor("Field (obj.Width)", MatlabAnnotator.FIELD),
        AttributesDescriptor("Function call", MatlabAnnotator.FUNCTION_CALL),
        AttributesDescriptor("Method call (obj.move())", MatlabAnnotator.METHOD_CALL),
        AttributesDescriptor("Constant property", MatlabAnnotator.CONSTANT),
        AttributesDescriptor("Type (double, superclass)", MatlabAnnotator.TYPE),
        AttributesDescriptor("Attribute name (SetAccess)", MatlabAnnotator.ATTRIBUTE),
        AttributesDescriptor("Attribute value (private)", MatlabAnnotator.ATTRIBUTE_VALUE),
        AttributesDescriptor("Event", MatlabAnnotator.EVENT),
        AttributesDescriptor("Enumeration member", MatlabAnnotator.ENUM_MEMBER),
        AttributesDescriptor("Command argument (hold on)", MatlabAnnotator.COMMAND_ARGUMENT),
        AttributesDescriptor("Function declaration", MatlabAnnotator.FUNCTION_DECLARATION),
        AttributesDescriptor("Class declaration", MatlabAnnotator.CLASS_DECLARATION),
        AttributesDescriptor("Function handle @(...) / @name", MatlabAnnotator.LAMBDA_PARENTH),
        AttributesDescriptor("Bad character", MatlabSyntaxHighlighter.BAD_CHARACTER),
    )

    private val tags = mapOf(
        "fn" to MatlabAnnotator.FUNCTION_DECLARATION,
        "cls" to MatlabAnnotator.CLASS_DECLARATION,
        "blk" to MatlabAnnotator.KEYWORD,
        "at" to MatlabAnnotator.LAMBDA_PARENTH,
        "id" to MatlabAnnotator.ID,
        "param" to MatlabAnnotator.PARAMETER,
        "obj" to MatlabAnnotator.OBJECT_REFERENCE,
        "field" to MatlabAnnotator.FIELD,
        "call" to MatlabAnnotator.METHOD_CALL,
        "fcall" to MatlabAnnotator.FUNCTION_CALL,
        "const" to MatlabAnnotator.CONSTANT,
        "type" to MatlabAnnotator.TYPE,
        "attr" to MatlabAnnotator.ATTRIBUTE,
        "val" to MatlabAnnotator.ATTRIBUTE_VALUE,
        "event" to MatlabAnnotator.EVENT,
        "cmd" to MatlabAnnotator.COMMAND_ARGUMENT,
    )

    override fun getDisplayName(): String = "MATLAB"
    override fun getIcon(): Icon = Icons.Matlab
    override fun getHighlighter(): SyntaxHighlighter = MatlabSyntaxHighlighter()
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tags

    override fun getDemoText(): String = """
        classdef <cls>Counter</cls> < <type>handle</type>
            % Counts things.
            <blk>properties</blk> (<attr>SetAccess</attr> = <val>private</val>)
                <field>Count</field> (1,1) <type>double</type> = 0
            end
            <blk>properties</blk> (<attr>Constant</attr>)
                <const>Step</const> = 1
            end
            <blk>events</blk>
                <event>Changed</event>
            end
            <blk>methods</blk>
                function <fn>increment</fn>(<obj>obj</obj>, <param>step</param>)
                    if nargin < 2
                        <param>step</param> = 1;
                    end
                    <obj>obj</obj>.<field>Count</field> = <obj>obj</obj>.<field>Count</field> + <param>step</param>;
                    <obj>obj</obj>.<call>notify</call>('Changed');
                end
            end
        end

        <id>sq</id> = <at>@(</at><param>x</param><at>)</at> <param>x</param>.^2;
        <id>fh</id> = <at>@</at><fcall>disp</fcall>;
        <id>msg</id> = <fcall>sprintf</fcall>('total: %d\n', 42);
        <id>s</id> = "a string";
        <id>m</id> = [1 2; 3 4]';
        <fcall>disp</fcall>(<id>msg</id>)  % trailing comment
        <fcall>hold</fcall> <cmd>on</cmd>
    """.trimIndent()
}
