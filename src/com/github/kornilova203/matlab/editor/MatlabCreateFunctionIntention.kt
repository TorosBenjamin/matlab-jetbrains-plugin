package com.github.kornilova203.matlab.editor

import com.github.kornilova203.matlab.MatlabFileType
import com.github.kornilova203.matlab.MatlabLibraryDocs
import com.github.kornilova203.matlab.MatlabReference
import com.github.kornilova203.matlab.foreign.MatlabForeignReference
import com.github.kornilova203.matlab.psi.*
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.elementType

/**
 * On a call of a function that doesn't exist (`y = scale(x, 2)`), creates it:
 * `function y = scale(x, arg2)` with the call's argument and output names.
 */
abstract class MatlabCreateFunctionIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Create function"
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val call = unresolvedCall(element) ?: return false
        text = text(call.name)
        return isAvailableFor(call)
    }

    protected open fun isAvailableFor(call: Call): Boolean = true
    protected abstract fun text(name: String): String

    /** A call like `name(a, b)` or the command `name arg` of a function that isn't defined anywhere. */
    class Call(val ref: MatlabRefExpr, val name: String, val parameters: List<String>, val outputs: List<String>) {
        /** `function [a, b] = name(x, y)` with an empty body. */
        fun code(indent: String = ""): String {
            val out = when (outputs.size) {
                0 -> ""
                1 -> outputs[0] + " = "
                else -> outputs.joinToString(", ", "[", "] = ")
            }
            return "${indent}function $out$name(${parameters.joinToString(", ")})\n$indent    \n${indent}end\n"
        }
    }

    companion object {
        fun unresolvedCall(element: PsiElement): Call? {
            if (element.elementType != MatlabTypes.IDENTIFIER) return null
            val ref = element.parent as? MatlabRefExpr ?: return null
            val name = ref.text
            val parent = ref.parent
            val call = parent as? MatlabFunctionExpr
            val command = parent as? MatlabCommandStatement
            if (call?.firstChild != ref && command?.refExpr != ref) return null
            if (call?.parent is MatlabQualifiedExpr) return null  // obj.method(x)
            if (MatlabLibraryDocs.isKnown(name) || MatlabReference(ref).resolve() != null) return null
            // A MEX file or a Java class imported by the file
            if (ref.references.any { it is MatlabForeignReference<*> && it.multiResolve(false).isNotEmpty() }) return null
            val parameters = call?.arguments?.children.orEmpty().mapIndexed { i, arg ->
                arg.text.takeIf { arg is MatlabRefExpr && it.matches(IDENTIFIER) } ?: "arg${i + 1}"
            }.let(::unique)
            val outputs = (call?.parent as? MatlabAssignExpr)?.takeIf { it.right == call }?.let { assign ->
                val left = assign.left
                when {
                    left is MatlabRefExpr -> listOf(left.text)
                    left is MatlabLiteralExpr && left.matrixLiteral != null ->
                        left.matrixLiteral!!.matrixRowList.flatMap { it.matrixItemList }.mapIndexed { i, item ->
                            item.text.trim().takeIf { it.matches(IDENTIFIER) } ?: "out${i + 1}"
                        }
                    else -> listOf("out")
                }
            }.orEmpty()
            return Call(ref, name, parameters, unique(outputs))
        }

        private val IDENTIFIER = Regex("[A-Za-z]\\w*")

        /** Names made distinct: x, x -> x, x2 */
        private fun unique(names: List<String>): List<String> {
            val seen = HashSet<String>()
            return names.map { name ->
                var candidate = name
                var i = 2
                while (!seen.add(candidate)) candidate = name + i++
                candidate
            }
        }
    }
}

/** Create function 'name' in name.m (next to the current file), which is how MATLAB finds functions. */
class MatlabCreateFunctionFileIntention : MatlabCreateFunctionIntention() {
    override fun text(name: String) = "Create function '$name' in $name.m"

    override fun isAvailableFor(call: Call): Boolean {
        val dir = call.ref.containingFile.originalFile.containingDirectory ?: return false
        return dir.findFile("${call.name}.m") == null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val call = unresolvedCall(element) ?: return
        val dir = call.ref.containingFile.originalFile.containingDirectory ?: return
        val created = WriteCommandAction.writeCommandAction(project).withName(text).compute<com.intellij.psi.PsiFile, RuntimeException> {
            val file = PsiFileFactory.getInstance(project).createFileFromText("${call.name}.m", MatlabFileType, call.code())
            dir.add(file) as com.intellij.psi.PsiFile
        }
        // Open the new file with the caret in the function's body
        val virtualFile = created.virtualFile ?: return
        val bodyOffset = created.text.indexOf('\n') + 5
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, bodyOffset), true)
    }
}

/** Create local function 'name' at the end of the current file. */
class MatlabCreateLocalFunctionIntention : MatlabCreateFunctionIntention() {
    override fun text(name: String) = "Create local function '$name'"

    // Only changes the current file, so it runs in the platform's write action (which also checks the file is writable)
    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val call = unresolvedCall(element) ?: return
        editor ?: return
        val document = editor.document
        val text = document.charsSequence
        val separator = when {
            text.isEmpty() -> ""
            text.endsWith("\n\n") -> ""
            text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val start = document.textLength + separator.length
        document.insertString(document.textLength, separator + call.code())
        PsiDocumentManager.getInstance(project).commitDocument(document)
        editor.caretModel.moveToOffset(document.getLineEndOffset(document.getLineNumber(start) + 1))
    }
}
