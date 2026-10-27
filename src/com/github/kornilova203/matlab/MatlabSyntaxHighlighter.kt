package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.lexer.MatlabHighlightingLexer
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.github.kornilova203.matlab.psi.MatlabTypes.*
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType


class MatlabSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        val COMMENT = createTextAttributesKey("MATLAB.COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BAD_CHARACTER = createTextAttributesKey("MATLAB.BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
        val STRING = createTextAttributesKey("MATLAB.STRING", DefaultLanguageHighlighterColors.STRING)
        val SEMICOLON = createTextAttributesKey("MATLAB.SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val ID = createTextAttributesKey("MATLAB.VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val KEYWORD = createTextAttributesKey("MATLAB.KEYWORD_TOKEN", DefaultLanguageHighlighterColors.KEYWORD)

        val NUMBER = createTextAttributesKey("MATLAB.NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING_ESCAPE = createTextAttributesKey("MATLAB.VALID_STRING_ESCAPE", VALID_STRING_ESCAPE)
        val INVALID_ESCAPE = createTextAttributesKey("MATLAB.INVALID_STRING_ESCAPE", INVALID_STRING_ESCAPE)

        private val BAD_CHAR_KEYS: Array<TextAttributesKey> = arrayOf(BAD_CHARACTER)
        private val VALID_STRING_ESCAPE_KEYS: Array<TextAttributesKey> = arrayOf(STRING_ESCAPE)
        private val INVALID_STRING_ESCAPE_KEYS: Array<TextAttributesKey> = arrayOf(INVALID_ESCAPE)
        private val COMMENT_KEYS: Array<TextAttributesKey> = arrayOf(COMMENT)
        private val STRING_KEYS: Array<TextAttributesKey> = arrayOf(STRING)
        private val SEMICOLON_KEYS: Array<TextAttributesKey> = arrayOf(SEMICOLON)
        private val KEYWORD_KEYS: Array<TextAttributesKey> = arrayOf(KEYWORD)
        private val ID_KEYS: Array<TextAttributesKey> = arrayOf(ID)
        private val NUMBER_KEYS: Array<TextAttributesKey> = arrayOf(NUMBER)
        private val EMPTY_KEYS = arrayOf<TextAttributesKey>()
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        if (tokenType == null) {
            return EMPTY_KEYS
        }
        return when (tokenType) {
            MatlabTypes.COMMENT -> COMMENT_KEYS

            SINGLE_QUOTE_STRING, DOUBLE_QUOTE_STRING -> STRING_KEYS

            StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN -> VALID_STRING_ESCAPE_KEYS
            StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN -> INVALID_STRING_ESCAPE_KEYS

            MatlabTypes.SEMICOLON -> SEMICOLON_KEYS
            MatlabTypes.IDENTIFIER -> ID_KEYS

            in MatlabParserUtil.KEYWORDS -> KEYWORD_KEYS

            INTEGER, FLOAT, FLOAT_EXPONENTIAL -> NUMBER_KEYS

            TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS

            else -> EMPTY_KEYS
        }
    }

    override fun getHighlightingLexer(): Lexer = MatlabHighlightingLexer()
}
