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
        else pkg?.let { addStructFields(position, it, processor.result) }  // s.<caret>: a struct
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

    /**
     * Fields of struct [variable] (`s` or `s.inner`) seen in the same function or script:
     * `s.name = ...`, uses like `s.age`, and `s = struct('name', ..., 'age', ...)`.
     */
    private fun addStructFields(position: PsiElement, variable: String, result: CompletionResultSet) {
        val scope = com.github.kornilova203.matlab.psi.types.ownScope(position)
        val fields = LinkedHashSet<String>()
        val prefix = "$variable."
        for (expr in PsiTreeUtil.findChildrenOfType(scope, MatlabQualifiedExpr::class.java)) {
            val name = MatlabPackages.dottedName(expr) ?: continue
            if (!name.startsWith(prefix)) continue
            val field = name.substring(prefix.length).substringBefore('.')
            if (field.isNotEmpty() && !field.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) fields += field
        }
        for (assign in PsiTreeUtil.findChildrenOfType(scope, com.github.kornilova203.matlab.psi.MatlabAssignExpr::class.java)) {
            if (assign.left?.text != variable) continue
            val call = assign.right as? com.github.kornilova203.matlab.psi.MatlabFunctionExpr ?: continue
            if (call.expr?.text != "struct") continue
            // struct('name', value, 'age', value): every other argument is a field name
            call.arguments.children.filterIndexed { i, _ -> i % 2 == 0 }
                .map { it.text.trim('\'', '"') }.filter { it.matches(Regex("[A-Za-z]\\w*")) }.forEach { fields += it }
        }
        fields.forEach { result.addElement(LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Field)) }
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
