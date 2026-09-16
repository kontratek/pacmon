package dev.pacmon.jetbrains.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PacmonToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PacmonToolWindowRootPanel(project)
        project.getService(PacmonToolWindowService::class.java).attach(panel)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
    }
}
