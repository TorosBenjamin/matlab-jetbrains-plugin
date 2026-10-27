package com.github.kornilova203.matlab.navigation

import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.stub.MatlabClassDeclarationIndex
import com.github.kornilova203.matlab.stub.MatlabFunctionDeclarationIndex
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

/** Navigate | Class (Ctrl+N): MATLAB classes. */
class MatlabGotoClassContributor : MatlabGotoContributor<MatlabClassDeclaration>(
    MatlabClassDeclarationIndex.KEY, MatlabClassDeclaration::class.java)

/** Navigate | Symbol (Ctrl+Alt+Shift+N): MATLAB functions and methods. */
class MatlabGotoSymbolContributor : MatlabGotoContributor<MatlabFunctionDeclaration>(
    MatlabFunctionDeclarationIndex.KEY, MatlabFunctionDeclaration::class.java)

abstract class MatlabGotoContributor<T : PsiElement>(
    private val key: StubIndexKey<String, T>,
    private val type: Class<T>,
) : ChooseByNameContributorEx {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        StubIndex.getInstance().processAllKeys(key, processor, scope, filter)
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        StubIndex.getInstance().processElements(key, name, parameters.project, parameters.searchScope, parameters.idFilter, type) {
            (it as? NavigationItem)?.let(processor::process) ?: true
        }
    }
}
