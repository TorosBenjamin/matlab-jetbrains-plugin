package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.lexer.MatlabLexer
import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/** Finds TODO/FIXME in MATLAB comments for the TODO tool window and highlighting. */
class MatlabIndexPatternBuilder : IndexPatternBuilder {
    override fun getIndexingLexer(file: PsiFile): Lexer? = if (file is MatlabFile) MatlabLexer.getAdapter() else null
    override fun getCommentTokenSet(file: PsiFile): TokenSet? = if (file is MatlabFile) TokenSet.create(MatlabTypes.COMMENT) else null
    override fun getCommentStartDelta(tokenType: IElementType?): Int = 1  // the %
    override fun getCommentEndDelta(tokenType: IElementType?): Int = 0
}
