package dev.pacmon.jetbrains.editor

import com.intellij.codeInsight.hint.TooltipController
import com.intellij.codeInsight.hint.TooltipGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import dev.pacmon.jetbrains.core.LintFinding
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.core.NotesLint
import dev.pacmon.jetbrains.service.PacmonNotesListener

/**
 * Live `docs/format.md` warnings for `.pacmon/DEPENDENCY-NOTES.md`: every
 * finding `NotesLint` reports, underlined where the TypeScript extension's
 * `NotesDiagnostics` would put a diagnostic, with the same message on hover.
 * Independent of the file type the Markdown plugin does or does not give the
 * file, like [PacmonLinePainter] — it only checks the file's name.
 */
class PacmonNotesAnnotator : EditorFactoryListener {
    private val watchers = mutableMapOf<Editor, Watcher>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (file.name != NotesCore.NOTES_FILE_NAME) return
        watchers[editor] = Watcher(editor, project, file).also { it.start() }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        watchers.remove(event.editor)?.let { Disposer.dispose(it) }
    }

    private class Watcher(
        private val editor: Editor,
        private val project: Project,
        private val file: VirtualFile,
    ) : DocumentListener, EditorMouseMotionListener, Disposable {
        private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
        private val highlighters = mutableListOf<RangeHighlighter>()
        private var findings: List<LintFinding> = emptyList()

        fun start() {
            editor.document.addDocumentListener(this, this)
            editor.addEditorMouseMotionListener(this, this)
            project.messageBus.connect(this).subscribe(PacmonNotesListener.TOPIC, PacmonNotesListener { schedule() })
            relint()
        }

        override fun documentChanged(event: DocumentEvent) = schedule()

        private fun schedule() {
            alarm.cancelAllRequests()
            alarm.addRequest(::relint, 300)
        }

        private fun relint() {
            if (project.isDisposed || editor.isDisposed) return
            findings = ReadAction.compute<List<LintFinding>, RuntimeException> {
                val model = NotesCore.parse(editor.document.text)
                NotesLint.lint(model, dependencyNames())
            }
            paint()
        }

        private fun dependencyNames(): List<String> {
            // Mirrors the VS Code extension's own simplification: the notes
            // file describes the package.json right next to its `.pacmon/`.
            val packageJson = file.parent?.parent?.findChild("package.json") ?: return emptyList()
            val psiFile = PsiManager.getInstance(project).findFile(packageJson) ?: return emptyList()
            return DependencyPsi.all(psiFile).map { it.name }
        }

        private fun paint() {
            highlighters.forEach { editor.markupModel.removeHighlighter(it) }
            highlighters.clear()
            if (editor.isDisposed) return
            val document = editor.document
            findings.forEach { finding ->
                val range = rangeFor(finding)
                val highlighter = editor.markupModel.addRangeHighlighter(
                    CodeInsightColors.WARNINGS_ATTRIBUTES,
                    range.first,
                    range.second,
                    HighlighterLayer.WARNING,
                    HighlighterTargetArea.EXACT_RANGE,
                )
                highlighter.errorStripeTooltip = finding.message()
                highlighters.add(highlighter)
            }
        }

        /** [start, end) offsets: the finding's span within its line, or the whole line. */
        private fun rangeFor(finding: LintFinding): Pair<Int, Int> {
            val document = editor.document
            val line = finding.line.coerceIn(0, maxOf(document.lineCount - 1, 0))
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val span = when (finding) {
                is LintFinding.UnknownAgentKey -> finding.span
                is LintFinding.EmptyAgentValue -> finding.span
                is LintFinding.BadAgentValue -> finding.span
                is LintFinding.RemovedButPresent -> finding.span
                else -> null
            } ?: return lineStart to lineEnd
            val lineLength = lineEnd - lineStart
            val start = span.start.coerceIn(0, lineLength)
            val end = span.end.coerceIn(start, lineLength)
            return (lineStart + start) to (lineStart + end)
        }

        override fun mouseMoved(event: EditorMouseEvent) {
            val offset = event.offset
            val hit = findings.firstOrNull { finding ->
                val (start, end) = rangeFor(finding)
                offset in start until end
            }
            val controller = TooltipController.getInstance()
            if (hit == null) {
                controller.cancelTooltip(TOOLTIP_GROUP, event.mouseEvent, true)
            } else {
                controller.showTooltip(editor, event.mouseEvent.point, hit.message(), false, TOOLTIP_GROUP)
            }
        }

        override fun dispose() {
            highlighters.forEach { if (it.isValid) editor.markupModel.removeHighlighter(it) }
            highlighters.clear()
        }

        private companion object {
            val TOOLTIP_GROUP = TooltipGroup("pacmon.notes.lint", 0)
        }
    }
}
