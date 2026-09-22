package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.service.PacmonProjectService

class AddOrEditNoteAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val editor = event.getData(CommonDataKeys.EDITOR)
        val manifest = file?.virtualFile
        event.presentation.isEnabledAndVisible = file != null && editor != null && manifest != null &&
            DependencyPsi.isManifest(file) &&
            event.project?.getService(PacmonProjectService::class.java)
                ?.dependencyAt(manifest, editor.caretModel.offset, lineFallback = true) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val manifest = file.virtualFile ?: return
        val dependency = project.getService(PacmonProjectService::class.java)
            .dependencyAt(manifest, editor.caretModel.offset, lineFallback = true) ?: return
        NoteEditor.open(project, dependency, editor)
    }
}
