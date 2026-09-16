package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.pacmon.jetbrains.core.AiInstructions
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Writes `.pacmon/AGENT-RULES.md` and, into whichever instruction files the
 * user picks (`AGENTS.md`, `CLAUDE.md`, …), the three-line pointer to it —
 * the JetBrains counterpart of the VS Code extension's
 * "Pacmon: Set Up AI Instructions" command.
 */
class SetupAiInstructionsAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = TargetsDialog(project)
        if (!dialog.showAndGet()) return
        val service = project.getService(PacmonProjectService::class.java)
        val written = service.setUpAiInstructions(dialog.selectedTargets())
        WindowManager.getInstance().getStatusBar(project)?.info =
            "Pacmon: AI instructions written to ${written.joinToString(", ")}."
    }

    private class TargetsDialog(private val project: Project) : DialogWrapper(project) {
        private val list = CheckBoxList<String>()

        init {
            title = "Set Up AI Instructions"
            val root = project.basePath?.replace('\\', '/')?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            for (target in AiInstructions.TARGETS) {
                val exists = target.split('/').fold(root) { dir, part -> dir?.findChild(part) } != null
                list.addItem(target, if (exists) "$target — exists, block will be updated" else "$target — will be created", exists || target == "AGENTS.md")
            }
            init()
        }

        fun selectedTargets(): List<String> = AiInstructions.TARGETS.filter(list::isItemSelected)

        override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(4)
            add(
                JBLabel("Write dependency-notes format instructions into:"),
                BorderLayout.NORTH,
            )
            list.preferredSize = java.awt.Dimension(420, 120)
            add(list, BorderLayout.CENTER)
        }
    }
}
