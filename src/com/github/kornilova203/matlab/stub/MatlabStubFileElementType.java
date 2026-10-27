package com.github.kornilova203.matlab.stub;

import com.github.kornilova203.matlab.MatlabLanguage;
import com.intellij.psi.tree.IStubFileElementType;

public class MatlabStubFileElementType extends IStubFileElementType<MatlabFileStub> {
    public MatlabStubFileElementType() {
        super("MATLAB_FILE", MatlabLanguage.Companion.getINSTANCE());
    }

    @Override
    public int getStubVersion() {
        return MatlabIndexVersion.INDEX_VERSION;
    }
}
