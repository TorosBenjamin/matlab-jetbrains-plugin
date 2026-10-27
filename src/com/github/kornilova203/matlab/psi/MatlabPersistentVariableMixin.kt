package com.github.kornilova203.matlab.psi

import com.intellij.lang.ASTNode

/** Variable declared with `persistent`: visible only inside its function. */
abstract class MatlabPersistentVariableMixin(node: ASTNode) : MatlabDeclarationBase(node) {
    override val visibleOutsideFunction = false
}
