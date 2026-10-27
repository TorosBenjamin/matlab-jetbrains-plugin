package com.github.kornilova203.matlab.psi

import com.github.kornilova203.matlab.psi.types.MatlabType
import com.github.kornilova203.matlab.psi.types.MatlabTypeUnknown
import com.github.kornilova203.matlab.psi.types.inferVariableType
import com.github.kornilova203.matlab.psi.types.ownScope
import com.intellij.lang.ASTNode

/** Variable declared with `persistent`: visible only inside its function. */
abstract class MatlabPersistentVariableMixin(node: ASTNode) : MatlabDeclarationBase(node), MatlabTypedExpr {
    override val visibleOutsideFunction = false

    /** The class assigned to it in the function, e.g. `inst = Logger();` (the singleton pattern). */
    override fun getType(): MatlabType = name?.let { inferVariableType(it, ownScope(this)) } ?: MatlabTypeUnknown()
}
