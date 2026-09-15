package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.service.PacmonProjectService

class ShowCoverageAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = DependencyPsi.isPackageJson(event.getData(CommonDataKeys.PSI_FILE))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val packageJson = file.virtualFile ?: return
        val dependencies = DependencyPsi.all(file)
        val service = project.getService(PacmonProjectService::class.java)
        val documented = dependencies.filter { service.noteFor(packageJson, it.name) != null }
        val missing = dependencies.filterNot { dependency -> documented.any { it.name == dependency.name } }
        val percent = if (dependencies.isEmpty()) 100 else documented.size * 100 / dependencies.size
        val details = buildString {
            append("${documented.size} of ${dependencies.size} dependencies documented ($percent%).")
            if (missing.isNotEmpty()) {
                append("\n\nMissing notes:\n")
                append(missing.joinToString("\n") { "• ${it.name}" })
            }
        }
        Messages.showInfoMessage(project, details, "Pacmon documentation coverage")
    }
}
