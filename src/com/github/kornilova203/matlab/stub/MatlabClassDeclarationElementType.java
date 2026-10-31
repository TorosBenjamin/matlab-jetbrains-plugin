package com.github.kornilova203.matlab.stub;

import com.github.kornilova203.matlab.MatlabLanguage;
import com.github.kornilova203.matlab.psi.MatlabClassDeclaration;
import com.github.kornilova203.matlab.psi.MatlabInheritance;
import com.github.kornilova203.matlab.psi.MatlabTypes;
import com.github.kornilova203.matlab.psi.impl.MatlabClassDeclarationImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MatlabClassDeclarationElementType extends IStubElementType<MatlabClassDeclarationStub, MatlabClassDeclaration> {
    public MatlabClassDeclarationElementType(String debugName) {
        super(debugName, MatlabLanguage.Companion.getINSTANCE());
    }

    @Override
    public MatlabClassDeclaration createPsi(@NotNull MatlabClassDeclarationStub stub) {
        return new MatlabClassDeclarationImpl(stub, (IStubElementType) MatlabTypes.CLASS_DECLARATION);
    }

    @NotNull
    @Override
    public MatlabClassDeclarationStub createStub(@NotNull MatlabClassDeclaration psi, StubElement parentStub) {
        MatlabClassDeclarationImpl impl = (MatlabClassDeclarationImpl)psi;
        List<String> superClasses = new ArrayList<>();
        for (String qualified : MatlabInheritance.INSTANCE.superClassNames(impl)) {
            superClasses.add(qualified.substring(qualified.lastIndexOf('.') + 1));
        }
        return new MatlabClassDeclarationStubImpl(parentStub, (IStubElementType)MatlabTypes.CLASS_DECLARATION, impl.getName(), superClasses);
    }

    @Override
    public @NotNull String getExternalId() {
        return "MatlabClassDeclaration";
    }

    @Override
    public void serialize(@NotNull MatlabClassDeclarationStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeVarInt(stub.getSuperClassNames().size());
        for (String name : stub.getSuperClassNames()) dataStream.writeName(name);
    }

    @NotNull
    @Override
    public MatlabClassDeclarationStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        int count = dataStream.readVarInt();
        List<String> superClasses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) superClasses.add(dataStream.readNameString());
        return new MatlabClassDeclarationStubImpl(parentStub, this, name, superClasses);
    }

    @Override
    public void indexStub(@NotNull MatlabClassDeclarationStub stub, @NotNull IndexSink sink) {
        if (stub.getName() != null) {
            sink.occurrence(MatlabClassDeclarationIndex.KEY, stub.getName());
        }
        for (String superClass : stub.getSuperClassNames()) {
            sink.occurrence(MatlabSubclassIndex.KEY, superClass);
        }
    }
}
