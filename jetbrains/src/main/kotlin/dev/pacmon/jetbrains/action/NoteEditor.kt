package dev.pacmon.jetbrains.action

import com.intellij.openapi.project.Project
import dev.pacmon.jetbrains.editor.DependencyRef
import dev.pacmon.jetbrains.ui.PacmonToolWindowService

object NoteEditor {
    fun open(project: Project, dependency: DependencyRef) {
        project.getService(PacmonToolWindowService::class.java).open(dependency)
    }
}
