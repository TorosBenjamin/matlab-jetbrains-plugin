package com.github.kornilova203.matlab.completion

import com.github.kornilova203.matlab.completion.MatlabKeywordCompletionContributor.Companion.M
import com.github.kornilova203.matlab.psi.MatlabClassDeclaration
import com.github.kornilova203.matlab.psi.MatlabPackages
import com.github.kornilova203.matlab.psi.MatlabQualifiedExpr
import com.github.kornilova203.matlab.psi.MatlabRefExpr
import com.github.kornilova203.matlab.psi.MatlabTypedExpr
import com.github.kornilova203.matlab.psi.types.memberOwnerClass
import com.github.kornilova203.matlab.processDeclarationsInClass
import com.github.kornilova203.matlab.psi.MatlabDeclaration
import com.github.kornilova203.matlab.psi.MatlabFunctionDeclaration
import com.github.kornilova203.matlab.stub.MatlabClassDeclarationIndex
import com.github.kornilova203.matlab.stub.MatlabFunctionDeclarationIndex
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class MatlabReferenceCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC,
                M,
                object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                        val processor = MatlabCompletionScopeProcessor(result)
                        if (completeMember(parameters.position, processor)) return
                        val position = parameters.originalPosition ?: return
                        PsiTreeUtil.treeWalkUp(processor, position, parameters.originalFile, ResolveState.initial())
                        addCompletionsFromStubs(position, result)
                    }
                })
    }

    /**
     * After a dot (`obj.<caret>`) offer the members of the object's class, not the names in scope.
     * Returns true if the caret is after a dot.
     */
    private fun completeMember(position: PsiElement, processor: MatlabCompletionScopeProcessor): Boolean {
        val ref = position.parent as? MatlabRefExpr ?: return false
        val qualified = ref.parent as? MatlabQualifiedExpr ?: return false
        if (qualified.lastChild != ref) return false
        val pkg = MatlabPackages.dottedName(qualified.firstChild)
        if (pkg != null && MatlabPackages.isPackage(position.project, pkg)) {
            addPackageMembers(position.project, pkg, processor.result)
            return true
        }
        val type = (qualified.firstChild as? MatlabTypedExpr)?.getType()?.let(::memberOwnerClass)
        if (type != null) processDeclarationsInClass(processor, ResolveState.initial(), type)
        return true
    }

    private fun addCompletionsFromStubs(position: PsiElement, result: CompletionResultSet) {
        val project = position.project
        // Packages, and names brought in by import statements
        for (pkg in MatlabPackages.rootPackages(project)) {
            result.addElement(LookupElementBuilder.create(pkg).withIcon(AllIcons.Nodes.Package))
        }
        val imports = MatlabPackages.imports(position)
        for (pkg in imports.packages) addPackageMembers(project, pkg, result)
        val stubs = mutableListOf<LookupElement>()
        resolveIndex(position, MatlabClassDeclarationIndex.KEY, MatlabClassDeclaration::class.java, stubs) { declaration ->
            return@resolveIndex LookupElementBuilder.create(declaration).withIcon(AllIcons.Nodes.Class)
        }
        resolveIndex(position, MatlabFunctionDeclarationIndex.KEY, MatlabFunctionDeclaration::class.java, stubs) { declaration -> createFunctionLookupElement(declaration) }
        result.addAllElements(stubs)
    }

    private fun <Psi : PsiElement?> resolveIndex(position: PsiElement, indexKey: StubIndexKey<String, Psi>, requiredClass: Class<Psi>, stubs: MutableList<LookupElement>, create: (MatlabDeclaration) -> LookupElement?) {
        val project = position.project
        // Collect keys first: querying the index while iterating its keys can deadlock.
        val scope = GlobalSearchScope.projectScope(project)
        val keys = StubIndex.getInstance().getAllKeys(indexKey, project)
        for (key in keys) {
            StubIndex.getInstance().processElements(indexKey, key, project, scope, requiredClass) { psiElement ->
                if (psiElement is MatlabDeclaration && psiElement.name + ".m" == psiElement.containingFile.name &&
                        MatlabPackages.isVisibleByPlainName(psiElement, position)) {
                    val element = create(psiElement)
                    if (element != null) {
                        stubs.add(element)
                    }
                }
                return@processElements true
            }
        }
    }

    companion object {
        fun addPackageMembers(project: Project, pkg: String, result: CompletionResultSet) {
            for (member in MatlabPackages.members(project, pkg)) {
                val icon = when (member.kind) {
                    MatlabPackages.Kind.PACKAGE -> AllIcons.Nodes.Package
                    MatlabPackages.Kind.CLASS -> AllIcons.Nodes.Class
                    MatlabPackages.Kind.FUNCTION -> AllIcons.Nodes.Function
                }
                result.addElement(LookupElementBuilder.create(member.name).withIcon(icon).withTypeText(pkg))
            }
        }
    }
}
