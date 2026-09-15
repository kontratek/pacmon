package dev.pacmon.jetbrains.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import javax.swing.JComponent

class NoteEditorDialog(
    project: Project,
    dependency: String,
    initialText: String,
) : DialogWrapper(project) {
    private val editor = JBTextArea(initialText, 12, 64).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    val noteText: String
        get() = editor.text

    init {
        title = "Dependency note — $dependency"
        setOKButtonText("Save")
        init()
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(editor).apply {
        preferredSize = Dimension(640, 280)
    }

    override fun getPreferredFocusedComponent(): JComponent = editor
}
