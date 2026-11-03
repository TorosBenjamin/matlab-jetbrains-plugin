package com.github.kornilova203.matlab

import com.intellij.openapi.util.IconLoader

object Icons {
    /**
     * MATLAB files and other MATLAB things inside the IDE: the original Matlab Support plugin's icon
     * (matlab_dark.svg in dark themes). The plugin's own logo, META-INF/pluginIcon.svg, is separate.
     */
    val Matlab = IconLoader.getIcon("/icons/matlab.svg", Icons::class.java.classLoader)
}
