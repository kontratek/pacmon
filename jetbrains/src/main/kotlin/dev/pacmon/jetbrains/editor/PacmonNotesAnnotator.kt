package dev.pacmon.jetbrains.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import dev.pacmon.jetbrains.core.LintFinding
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.core.NotesLint
import dev.pacmon.jetbrains.core.Span

/**
 * Live `docs/format.md` warnings for `DEPENDENCY-NOTES.md`: every finding
 * [NotesLint] reports, as an ordinary IDE warning where the TypeScript
 * extension's `NotesDiagnostics` puts a diagnostic — underlined in the editor,
 * marked in the scrollbar, listed in Problems, with the same message on hover.
 *
 * An [Annotator] rather than hand-drawn highlighters, so the daemon owns the
 * lifecycle: already-open files are covered the moment the plugin loads, the
 * warnings follow edits without a debounce of our own, and they look and behave
 * like every other warning in the IDE.
 *
 * Registered for plain text and, when that plugin is present, for Markdown —
 * the two languages an `.md` file can get — and gated on the file's name only,
 * like [PacmonLinePainter].
 */
class PacmonNotesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // The whole file is one analysis, so it runs once, on the file element.
        val file = element as? PsiFile ?: return
        if (file.name != NotesCore.NOTES_FILE_NAME) return

        val text = file.viewProvider.document?.immutableCharSequence?.toString() ?: file.text
        val model = NotesCore.parse(text)
        val findings = NotesLint.lint(model, dependencyNames(file))
        if (findings.isEmpty()) return

        val starts = lineStartOffsets(text)
        val fileEnd = file.textRange.endOffset
        findings.forEach { finding ->
            val range = rangeFor(finding, text, starts, fileEnd) ?: return@forEach
            holder.newAnnotation(HighlightSeverity.WARNING, finding.message())
                .range(range)
                .create()
        }
    }

    /**
     * The dependencies the notes file describes: the nearest package.json at or
     * above its `.pacmon/` directory. `.pacmon/` normally sits right next to
     * one, which is the VS Code extension's `packageJsonFor`; walking up covers
     * a notes file kept at another depth.
     *
     * Nothing found means no dependency names, and the findings that need them
     * (a name under the wrong heading level, `##name`, `status: removed` on a
     * package that is still there) simply do not apply — that is the same rule
     * the TypeScript core follows.
     */
    private fun dependencyNames(file: PsiFile): List<String> {
        val notes = file.virtualFile ?: file.originalFile.virtualFile ?: return emptyList()
        val packageJson = packageJsonFor(notes) ?: return emptyList()
        val psiFile = PsiManager.getInstance(file.project).findFile(packageJson) ?: return emptyList()
        return DependencyPsi.all(psiFile).map { it.name }
    }

    private fun packageJsonFor(notes: VirtualFile): VirtualFile? {
        var directory = notes.parent?.parent
        repeat(64) {
            val current = directory ?: return null
            current.findChild("package.json")?.let { if (!it.isDirectory) return it }
            directory = current.parent
        }
        return null
    }

    private fun lineStartOffsets(text: String): List<Int> {
        val starts = mutableListOf(0)
        text.forEachIndexed { index, ch -> if (ch == '\n') starts.add(index + 1) }
        return starts
    }

    /**
     * The finding's span within its line, or the line's own text. A whole-line
     * finding on a blank line would have nothing to underline — a line break
     * has no width, so the warning would reach Problems but stay invisible in
     * the editor — so it moves down to the first line that has text. Missing
     * frontmatter and a missing title are the two that land there, and the top
     * of the file is where they belong anyway.
     */
    private fun rangeFor(finding: LintFinding, text: String, starts: List<Int>, fileEnd: Int): TextRange? {
        val span = spanOf(finding)
        var line = finding.line.coerceIn(0, starts.size - 1)
        if (span == null) {
            while (line < starts.size && lineText(text, starts, line).isBlank()) line++
            if (line >= starts.size) return null
        }
        val lineStart = starts[line]
        val content = lineText(text, starts, line)
        val range = if (span == null) {
            val indent = content.length - content.trimStart().length
            (lineStart + indent) to (lineStart + content.trimEnd().length)
        } else {
            val start = span.start.coerceIn(0, content.length)
            (lineStart + start) to (lineStart + span.end.coerceIn(start, content.length))
        }
        val start = range.first.coerceAtMost(fileEnd)
        var end = range.second.coerceAtMost(fileEnd)
        // A span the edit has since emptied still deserves a caret-width mark.
        if (start == end) end = minOf(end + 1, fileEnd)
        return if (start < end) TextRange(start, end) else null
    }

    /** Line [line]'s text, without its terminator. */
    private fun lineText(text: String, starts: List<Int>, line: Int): String {
        val start = starts[line]
        val next = starts.getOrNull(line + 1)
        val end = if (next == null) text.length else (next - 1).let { if (it > start && text[it - 1] == '\r') it - 1 else it }
        return text.substring(start, end.coerceAtLeast(start))
    }

    private fun spanOf(finding: LintFinding): Span? = when (finding) {
        is LintFinding.UnknownAgentKey -> finding.span
        is LintFinding.EmptyAgentValue -> finding.span
        is LintFinding.BadAgentValue -> finding.span
        is LintFinding.RemovedButPresent -> finding.span
        else -> null
    }
}
