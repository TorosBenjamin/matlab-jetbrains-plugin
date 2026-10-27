// This is a generated file. Not intended for manual editing.
package com.github.kornilova203.matlab.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.github.kornilova203.matlab.stub.MatlabClassDeclarationStub;

public interface MatlabClassDeclaration extends PsiElement, StubBasedPsiElement<MatlabClassDeclarationStub> {

  @Nullable
  MatlabAttributes getAttributes();

  @NotNull
  List<MatlabEnumerationBlock> getEnumerationBlockList();

  @NotNull
  List<MatlabEventsBlock> getEventsBlockList();

  @NotNull
  List<MatlabMethodsBlock> getMethodsBlockList();

  @NotNull
  List<MatlabPropertiesBlock> getPropertiesBlockList();

  @Nullable
  MatlabSuperClasses getSuperClasses();

}
