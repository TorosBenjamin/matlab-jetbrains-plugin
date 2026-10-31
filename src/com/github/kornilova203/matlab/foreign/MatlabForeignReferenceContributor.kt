package com.github.kornilova203.matlab.foreign

import com.github.kornilova203.matlab.psi.*
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.ProcessingContext

/**
 * References from MATLAB code into Python, Java and C/C++ (see [foreignTarget]):
 *   py.mod.func(x)               Python module, function, class or method
 *   java.util.ArrayList()        Java class or member, also after `import com.example.*`
 *   foo(x)                       MEX gateway in foo.c / foo.cpp / ...
 *   calllib('lib', 'add', ...)   C function in the header passed to loadlibrary
 *   loadlibrary('lib', 'lib.h'), mex foo.c, pyrunfile('script.py'), javaObject('com.example.Util', ...)
 */
class MatlabForeignReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(MatlabRefExpr::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val ref = element as MatlabRefExpr
                val chain = qualifiedChain(ref)
                return when {
                    chain == null -> if (isPlainCall(ref)) arrayOf(PlainNameReference(ref)) else PsiReference.EMPTY_ARRAY
                    chain.size >= 2 -> arrayOf(QualifiedNameReference(ref, chain))
                    else -> PsiReference.EMPTY_ARRAY
                }
            }
        })
        registrar.registerReferenceProvider(psiElement(MatlabLiteralExpr::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
                stringArgumentReferences(element as MatlabLiteralExpr)
        })
        registrar.registerReferenceProvider(psiElement(MatlabCommandArguments::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
                commandArgumentReferences(element as MatlabCommandArguments)
        })
    }

    companion object {
        /**
         * Names from the start of a chain of names and dots up to [ref]: [py, mod, func] for `func` in
         * `py.mod.func(x)`. Null if [ref] isn't after a dot, or something other than a name is before it.
         */
        fun qualifiedChain(ref: MatlabRefExpr): List<String>? {
            var node: PsiElement = ref
            val parent = ref.parent
            if (parent is MatlabFunctionExpr && parent.firstChild == ref) node = parent  // a.b.c(x)
            val qualified = node.parent as? MatlabQualifiedExpr ?: return null
            if (qualified.lastChild != node) return null
            val left = MatlabPackages.dottedName(qualified.firstChild) ?: return null
            return left.split('.') + ref.text
        }

        /** A name used on its own, like a function call: not a field after a dot, not assigned to. */
        private fun isPlainCall(ref: MatlabRefExpr): Boolean {
            var node: PsiElement = ref
            val parent = ref.parent
            if (parent is MatlabFunctionExpr && parent.firstChild == ref) node = parent
            val above = node.parent
            if (above is MatlabQualifiedExpr) return false
            return ref.isLeftPartOfAssign() == null
        }

        private fun stringArgumentReferences(literal: MatlabLiteralExpr): Array<PsiReference> {
            val value = stringValue(literal) ?: return PsiReference.EMPTY_ARRAY
            val args = literal.parent as? MatlabArguments ?: return PsiReference.EMPTY_ARRAY
            val call = args.parent as? MatlabFunctionExpr ?: return PsiReference.EMPTY_ARRAY
            val arguments = args.children
            val index = arguments.indexOf(literal)
            val range = TextRange(1, literal.textLength - 1)
            val reference: PsiReference? = when (call.expr.text) {
                "loadlibrary" -> if (index == 1) FileReference(literal, fileNameRange(range, value), value) else null
                "calllib" -> stringValue(arguments.getOrNull(0))?.takeIf { index == 1 }?.let { lib ->
                    StringReference(literal, range) { CSources.libraryFunction(it.project, lib, value, it.containingFile) }
                }
                "mex" -> if (CSources.isSourceFile(value)) FileReference(literal, fileNameRange(range, value), value) else null
                "pyrunfile" -> value.substringBefore(' ').takeIf { index == 0 && it.endsWith(".py") }?.let { script ->
                    FileReference(literal, fileNameRange(TextRange(1, 1 + script.length), script), script)
                }
                "javaObject", "javaObjectEDT", "javaArray" -> if (index == 0) javaClassReference(literal, range, value) else null
                "javaMethod", "javaMethodEDT" -> when (index) {
                    1 -> javaClassReference(literal, range, value)
                    0 -> stringValue(arguments.getOrNull(1))?.let { cls ->
                        StringReference(literal, range) { JavaClasses.resolve(it.project, cls.split('.') + value, it.containingFile) }
                    }
                    else -> null
                }
                else -> null
            }
            return if (reference != null) arrayOf(reference) else PsiReference.EMPTY_ARRAY
        }

        private fun javaClassReference(literal: MatlabLiteralExpr, range: TextRange, value: String): PsiReference =
            StringReference(literal, range) { JavaClasses.findClass(it.project, value.split('.'), it.containingFile) }

        /** `mex foo.c bar.c -lm`, `loadlibrary mylib mylib.h` */
        private fun commandArgumentReferences(arguments: MatlabCommandArguments): Array<PsiReference> {
            val command = (arguments.parent as? MatlabCommandStatement)?.refExpr?.text ?: return PsiReference.EMPTY_ARRAY
            if (command != "mex" && command != "loadlibrary") return PsiReference.EMPTY_ARRAY
            val words = Regex("[^\\s,;]+").findAll(arguments.text).toList()
            return words.withIndex().mapNotNull { (i, word) ->
                val isFile = when (command) {
                    "mex" -> !word.value.startsWith("-") && CSources.isSourceFile(word.value)
                    else -> i == 1
                }
                if (isFile) FileReference(arguments, fileNameRange(TextRange(word.range.first, word.range.last + 1), word.value), word.value)
                else null
            }.toTypedArray()
        }

        /** The part of [range] (holding [path]) after the last folder separator. */
        private fun fileNameRange(range: TextRange, path: String): TextRange {
            val start = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1
            return TextRange(range.startOffset + start, range.endOffset)
        }
    }
}

