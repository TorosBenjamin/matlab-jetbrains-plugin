package com.github.kornilova203.matlab.editor.templates

import com.github.kornilova203.matlab.psi.MatlabFile
import com.github.kornilova203.matlab.psi.MatlabTypes
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.util.elementType

/** Live templates apply in MATLAB code, but not inside comments or strings. */
class MatlabTemplateContextType : TemplateContextType("MATLAB") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file as? MatlabFile ?: return false
        val offset = (context.startOffset - 1).coerceAtLeast(0)
        val type = file.findElementAt(offset).elementType
        return type != MatlabTypes.COMMENT && type != MatlabTypes.SINGLE_QUOTE_STRING && type != MatlabTypes.DOUBLE_QUOTE_STRING
    }
}
