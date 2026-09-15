package dev.pacmon.jetbrains.action

import com.intellij.openapi.project.Project
import dev.pacmon.jetbrains.editor.DependencyRef
import dev.pacmon.jetbrains.service.PacmonProjectService
import dev.pacmon.jetbrains.ui.NoteEditorDialog

object NoteEditor {
    fun open(project: Project, dependency: DependencyRef) {
        val packageJson = dependency.property.containingFile.virtualFile ?: return
        val service = project.getService(PacmonProjectService::class.java)
        val initial = service.noteFor(packageJson, dependency.name)?.layers?.human.orEmpty()
        val dialog = NoteEditorDialog(project, dependency.name, initial)
        if (!dialog.showAndGet()) return
        service.saveHumanNote(packageJson, dependency.name, dialog.noteText)
    }
}
