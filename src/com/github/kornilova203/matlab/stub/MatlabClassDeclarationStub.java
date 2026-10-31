package com.github.kornilova203.matlab.stub;

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration;
import com.intellij.psi.stubs.StubElement;

import java.util.List;

public interface MatlabClassDeclarationStub extends StubElement<MatlabClassDeclaration> {
    String getName();

    /** Names of the superclasses without their packages: Base for `classdef A < pkg.Base`. */
    List<String> getSuperClassNames();
}
