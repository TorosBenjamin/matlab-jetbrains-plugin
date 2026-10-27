package com.github.kornilova203.matlab;

import com.github.kornilova203.matlab.psi.MatlabElementType;
import com.github.kornilova203.matlab.psi.MatlabTokenType;
import com.github.kornilova203.matlab.psi.MatlabTypes;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.github.kornilova203.matlab.psi.MatlabTypes.*;
import static com.intellij.psi.TokenType.WHITE_SPACE;

/**
 * @author Liudmila Kornilova
 **/
public class MatlabParserUtil extends GeneratedParserUtilBase {
    static final String ALLOW_END_AS_IDENTIFIER = "ALLOW_END_AS_IDENTIFIER";
    static final String NEW_LINE_ALLOWED = "NEW_LINE_ALLOWED";
    private static final Key<Object2LongOpenHashMap<String>> MODES_KEY = Key.create("MODES_KEY");
    static final HashSet<IElementType> KEYWORDS = new HashSet<>(Arrays.asList(FUNCTION, END, IF, ELSE, ELSEIF, WHILE,
            SWITCH, CASE, OTHERWISE, FOR, CLASSDEF, TRY, CATCH, GLOBAL, RETURN, CONTINUE, BREAK, SPMD, PARFOR));

    public static boolean parseIdentifier(PsiBuilder builder, int level) {
        if (builder.getTokenType() == IDENTIFIER) {
            builder.advanceLexer();
            return true;
        } else if (isOn(builder, level, ALLOW_END_AS_IDENTIFIER) && builder.getTokenType() == END) {
            Marker mark = builder.mark();
            builder.advanceLexer();
            mark.collapse(IDENTIFIER);
            return true;
        }
        return false;
    }

    public static boolean parseWhiteSpace(PsiBuilder builder, int level, Parser brParser) {
        Boolean newLineAllowed = null;
        while (true) {
            if (brParser.parse(builder, level)) continue;
            if (builder.getTokenType() == NEWLINE) {
                if (newLineAllowed == null) newLineAllowed = isOn(builder, level, NEW_LINE_ALLOWED);
                if (newLineAllowed) {
                    builder.advanceLexer();
                    continue;
                }
            }
            break;
        }
        return true;
    }

    public static boolean parseIdentifierAsKeyword(PsiBuilder builder, int level, @NotNull MatlabElementType type) {
        if (builder.getTokenType() == IDENTIFIER && type.toString().equals(builder.getTokenText())) {
            Marker mark = builder.mark();
            builder.advanceLexer();
            mark.collapse(type);
            return true;
        }
        return false;
    }

    public static boolean withOn(PsiBuilder builder, int level, String mode, Parser parser) {
        return withImpl(builder, level, mode, true, parser);
    }

    public static boolean withOff(PsiBuilder builder, int level, String mode, Parser parser) {
        return withImpl(builder, level, mode, false, parser);
    }

    private static boolean withImpl(PsiBuilder builder, int level, String mode, boolean onOff, Parser parser) {
        Object2LongOpenHashMap<String> map = getParsingModes(builder);
        long prev = map.getLong(mode);
        boolean change = ((prev & 1) == 0) == onOff;
        if (change) map.put(mode, prev << 1 | (onOff ? 1 : 0));
        boolean result = parser.parse(builder, level);
        if (change) map.put(mode, prev);
        return result;
    }

    private static boolean isOn(PsiBuilder builder, int level, String mode) {
        Object2LongOpenHashMap<String> map = getParsingModes(builder);
        return (map.getLong(mode) & 1) != 0;
    }

    private static Object2LongOpenHashMap<String> getParsingModes(PsiBuilder builder) {
        Object2LongOpenHashMap<String> flags = builder.getUserData(MODES_KEY);
        if (flags == null) builder.putUserData(MODES_KEY, flags = new Object2LongOpenHashMap<>());
        return flags;
    }

    private static final java.util.Set<String> ARGUMENTS_BLOCK_KINDS = java.util.Set.of("Input", "Output", "Repeating");

    /** `arguments` followed by end of line or by (Input|Output|Repeating) starts a block; otherwise it's a variable. */
    public static boolean isArgumentsBlockStart(PsiBuilder builder, int level) {
        if (builder.getTokenType() != IDENTIFIER || !"arguments".equals(builder.getTokenText())) return false;
        IElementType next = builder.lookAhead(1);
        if (next == null || next == NEWLINE) return true;
        if (next != LPARENTH) return false;
        Marker probe = builder.mark();
        try {
            builder.advanceLexer();
            builder.advanceLexer();
            if (builder.getTokenType() != IDENTIFIER || !ARGUMENTS_BLOCK_KINDS.contains(builder.getTokenText())) return false;
            builder.advanceLexer();
            return builder.getTokenType() == RPARENTH;
        } finally {
            probe.rollbackTo();
        }
    }

    private static final Set<IElementType> COMMAND_START_TOKENS = new HashSet<>(Arrays.asList(
            IDENTIFIER, INTEGER, FLOAT, FLOAT_EXPONENTIAL, SINGLE_QUOTE_STRING, DOUBLE_QUOTE_STRING, CTRANS,
            GLOBAL /* clear global */));
    /** Tokens that start a command argument only when glued to the next token: -O, *.m, ../lib, /tmp */
    private static final Set<IElementType> COMMAND_START_PREFIXES = new HashSet<>(Arrays.asList(
            MINUS, MUL, DOT_RDIV, RDIV, LDIV));

    /**
     * Consumes the rest of a command syntax statement (`hold on`, `addpath ../lib`).
     * Called right after the command name; succeeds only if the name is followed by whitespace
     * and then something that can't continue an expression.
     */
    public static boolean parseCommandArguments(PsiBuilder builder, int level) {
        if (builder.rawLookup(-1) != WHITE_SPACE) return false;
        IElementType type = builder.getTokenType();
        boolean isCommand = COMMAND_START_TOKENS.contains(type) ||
                type == DOT ||  // cd .  /  addpath ../lib

                (COMMAND_START_PREFIXES.contains(type) && isGluedToNext(builder));
        if (!isCommand) return false;
        boolean inQuote = false;  // 'quoted text' after a space is lexed as transpose tokens
        while (!builder.eof()) {
            type = builder.getTokenType();
            if (!inQuote && (type == NEWLINE || type == SEMICOLON || type == COMMA)) break;
            if (type == NEWLINE) break;
            if (type == CTRANS || type == TRANS) inQuote = !inQuote;
            builder.advanceLexer();
        }
        return true;
    }

    private static boolean isGluedToNext(PsiBuilder builder) {
        IElementType next = builder.rawLookup(1);
        return next != null && next != WHITE_SPACE && next != NEWLINE;
    }

    public static boolean parseKeywordAsRefExpression(PsiBuilder builder, int level) {
        if (KEYWORDS.contains(builder.getTokenType())) {
            Marker mark = builder.mark();
            builder.advanceLexer();
            Marker ref = mark.precede();
            mark.collapse(IDENTIFIER);
            ref.done(REF_EXPR);
            return true;
        }
        return false;
    }
}