/** A reference whose targets are computed once per change of the code (cached like MATLAB's own). */
abstract class MatlabForeignReference<T : PsiElement>(element: T, range: TextRange) : PsiPolyVariantReferenceBase<T>(element, range, true) {
    protected abstract fun targets(): List<PsiElement>

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, false)

    // Targets are never MATLAB code: skip resolving when searching for usages of MATLAB declarations
    override fun isReferenceTo(element: PsiElement): Boolean = element !is MatlabDeclaration && super.isReferenceTo(element)

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private val RESOLVER = ResolveCache.PolyVariantResolver<MatlabForeignReference<*>> { ref, _ ->
            ref.targets().map { PsiElementResolveResult(it) }.toTypedArray()
        }
    }
}

/** `func` in `py.mod.func`, `ArrayList` in `java.util.ArrayList`, `parse` in `com.example.Util.parse`. */
class QualifiedNameReference(ref: MatlabRefExpr, private val chain: List<String>) :
    MatlabForeignReference<MatlabRefExpr>(ref, TextRange(0, ref.textLength)) {

    override fun targets(): List<PsiElement> {
        val project = element.project
        val file = element.containingFile
        if (chain[0] == "py") return listOfNotNull(PythonModules.resolve(project, chain.drop(1), file))
        // Java: only if the chain doesn't start with a MATLAB variable, function or package (s.field)
        val first = firstRef() ?: return emptyList()
        if (first.reference?.resolve() != null || MatlabPackages.isPackage(project, chain[0])) return emptyList()
        return listOfNotNull(JavaClasses.resolve(project, chain, file))
    }

    private fun firstRef(): MatlabRefExpr? {
        var node: PsiElement? = element.parent
        while (node != null && node !is MatlabQualifiedExpr) node = node.parent
        var left: PsiElement? = node?.firstChild
        while (left != null && left !is MatlabRefExpr) left = left.firstChild
        return left as? MatlabRefExpr
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        super.handleElementRename(newElementName.substringBeforeLast('.'))  // renamed module file utils.py: utils
}

/** `foo` in `foo(x)` that is a MEX file, or `ArrayList` after `import java.util.*`. */
class PlainNameReference(ref: MatlabRefExpr) : MatlabForeignReference<MatlabRefExpr>(ref, TextRange(0, ref.textLength)) {
    override fun targets(): List<PsiElement> {
        val own = element.reference?.resolve()
        // A variable, or a MATLAB function without a MEX file next to it
        if (own != null && own !is MatlabFunctionDeclaration && own !is MatlabClassDeclaration) return emptyList()
        val project = element.project
        val file = element.containingFile
        val name = element.text
        val targets = ArrayList<PsiElement>(CSources.mexGateways(project, name, file))
        if (own == null) {
            val imports = MatlabPackages.imports(element)
            val candidates = imports.names.filter { it.endsWith(".$name") }.map { it.split('.') } +
                    imports.packages.map { it.split('.') + name }
            candidates.firstNotNullOfOrNull { JavaClasses.findClass(project, it, file) }?.let { targets += it }
        }
        return targets
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        super.handleElementRename(newElementName.substringBeforeLast('.'))
}

/** A name inside a string, like 'add' in calllib('lib', 'add'). */
class StringReference(literal: PsiElement, range: TextRange, private val find: (PsiElement) -> PsiElement?) :
    MatlabForeignReference<PsiElement>(literal, range) {
    override fun targets(): List<PsiElement> = listOfNotNull(find(element))
}

/** A file name inside a string or command: 'mylib.h', mex foo.c. */
class FileReference(element: PsiElement, range: TextRange, private val path: String) : MatlabForeignReference<PsiElement>(element, range) {
    override fun targets(): List<PsiElement> = listOfNotNull(ForeignFiles.find(element.project, path, element.containingFile))
}
