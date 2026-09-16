package dev.pacmon.jetbrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import dev.pacmon.jetbrains.editor.DependencyRef
import dev.pacmon.jetbrains.service.PacmonNotesListener

data class PacmonTarget(
    val packageJson: com.intellij.openapi.vfs.VirtualFile,
    val name: String,
    val section: String,
)

@Service(Service.Level.PROJECT)
class PacmonToolWindowService(private val project: Project) : Disposable {
    private var panel: PacmonToolWindowRootPanel? = null
    private var pendingTarget: PacmonTarget? = null
    private var openingDependency = false
    private var wasVisible = false

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(PacmonNotesListener.TOPIC, PacmonNotesListener { paths ->
            panel?.externalNotesChanged(paths)
        })
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID) ?: return
                val visible = toolWindow.isVisible
                if (visible && !wasVisible && !openingDependency) panel?.showDashboard()
                if (!visible && wasVisible) panel?.flushPending()
                wasVisible = visible
            }
        })
    }

    fun attach(value: PacmonToolWindowRootPanel) {
        panel = value
        pendingTarget?.let(value::showTarget) ?: value.showDashboard()
    }

    fun open(dependency: DependencyRef) {
        val packageJson = dependency.property.containingFile.virtualFile ?: return
        val target = PacmonTarget(packageJson, dependency.name, dependency.section)
        pendingTarget = target
        openingDependency = true
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            openingDependency = false
            return
        }
        toolWindow.activate {
            panel?.showTarget(target)
            openingDependency = false
        }
    }

    fun showDashboard() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate {
            panel?.showDashboard()
        }
    }

    override fun dispose() {
        panel?.flushPending()
        panel?.dispose()
        panel = null
    }

    companion object {
        const val TOOL_WINDOW_ID = "Pacmon"
    }
}
