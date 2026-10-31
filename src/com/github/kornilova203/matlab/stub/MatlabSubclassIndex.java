package com.github.kornilova203.matlab.stub;

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

/** Classes by the names of their superclasses (without packages): Shape finds `classdef Circle < Shape`. */
public class MatlabSubclassIndex extends StringStubIndexExtension<MatlabClassDeclaration> {
    public static final StubIndexKey<String, MatlabClassDeclaration> KEY = StubIndexKey.createIndexKey("matlab.class.subclass");

    @Override
    public @NotNull StubIndexKey<String, MatlabClassDeclaration> getKey() {
        return KEY;
    }

    @Override
    public int getVersion() {
        return super.getVersion() + MatlabIndexVersion.INDEX_VERSION;
    }
}
