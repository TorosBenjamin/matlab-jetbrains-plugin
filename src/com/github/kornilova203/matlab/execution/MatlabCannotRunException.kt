package com.github.kornilova203.matlab.execution

import com.intellij.execution.ExecutionException


class MatlabCannotRunException(message: String) : ExecutionException(message) {
    companion object {
        fun interpreterNotSetUp(): MatlabCannotRunException {
            return MatlabCannotRunException(
                "MATLAB was not found. Set its path in Settings | Languages & Frameworks | MATLAB or in this run configuration.")
        }

        fun fileNotSetUp(): MatlabCannotRunException {
            return MatlabCannotRunException("File is not specified.")
        }
    }
}
