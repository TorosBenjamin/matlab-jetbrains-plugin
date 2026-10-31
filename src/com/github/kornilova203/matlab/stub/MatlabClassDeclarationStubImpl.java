package com.github.kornilova203.matlab.stub;

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

import java.util.List;

public class MatlabClassDeclarationStubImpl extends StubBase<MatlabClassDeclaration> implements MatlabClassDeclarationStub {
    private final String myName;
    private final List<String> mySuperClassNames;

    public MatlabClassDeclarationStubImpl(StubElement parent, IStubElementType elementType, String name, List<String> superClassNames) {
        super(parent, elementType);
        myName = name;
        mySuperClassNames = superClassNames;
    }

    @Override
    public List<String> getSuperClassNames() {
        return mySuperClassNames;
    }

    @Override
    public String getName() {
        return myName;
    }
}
