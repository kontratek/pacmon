package dev.pacmon.jetbrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import dev.pacmon.jetbrains.core.AgentNotes
import dev.pacmon.jetbrains.core.AgentProblem
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PacmonToolWindowPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {
    private data class Layers(val human: String, val agent: String)

    private val notes = project.getService(PacmonProjectService::class.java)
    private val saveAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val nameLabel = JBLabel("Select a dependency").apply {
        // Matches the VS Code panel's `.dep`: the editor font (dependency
        // names read as code, not prose), bold standing in for its 600 weight.
        val editorScheme = EditorColorsManager.getInstance().globalScheme
        font = Font(editorScheme.editorFontName, Font.BOLD, editorScheme.editorFontSize + 2)
    }
    private val sectionLabel = DependencySectionBadge()
    private val notesPathLabel = JBLabel("->  .pacmon/DEPENDENCY-NOTES.md").apply {
        // Matches `.meta`: smaller and muted, secondary to the title above it.
        font = font.deriveFont(font.size2D - 1f)
        foreground = JBColor.namedColor(
            "Label.disabledForeground",
            JBColor(0x6F737A, 0xA0A0A0),
        )
    }
    private val openFileButton = JButton("Open notes file").apply {
        // Matches the VS Code panel's secondary button: modest text, tight
        // padding, no accent color — a quiet affordance, not a call to action.
        font = font.deriveFont(font.size2D - 1f)
        margin = JBUI.insets(2, 10)
    }
    private val statusLabel = JBLabel("")
    private val conflictPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
    private val conflictLabel = JBLabel("Notes changed on disk while this panel has unsaved edits.")
    private val reloadButton = JButton("Reload")
    private val keepButton = JButton("Keep mine")
    private val humanLayer = NoteLayerPanel(
        "Your note",
        "Nothing written yet — click to write.",
        ::onInput,
        ::saveNow,
    )
    private val agentLayer = NoteLayerPanel(
        null,
        "No agent notes yet — click to add.",
        ::onInput,
        ::saveNow,
    )
    private val agentToggle = JButton("\u25B8 Agent notes")
    private val agentContent = JPanel()
    private val problemsLabel = JBLabel("")
    private val fixButton = JButton("Fix")
    private val problemsPanel = JPanel(BorderLayout(8, 0))
    private val generatedPanel = JPanel(BorderLayout())
    private val generatedPreview = JEditorPane("text/html", "").apply {
        isEditable = false
        isOpaque = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    private var target: PacmonTarget? = null
    private var baseline = Layers("", "")
    private var writing = false
    private var conflict = false
    private var agentExpanded = false
    private var disposed = false

    init {
        border = JBUI.Borders.empty()
        add(createHeader(), BorderLayout.NORTH)
        add(createBody(), BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)

        openFileButton.addActionListener { openNotesFile() }
        agentToggle.addActionListener { setAgentExpanded(!agentExpanded) }
        reloadButton.addActionListener { loadFromDisk(focusEmpty = false) }
        keepButton.addActionListener { saveNow() }
        fixButton.addActionListener {
            agentLayer.replaceText(AgentNotes.fix(agentLayer.text))
            onInput()
            saveNow()
        }
        showConflict(false)
        setAgentExpanded(false)
        generatedPanel.isVisible = false
    }

    private fun createHeader(): JPanel {
        val titleRow = ResponsiveHeaderRow(nameLabel, openFileButton)
        val metadataRow = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(sectionLabel, BorderLayout.WEST)
            add(notesPathLabel, BorderLayout.CENTER)
        }
        val stacked = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            titleRow.alignmentX = Component.LEFT_ALIGNMENT
            metadataRow.alignmentX = Component.LEFT_ALIGNMENT
            add(titleRow)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(metadataRow)
        }
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(10, 12),
            )
            add(stacked, BorderLayout.CENTER)
        }
    }

    private fun createBody(): JBScrollPane {
        problemsPanel.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.ORANGE, 1),
                JBUI.Borders.empty(6),
            )
            add(problemsLabel, BorderLayout.CENTER)
            add(fixButton, BorderLayout.EAST)
            isVisible = false
        }
        agentToggle.apply {
            horizontalAlignment = JButton.LEFT
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = JBColor.namedColor(
                "Label.disabledForeground",
                JBColor(0x6F737A, 0xA0A0A0),
            )
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            margin = JBUI.emptyInsets()
        }
        agentContent.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("<html>Written by AI agents as \"- key: value\" lines (rules in .pacmon/AGENT-RULES.md).<br>Edit if you must; agents revise these as they work.</html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.empty(5, 12, 0, 0)
            })
            agentLayer.alignmentX = Component.LEFT_ALIGNMENT
            add(agentLayer)
            add(problemsPanel)
        }
        generatedPanel.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(10)
            add(JBLabel("Generated (read-only)"), BorderLayout.NORTH)
            add(generatedPreview, BorderLayout.CENTER)
        }
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 12, 16, 12)
            humanLayer.alignmentX = Component.LEFT_ALIGNMENT
            add(humanLayer)
            add(agentToggle)
            add(agentContent)
            add(generatedPanel)
            add(Box.createVerticalGlue().apply { setAlignmentX(Component.LEFT_ALIGNMENT) })
        }
        return JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
    }

    private fun createFooter(): JPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLineTop(JBColor.border()),
            JBUI.Borders.empty(5, 10),
        )
        conflictPanel.apply {
            add(conflictLabel)
            add(reloadButton)
            add(keepButton)
        }
        add(statusLabel, BorderLayout.WEST)
        add(conflictPanel, BorderLayout.CENTER)
    }

    fun showTarget(value: PacmonTarget) {
        if (disposed) return
        flushPending()
        target = value
        nameLabel.text = value.name
        sectionLabel.text = value.section
        loadFromDisk(focusEmpty = true)
    }

    fun externalNotesChanged(paths: Set<String>) {
        if (disposed || writing || target == null) return
        val current = target ?: return
        val relevantPath = notes.resolveNotesFile(current.packageJson)?.path?.replace('\\', '/')
            ?: "${current.packageJson.parent.path.replace('\\', '/')}/${dev.pacmon.jetbrains.core.NotesCore.NOTES_RELATIVE_PATH}"
        if (relevantPath !in paths) return
        SwingUtilities.invokeLater {
            if (disposed || writing || target == null) return@invokeLater
            if (isDirty()) {
                saveAlarm.cancelAllRequests()
                conflict = true
                showConflict(true)
                statusLabel.text = "Resolve the external change before autosave continues."
            } else {
                loadFromDisk(focusEmpty = false, forceReset = false)
            }
        }
    }

    fun flushPending() {
        if (!disposed && isDirty()) saveNow()
    }

    internal fun setLayersForTest(human: String, agent: String) {
        humanLayer.replaceText(human)
        agentLayer.replaceText(agent)
        onInput()
    }

    internal fun hasConflictForTest(): Boolean = conflict

    internal fun saveForTest() = saveNow()

    private fun loadFromDisk(focusEmpty: Boolean, forceReset: Boolean = true) {
        val current = target ?: return
        saveAlarm.cancelAllRequests()
        val note = notes.noteFor(current.packageJson, current.name)
        val loaded = Layers(note?.layers?.human.orEmpty(), note?.layers?.agent.orEmpty())
        baseline = loaded
        humanLayer.load(loaded.human, forceReset)
        agentLayer.load(loaded.agent, forceReset)
        val generated = note?.layers?.generated.orEmpty()
        generatedPreview.text = PacmonMarkdown.render(generated, "")
        generatedPreview.caretPosition = 0
        generatedPanel.isVisible = generated.isNotBlank()
        conflict = false
        showConflict(false)
        statusLabel.text = ""
        validateAgent()
        if (focusEmpty && loaded.human.isBlank()) SwingUtilities.invokeLater(humanLayer::startEditing)
    }

    private fun onInput() {
        if (disposed || target == null) return
        validateAgent()
        statusLabel.text = "Unsaved changes"
        if (conflict) return
        saveAlarm.cancelAllRequests()
        saveAlarm.addRequest(::saveNow, 600)
    }

    private fun saveNow() {
        val current = target ?: return
        saveAlarm.cancelAllRequests()
        if (!isDirty()) {
            if (conflict) loadFromDisk(focusEmpty = false)
            return
        }
        val before = baseline
        val next = currentLayers()
        writing = true
        statusLabel.text = "Saving..."
        try {
            notes.saveNoteLayers(current.packageJson, current.name, next.human, next.agent)
            baseline = next
            humanLayer.refreshPreview()
            agentLayer.refreshPreview()
            conflict = false
            showConflict(false)
            statusLabel.text = "Saved ${java.time.LocalTime.now().withNano(0)}"
        } catch (error: RuntimeException) {
            baseline = before
            statusLabel.text = "Could not save: ${error.message ?: "unknown error"}"
        } finally {
            writing = false
        }
    }

    private fun openNotesFile() {
        val current = target ?: return
        flushPending()
        val file = notes.resolveNotesFile(current.packageJson)
            ?: notes.saveNoteLayers(current.packageJson, current.name, humanLayer.text, agentLayer.text)
        notes.open(file)
    }

    private fun validateAgent() {
        val problems = AgentNotes.lint(agentLayer.text)
        problemsPanel.isVisible = problems.isNotEmpty()
        if (problems.isEmpty()) return
        problemsLabel.text = problemsHtml(problems)
        fixButton.isVisible = problems.any(AgentProblem::fixable)
        setAgentExpanded(true)
    }

    private fun problemsHtml(problems: List<AgentProblem>): String = buildString {
        append("<html>")
        append(if (problems.size == 1) "1 problem:<br>" else "${problems.size} problems:<br>")
        problems.forEachIndexed { index, problem ->
            if (index > 0) append("<br>")
            append(escapeHtml(problem.message()))
        }
        append("</html>")
    }

    private fun setAgentExpanded(value: Boolean) {
        agentExpanded = value
        agentToggle.text = if (value) "\u25BE Agent notes" else "\u25B8 Agent notes"
        agentContent.isVisible = value
        revalidate()
        repaint()
    }

    private fun showConflict(value: Boolean) {
        conflictPanel.isVisible = value
        revalidate()
    }

    private fun currentLayers(): Layers = Layers(humanLayer.text.trim(), agentLayer.text.trim())

    private fun isDirty(): Boolean = currentLayers() != Layers(baseline.human.trim(), baseline.agent.trim())

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    override fun getMinimumSize(): Dimension = Dimension(260, 200)

    override fun dispose() {
        if (disposed) return
        flushPending()
        disposed = true
        saveAlarm.cancelAllRequests()
    }

    private class DependencySectionBadge : JBLabel("") {
        private val badgeBackground = JBColor(0xD9D9D9, 0x5A5D5E)

        init {
            isOpaque = false
            foreground = JBColor(0x303030, 0xFFFFFF)
            border = JBUI.Borders.empty(1, 6)
            font = font.deriveFont(font.size2D - 1f)
        }

        override fun paintComponent(graphics: Graphics) {
            val copy = graphics.create() as Graphics2D
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                copy.color = badgeBackground
                val arc = JBUI.scale(5)
                copy.fillRoundRect(0, 0, width, height, arc, arc)
            } finally {
                copy.dispose()
            }
            super.paintComponent(graphics)
        }
    }

    /**
     * Title label beside its action button when there is room for both; the
     * button drops below the title once the tool window gets too narrow to
     * fit a long dependency name and the button side by side — the Swing
     * equivalent of the panel's flex-wrap in the VS Code webview.
     */
    private class ResponsiveHeaderRow(
        private val content: JComponent,
        private val action: JComponent,
    ) : JPanel() {
        private val gap = JBUI.scale(8)

        init {
            isOpaque = false
            layout = null
            add(content)
            add(action)
            addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(event: java.awt.event.ComponentEvent) = revalidate()
            })
        }

        private fun fitsSideBySide(forWidth: Int): Boolean =
            content.preferredSize.width + gap + action.preferredSize.width <= forWidth

        override fun doLayout() {
            val w = width
            val contentSize = content.preferredSize
            val actionSize = action.preferredSize
            if (fitsSideBySide(w)) {
                val rowHeight = maxOf(contentSize.height, actionSize.height)
                val contentWidth = (w - gap - actionSize.width).coerceAtLeast(0)
                content.setBounds(0, (rowHeight - contentSize.height) / 2, contentWidth, contentSize.height)
                action.setBounds(w - actionSize.width, (rowHeight - actionSize.height) / 2, actionSize.width, actionSize.height)
            } else {
                content.setBounds(0, 0, w, contentSize.height)
                action.setBounds(0, contentSize.height + gap, minOf(actionSize.width, w), actionSize.height)
            }
        }

        override fun getPreferredSize(): Dimension {
            val contentSize = content.preferredSize
            val actionSize = action.preferredSize
            val idealWidth = contentSize.width + gap + actionSize.width
            val w = width.takeIf { it > 0 } ?: idealWidth
            return if (fitsSideBySide(w)) {
                Dimension(w, maxOf(contentSize.height, actionSize.height))
            } else {
                Dimension(w, contentSize.height + gap + actionSize.height)
            }
        }
    }
}
