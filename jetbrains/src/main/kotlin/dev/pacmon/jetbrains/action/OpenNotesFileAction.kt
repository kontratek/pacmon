package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.service.PacmonProjectService

class OpenNotesFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = DependencyPsi.isPackageJson(event.getData(CommonDataKeys.PSI_FILE))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val packageJson = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = project.getService(PacmonProjectService::class.java)
        val notesFile = service.resolveNotesFile(packageJson)
        if (notesFile == null) {
            Messages.showInfoMessage(project, "No dependency notes file exists for this package.json.", "Pacmon")
        } else {
            service.open(notesFile)
        }
    }
}
