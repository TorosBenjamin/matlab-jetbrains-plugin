package com.github.kornilova203.matlab.formatter

import com.github.kornilova203.matlab.MatlabLanguage
import com.github.kornilova203.matlab.psi.MatlabTypes.*
import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Code | Reformat Code: indents blocks (function, if, for, switch/case, classdef sections...) and line
 * continuations, and puts spaces around `=`, comparisons, `&&`/`||`, `+`/`-` and after commas.
 *
 * Whitespace is significant in parts of MATLAB, which are left exactly as they are: matrices and cell
 * arrays (`[1 -2]` has two elements, `[1 - 2]` one), command syntax (`hold on`), and `a -b`, which MATLAB
 * may read as a command when `a` is a function.
 *
 * Line breaks are tokens of the syntax tree here (statements end at them), not whitespace, so the
 * formatting model changes the document text instead of the tree.
 */
class MatlabFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        val settings = formattingContext.codeStyleSettings
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        val documentManager = PsiDocumentManager.getInstance(file.project)
        if (document == null || documentManager.isDocumentBlockedByPsi(document) || !documentManager.isCommitted(document)) {
            // Code built in memory, or tree changes not yet in the document (during refactorings): leave it alone
            return FormattingModelProvider.createFormattingModelForPsiFile(file, MatlabUnformattedBlock(file.node), settings)
        }
        val root = MatlabBlock(file.node, Indent.getNoneIndent(), Spacings(settings))
        return DocumentBasedFormattingModel(root, document, file.project, settings, file.fileType, file)
    }
}

/** The whole file as one block: nothing changes. */
private class MatlabUnformattedBlock(private val node: ASTNode) : Block {
    override fun getTextRange(): TextRange = node.textRange
    override fun getSubBlocks(): List<Block> = emptyList()
    override fun getWrap(): Wrap? = null
    override fun getIndent(): Indent = Indent.getNoneIndent()
    override fun getAlignment(): Alignment? = null
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)
    override fun isIncomplete(): Boolean = false
    override fun isLeaf(): Boolean = true
}

/** Compound statements and sections whose bodies are indented. */
private val CONTAINERS = TokenSet.create(
    IF_BLOCK, ELSEIF_BLOCK, ELSE_BLOCK, FOR_LOOP, PARFOR_LOOP, WHILE_LOOP, SWITCH_BLOCK, CASE_BLOCK, OTHERWISE_BLOCK,
    TRY_BLOCK, CATCH_BLOCK, SPMD_BLOCK, FUNCTION_DECLARATION, CLASS_DECLARATION, PROPERTIES_BLOCK, METHODS_BLOCK,
    EVENTS_BLOCK, ENUMERATION_BLOCK, ARGUMENTS_BLOCK)

/** Parts of a container that are indented one level: bodies and the lists in classdef sections. */
private val INDENTED_PARTS = TokenSet.create(
    BLOCK, CASE_BLOCK, OTHERWISE_BLOCK, PROPERTIES_BLOCK, METHODS_BLOCK, EVENTS_BLOCK, ENUMERATION_BLOCK,
    PROPERTIES_LIST, METHODS_LIST, EVENTS_LIST, ENUM_ITEM, ARGUMENT_VALIDATION_LIST, ARGUMENTS_BLOCK)

/** Kept exactly as written, including inner whitespace and line breaks. */
private val VERBATIM = TokenSet.create(MATRIX_LITERAL, CELL_ARRAY_LITERAL, COMMAND_STATEMENT, FILE_OPERATION)

/** Statement lists: their children are statements, not continuation lines. */
private val STATEMENT_LISTS = TokenSet.create(BLOCK, PROPERTIES_LIST, METHODS_LIST, EVENTS_LIST, ARGUMENT_VALIDATION_LIST)

private class MatlabBlock(private val node: ASTNode, private val indent: Indent, private val spacings: Spacings) : Block {
    private val children: List<Block> by lazy { buildChildren() }

    private fun buildChildren(): List<Block> {
        if (node.elementType in VERBATIM) return emptyList()
        val result = ArrayList<Block>()
        var child = node.firstChildNode
        while (child != null) {
            if (hasContent(child)) result += MatlabBlock(child, childIndent(child), spacings)
            child = child.treeNext
        }
        return result
    }

    private fun childIndent(child: ASTNode): Indent {
        val type = node.elementType
        val childType = child.elementType
        return when {
            type in CONTAINERS && childType in INDENTED_PARTS -> Indent.getNormalIndent()
            // A comment between the parts of a container lines up with the body (`% comment` before `else`)
            type in CONTAINERS && childType == COMMENT -> Indent.getNormalIndent()
            type in CONTAINERS || type in STATEMENT_LISTS || node.treeParent == null -> Indent.getNoneIndent()
            // The rest of a statement on the next line, after `...`
            else -> Indent.getContinuationWithoutFirstIndent()
        }
    }

