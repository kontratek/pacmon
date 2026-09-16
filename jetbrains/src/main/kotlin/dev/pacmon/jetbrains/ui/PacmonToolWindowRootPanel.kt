package dev.pacmon.jetbrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.awt.CardLayout
import javax.swing.JPanel

class PacmonToolWindowRootPanel(project: Project) : JPanel(CardLayout()), Disposable {
    private val cards = layout as CardLayout
    private val dashboard = PacmonDashboardPanel(project)
    private val note = PacmonToolWindowPanel(project)

    init {
        add(dashboard, DASHBOARD)
        add(note, NOTE)
        showDashboard()
    }

    fun showDashboard() {
        note.flushPending()
        dashboard.refresh()
        cards.show(this, DASHBOARD)
    }

    fun showTarget(target: PacmonTarget) {
        note.showTarget(target)
        cards.show(this, NOTE)
    }

    fun externalNotesChanged(paths: Set<String>) {
        note.externalNotesChanged(paths)
        dashboard.refresh()
    }

    fun flushPending() = note.flushPending()

    override fun dispose() = note.dispose()

    private companion object {
        const val DASHBOARD = "dashboard"
        const val NOTE = "note"
    }
}
