package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.ui.PacmonToolWindowService

class ShowCoverageAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = DependencyPsi.isPackageJson(event.getData(CommonDataKeys.PSI_FILE))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.getService(PacmonToolWindowService::class.java).showDashboard()
    }
}
