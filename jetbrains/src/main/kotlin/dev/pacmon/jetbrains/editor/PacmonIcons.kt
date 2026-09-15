package dev.pacmon.jetbrains.editor

import com.intellij.openapi.util.IconLoader

object PacmonIcons {
    @JvmField
    val Mark = IconLoader.getIcon("/icons/pacmon.svg", PacmonIcons::class.java)

    @JvmField
    val Documented = IconLoader.getIcon("/icons/pacmon-documented.svg", PacmonIcons::class.java)

    @JvmField
    val Undocumented = IconLoader.getIcon("/icons/pacmon-undocumented.svg", PacmonIcons::class.java)

    fun forNote(hasNote: Boolean) = if (hasNote) Documented else Undocumented
}