    override fun getTextRange(): TextRange {
        // Without the line breaks at its start or end, which belong to no block
        val blocks = children
        if (blocks.isEmpty()) return trimmedRange(node)
        return TextRange(blocks.first().textRange.startOffset, blocks.last().textRange.endOffset)
    }

    override fun getSubBlocks(): List<Block> = children
    override fun getWrap(): Wrap? = null
    override fun getIndent(): Indent = indent
    override fun getAlignment(): Alignment? = null
    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        if (child1 is MatlabBlock && child2 is MatlabBlock) spacings.between(node, child1.node, child2.node) else null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val type = node.elementType
        val indent = when {
            type in CONTAINERS || type == BLOCK -> Indent.getNormalIndent()
            node.treeParent == null -> Indent.getNoneIndent()
            else -> Indent.getContinuationWithoutFirstIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isIncomplete(): Boolean = false
    override fun isLeaf(): Boolean = children.isEmpty()
}

/** Whether [node] has text other than whitespace and line breaks. */
private fun hasContent(node: ASTNode): Boolean {
    if (node.elementType == TokenType.WHITE_SPACE || node.elementType == NEWLINE) return false
    return node.chars.any { !it.isWhitespace() }
}

private fun trimmedRange(node: ASTNode): TextRange {
    val chars = node.chars
    var start = 0
    var end = chars.length
    while (start < end && chars[start].isWhitespace()) start++
    while (end > start && chars[end - 1].isWhitespace()) end--
    return TextRange(node.startOffset + start, node.startOffset + end)
}

private class Spacings(settings: CodeStyleSettings) {
    private val keepBlankLines = settings.getCommonSettings(MatlabLanguage.INSTANCE).KEEP_BLANK_LINES_IN_CODE
    private val one = Spacing.createSpacing(1, 1, 0, true, keepBlankLines)
    private val none = Spacing.createSpacing(0, 0, 0, true, keepBlankLines)

    private val binaryOperators = mapOf<IElementType, IElementType>(
        EQUAL to EQUAL_EXPR, NOT_EQUAL to NOT_EQUAL_EXPR, LESS to LESS_EXPR, LESS_OR_EQUAL to LESS_OR_EQUAL_EXPR,
        MORE to MORE_EXPR, MORE_OR_EQUAL to MORE_OR_EQUAL_EXPR, AND to AND_EXPR, OR to OR_EXPR,
        PLUS to PLUS_EXPR, MINUS to MINUS_EXPR)

    /** Where a comma separates items: one space after, none before. */
    private val commaLists = TokenSet.create(ARGUMENTS, PARAMETERS, RETURN_VALUES, ATTRIBUTES, PROPERTY_SIZE)

    fun between(parent: ASTNode, left: ASTNode, right: ASTNode): Spacing? {
        val parentType = parent.elementType
        val leftType = left.elementType
        val rightType = right.elementType
        // Never join or split lines, and leave whitespace that holds a line break or comment alone
        if (rightType == COMMENT || leftType == ELLIPSIS) return null
        return when {
            rightType == COMMA && parentType in commaLists -> none
            leftType == COMMA && parentType in commaLists -> one
            // f(Name=value) keeps its style
            (leftType == ASSIGN || rightType == ASSIGN) && parentType == ASSIGN_EXPR && parent.treeParent?.elementType == ARGUMENTS -> null
            (leftType == ASSIGN || rightType == ASSIGN) && (parentType == ASSIGN_EXPR || parentType == ARGUMENT_VALIDATION ||
                    parentType == PROPERTY) -> one
            binaryOperators[leftType] == parentType || binaryOperators[rightType] == parentType -> operatorSpacing(parent, left, right)
            // x = 1 ; -> x = 1;
            rightType == SEMICOLON && (parentType == BLOCK || parent.treeParent == null) -> none
            else -> null
        }
    }

    /**
     * `a+b` and `a + b` become `a + b`. For + and - written with a space only before them (`a -b`), MATLAB
     * may call `a` with the text '-b', so they're left as they are.
     */
    private fun operatorSpacing(parent: ASTNode, left: ASTNode, right: ASTNode): Spacing? {
        val operator = if (binaryOperators.containsKey(left.elementType)) left else right
        if (operator.elementType == PLUS || operator.elementType == MINUS) {
            val before = operator.treePrev?.elementType == TokenType.WHITE_SPACE
            val after = operator.treeNext?.elementType == TokenType.WHITE_SPACE
            if (before && !after) return null
            // x(end-1), 1:n-1: usually written without spaces
            if (inIndexOrRange(parent)) return null
        }
        return one
    }

    private fun inIndexOrRange(node: ASTNode): Boolean {
        var n: ASTNode? = node
        while (n != null && n.elementType != BLOCK && n.treeParent != null) {
            if (n.elementType == ARGUMENTS || n.elementType == RANGE_EXPR || n.elementType == CELL_ARRAY_ACCESS_EXPR) return true
            n = n.treeParent
        }
        return false
    }
}
