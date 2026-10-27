// This is a generated file. Not intended for manual editing.
package com.github.kornilova203.matlab.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.github.kornilova203.matlab.stub.MatlabFunctionDeclarationStub;

public interface MatlabFunctionDeclaration extends PsiElement, StubBasedPsiElement<MatlabFunctionDeclarationStub> {

  @Nullable
  MatlabBlock getBlock();

  @Nullable
  MatlabGetterOrSetterModifier getGetterOrSetterModifier();

  @Nullable
  MatlabParameters getParameters();

  @Nullable
  MatlabReturnValues getReturnValues();

}
