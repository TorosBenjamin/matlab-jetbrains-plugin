// This is a generated file. Not intended for manual editing.
package com.github.kornilova203.matlab.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.github.kornilova203.matlab.psi.MatlabTypes.*;
import com.github.kornilova203.matlab.psi.MatlabStubbedFunctionDeclaration;
import com.github.kornilova203.matlab.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.github.kornilova203.matlab.stub.MatlabFunctionDeclarationStub;

public class MatlabFunctionDeclarationImpl extends MatlabStubbedFunctionDeclaration implements MatlabFunctionDeclaration {

  public MatlabFunctionDeclarationImpl(ASTNode node) {
    super(node);
  }

  public MatlabFunctionDeclarationImpl(MatlabFunctionDeclarationStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull MatlabVisitor visitor) {
    visitor.visitFunctionDeclaration(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MatlabVisitor) accept((MatlabVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public MatlabBlock getBlock() {
    return PsiTreeUtil.getChildOfType(this, MatlabBlock.class);
  }

  @Override
  @Nullable
  public MatlabGetterOrSetterModifier getGetterOrSetterModifier() {
    return PsiTreeUtil.getChildOfType(this, MatlabGetterOrSetterModifier.class);
  }

  @Override
  @Nullable
  public MatlabParameters getParameters() {
    return PsiTreeUtil.getChildOfType(this, MatlabParameters.class);
  }

  @Override
  @Nullable
  public MatlabReturnValues getReturnValues() {
    return PsiTreeUtil.getChildOfType(this, MatlabReturnValues.class);
  }

}
