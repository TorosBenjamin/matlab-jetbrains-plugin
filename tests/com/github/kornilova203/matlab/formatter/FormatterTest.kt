package com.github.kornilova203.matlab.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FormatterTest : BasePlatformTestCase() {
    private fun format(code: String): String {
        myFixture.configureByText("main.m", code)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        return myFixture.editor.document.text
    }

    private fun assertFormatted(expected: String, code: String) = assertEquals(expected, format(code))

    private fun assertUnchanged(code: String) = assertEquals(code, format(code))

    fun testIndentsBlocks() = assertFormatted("""
        function r = f(x)
            if x > 0
                r = 1;
            elseif x < 0
                r = -1;
            else
                for i = 1:3
                    r = i;
                end
            end
        end
        """.trimIndent() + "\n", """
        function r = f(x)
        if x > 0
        r = 1;
             elseif x < 0
          r = -1;
        else
        for i = 1:3
        r = i;
        end
        end
        end
        """.trimIndent() + "\n")

    fun testSwitch() = assertFormatted("""
        switch n
            case 1
                disp('one')
            otherwise
                disp('other')
        end
        """.trimIndent() + "\n", """
        switch n
        case 1
        disp('one')
        otherwise
        disp('other')
        end
        """.trimIndent() + "\n")

    fun testClassdef() = assertFormatted("""
        classdef Shape
            properties
                W = 1
            end
            methods
                function obj = Shape(w)
                    obj.W = w;
                end
            end
        end
        """.trimIndent() + "\n", """
        classdef Shape
        properties
        W=1
        end
        methods
        function obj = Shape(w)
        obj.W=w;
        end
        end
        end
        """.trimIndent() + "\n")

    fun testTryCatchAndWhile() = assertFormatted("try\n    while a\n        a = a - 1;\n    end\ncatch e\n    disp(e)\nend\n",
        "try\nwhile a\na = a - 1;\nend\ncatch e\ndisp(e)\nend\n")

    fun testOperatorSpacing() = assertFormatted("x = a + b;\nok = x >= 1 && y ~= 2;\n", "x=a+b;\nok=x>=1&&y~=2;\n")

    fun testCommaSpacing() = assertFormatted("y = f(a, b, c);\n", "y = f(a ,b,c);\n")

    fun testSemicolon() = assertFormatted("x = 1;\n", "x = 1 ;\n")

    fun testCommentInBody() = assertFormatted("if a\n    % note\n    b = 1;\nend\n", "if a\n% note\nb = 1;\nend\n")

    fun testContinuationLine() = assertFormatted("x = a + ...\n        b;\n", "x = a + ...\nb;\n")

    fun testTopLevelUnindented() = assertFormatted("a = 1;\nb = 2;\n", "  a = 1;\n    b = 2;\n")

    fun testBlankLinesKept() = assertUnchanged("a = 1;\n\nb = 2;\n")

    // Whitespace that means something stays as written

    fun testMatrixUntouched() = assertUnchanged("m = [1 -2, 3-4\n     5  6];\n")

    fun testCellArrayUntouched() = assertUnchanged("c = {1 -2 'a'};\n")

    fun testCommandSyntaxUntouched() = assertUnchanged("hold on\nformat   long\n")

    fun testAmbiguousMinusUntouched() = assertUnchanged("y = a -b;\n")

    fun testIndexingAndRangeUntouched() = assertUnchanged("y = x(end-1);\nfor i = 1:n-1\n    z = i;\nend\n")

    fun testNameValueUntouched() = assertUnchanged("plot(x, y, LineWidth=2);\n")

    fun testStringsUntouched() = assertUnchanged("s = 'a  ,b';\nt = \"x=1\";\n")

    fun testTrailingCommentKept() = assertUnchanged("x = 1;   % one\n")
}
