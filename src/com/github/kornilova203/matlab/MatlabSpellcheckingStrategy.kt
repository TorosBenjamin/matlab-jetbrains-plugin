package com.github.kornilova203.matlab

import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer

/** Spell-checks comments, declared names (the default) and string literals. */
class MatlabSpellcheckingStrategy : SpellcheckingStrategy() {
    override fun getTokenizer(element: PsiElement): Tokenizer<*> = when (element.elementType) {
        MatlabTypes.SINGLE_QUOTE_STRING, MatlabTypes.DOUBLE_QUOTE_STRING -> TEXT_TOKENIZER
        else -> super.getTokenizer(element)
    }
}
