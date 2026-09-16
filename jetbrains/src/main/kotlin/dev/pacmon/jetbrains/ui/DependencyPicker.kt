package dev.pacmon.jetbrains.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.pacmon.jetbrains.action.NoteEditor
import dev.pacmon.jetbrains.action.PacmonCommands
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.editor.PacmonIcons
import dev.pacmon.jetbrains.service.PacmonProjectService
import javax.swing.JList

/**
 * Every dependency in one searchable list, documented or not — the JetBrains
 * counterpart of the VS Code extension's "Documentation Coverage" quick pick,
 * which its view labels "Search dependencies…". Picking one opens its note,
 * so this doubles as the way to find the dependency you want to write about
 * without scrolling `package.json`.
 *
 * Documented entries come first, each with the first line of its note beside
 * it; the mark at the left says which is which, the same filled and hollow
 * mark that `package.json` draws.
 */
object DependencyPicker {
    private data class Item(
        val name: String,
        val section: String,
        val preview: String,
        val documented: Boolean,
    )

    fun show(project: Project, packageJson: VirtualFile? = null, editor: Editor? = null) {
        val manifest = packageJson?.takeIf { it.isValid } ?: PacmonCommands.defaultPackageJson(project)
        if (manifest == null) {
            Messages.showInfoMessage(project, "No package.json found in this project.", "Pacmon")
            return
        }
        val notes = project.getService(PacmonProjectService::class.java)
        val items = ReadAction.compute<List<Item>, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(manifest)
                ?: return@compute emptyList()
            DependencyPsi.all(psiFile).map { dependency ->
                val note = notes.noteFor(manifest, dependency.name)
                Item(
                    dependency.name,
                    dependency.section,
                    if (note == null) "" else NotesCore.preview(note.layers, notes.inlineSource(), 96),
                    note != null,
                )
            }
        }
        if (items.isEmpty()) {
            Messages.showInfoMessage(project, "This package.json has no dependencies.", "Pacmon")
            return
        }

        val ordered = items.sortedWith(compareByDescending<Item> { it.documented }.thenBy { it.name })
        val documented = ordered.count { it.documented }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ordered)
            .setTitle("Documentation coverage: $documented/${items.size} dependencies documented")
            .setRenderer(Renderer())
            .setNamerForFiltering { "${it.name} ${it.section}" }
            .setItemChosenCallback { chosen -> open(project, manifest, chosen.name, editor) }
            .setResizable(true)
            .setMovable(true)
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    /**
     * The PSI element behind a row is not kept across the popup's lifetime —
     * the file may be reparsed while the list is open — so the dependency is
     * looked up again by name once one is picked.
     */
    private fun open(project: Project, packageJson: VirtualFile, name: String, editor: Editor?) {
        val dependency = ReadAction.compute<dev.pacmon.jetbrains.editor.DependencyRef?, RuntimeException> {
            PsiManager.getInstance(project).findFile(packageJson)
                ?.let(DependencyPsi::all)
                ?.firstOrNull { it.name == name }
        } ?: return
        val target = editor ?: FileEditorManager.getInstance(project).selectedTextEditor
        NoteEditor.open(project, dependency, target)
    }

    private class Renderer : ColoredListCellRenderer<Item>() {
        override fun customizeCellRenderer(
            list: JList<out Item>,
            value: Item,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon = PacmonIcons.forNote(value.documented)
            append(value.name)
            append("  ${value.section}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            val trailing = if (value.documented) value.preview else "no note yet — pick to add one"
            if (trailing.isNotBlank()) {
                append("  $trailing", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }
    }
}
