package dev.pacmon.jetbrains.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import dev.pacmon.jetbrains.service.PacmonProjectService
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class PacmonConfigurable(private val project: Project) : Configurable {
    private val monorepoMode = JComboBox(arrayOf("nearest", "rootOnly"))
    private val inlineSource = JComboBox(arrayOf("human-first", "ai-first", "human-only", "ai-only"))
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Pacmon"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Notes file selection:"), monorepoMode)
            .addLabeledComponent(JBLabel("Inline note source:"), inlineSource)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = service().state
        return monorepoMode.selectedItem != settings.monorepoMode || inlineSource.selectedItem != settings.inlineSource
    }

    override fun apply() {
        val service = service()
        val settings = service.state
        settings.monorepoMode = monorepoMode.selectedItem as String
        settings.inlineSource = inlineSource.selectedItem as String
        service.settingsChanged()
    }

    override fun reset() {
        val settings = service().state
        monorepoMode.selectedItem = settings.monorepoMode
        inlineSource.selectedItem = settings.inlineSource
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun service(): PacmonProjectService = project.getService(PacmonProjectService::class.java)
}
