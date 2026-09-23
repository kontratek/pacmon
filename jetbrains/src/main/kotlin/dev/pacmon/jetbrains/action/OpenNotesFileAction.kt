package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Opens the notes file for the dependency manifest in play. Enabled wherever you are
 * in the project, not only inside a manifest: the VS Code command falls
 * back to the active editor, then to the workspace's own manifest, and this
 * one does the same through [PacmonCommands.findNotesFile].
 */
class OpenNotesFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        PacmonCommands.openNotesFile(project)
    }
}
