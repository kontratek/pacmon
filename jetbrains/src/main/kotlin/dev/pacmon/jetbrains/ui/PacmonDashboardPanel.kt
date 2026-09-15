package dev.pacmon.jetbrains.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import kotlin.math.roundToInt

class PacmonDashboardPanel(private val project: Project) : JPanel(BorderLayout()) {
    internal data class CoverageSnapshot(
        val ratio: String,
        val packagePath: String,
        val percent: Int,
        val status: String,
        val barVisible: Boolean,
    )

    private val service = project.getService(PacmonProjectService::class.java)
    private val mutedColor = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)

    private val coverageBar = JProgressBar(0, 100).apply {
        border = JBUI.Borders.empty()
        preferredSize = Dimension(1, JBUI.scale(4))
        minimumSize = Dimension(1, JBUI.scale(4))
        isStringPainted = false
    }

    private lateinit var coverageCountLabel: JLabel
    private lateinit var packageLabel: JLabel
    private lateinit var coverageStatusLabel: JLabel
    private lateinit var targetsCountLabel: JLabel
    private lateinit var iconToggle: JBCheckBox
    private lateinit var previewToggle: JBCheckBox

    private var lastPackageJson: VirtualFile? = null
    private var loading = false

    private val content = panel {
        row {
            label("Pacmon: Dependency Notes")
                .bold()
                .applyToComponent { font = font.deriveFont(font.size2D + 2f) }
                .resizableColumn()
            link("Settings") { ShowSettingsUtil.getInstance().showSettingsDialog(project, "Pacmon") }
        }

        row {
            label("COVERAGE").bold().resizableColumn()
            coverageCountLabel = label("").applyToComponent { foreground = mutedColor }.component
        }
        row {
            packageLabel = label("").applyToComponent { foreground = mutedColor }.component
        }
        row {
            cell(coverageBar).align(AlignX.FILL)
        }
        row {
            coverageStatusLabel = label("").applyToComponent { foreground = mutedColor }.component
        }

        row {
            label("CLICK TARGETS").bold().resizableColumn()
            targetsCountLabel = label("").applyToComponent { foreground = mutedColor }.component
        }
        row {
            comment("How you open a dependency's note from package.json. Right-click always works too.")
        }
        row {
            iconToggle = checkBox("Icon before the package name")
                .comment("Filled when a note exists, hollow when it does not.")
                .component
        }

        row {
            label("NOTE MARKERS").bold()
        }
        row {
            comment("End-of-line hint on dependencies that already have a note.")
        }
        row {
            previewToggle = checkBox("Note preview at end of line")
                .comment("Shows the selected note text after the dependency line.")
                .component
        }
    }

    init {
        border = JBUI.Borders.empty(8, 12)
        add(content, BorderLayout.CENTER)

        iconToggle.addActionListener {
            if (!loading) {
                service.state.showIcons = iconToggle.isSelected
                targetsCountLabel.text = if (iconToggle.isSelected) "1 of 1" else "0 of 1"
                service.settingsChanged()
            }
        }
        previewToggle.addActionListener {
            if (!loading) {
                service.state.showPreviews = previewToggle.isSelected
                service.settingsChanged()
            }
        }

        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    if (event.newFile?.name == "package.json") lastPackageJson = event.newFile
                    scheduleRefresh()
                }
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (FileDocumentManager.getInstance().getFile(event.document)?.name == "package.json") scheduleRefresh()
            }
        }, project)
    }

    fun refresh() {
        loading = true
        iconToggle.isSelected = service.state.showIcons
        previewToggle.isSelected = service.state.showPreviews
        targetsCountLabel.text = if (iconToggle.isSelected) "1 of 1" else "0 of 1"
        loading = false

        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.name == "package.json" }
        if (selected != null) lastPackageJson = selected
        val packageJson = selected ?: lastPackageJson?.takeIf { it.isValid } ?: rootPackageJson()
        if (packageJson == null) {
            showCoverage("", "", 0, "Open a package.json to see its dependencies here.", false)
            return
        }
        val dependencies = PsiManager.getInstance(project).findFile(packageJson)?.let(DependencyPsi::all).orEmpty()
        if (dependencies.isEmpty()) {
            showCoverage("", relativePath(packageJson), 0, "This package.json has no dependencies.", false)
            return
        }
        val documented = dependencies.count { service.noteFor(packageJson, it.name) != null }
        val missing = dependencies.size - documented
        val status = when (missing) {
            0 -> "Every dependency has a note."
            1 -> "1 dependency has no note yet."
            else -> "$missing dependencies have no note yet."
        }
        showCoverage(
            "$documented of ${dependencies.size}",
            relativePath(packageJson),
            (documented * 100.0 / dependencies.size).roundToInt(),
            status,
            true,
        )
    }

    internal fun coverageForTest(): CoverageSnapshot = CoverageSnapshot(
        coverageCountLabel.text,
        packageLabel.text,
        coverageBar.value,
        coverageStatusLabel.text,
        coverageBar.isVisible,
    )

    private fun showCoverage(ratio: String, file: String, percent: Int, status: String, showBar: Boolean) {
        coverageCountLabel.text = ratio
        packageLabel.text = file
        coverageBar.value = percent
        coverageBar.isVisible = showBar
        coverageStatusLabel.text = status
    }

    private fun scheduleRefresh() = ApplicationManager.getApplication().invokeLater {
        if (!project.isDisposed) refresh()
    }

    private fun rootPackageJson(): VirtualFile? = project.basePath
        ?.replace('\\', '/')
        ?.let { LocalFileSystem.getInstance().findFileByPath("$it/package.json") }

    private fun relativePath(file: VirtualFile): String {
        val root = project.basePath?.replace('\\', '/')?.trimEnd('/') ?: return file.name
        return file.path.replace('\\', '/').removePrefix("$root/")
    }
}
