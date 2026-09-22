package dev.pacmon.jetbrains.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.pacmon.jetbrains.action.NoteEditor
import dev.pacmon.jetbrains.action.PacmonCommands
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.editor.PacmonIcons
import dev.pacmon.jetbrains.service.PacmonProjectService
import javax.swing.JList

/**
 * Every dependency in one searchable list, documented or not — the JetBrains
 * counterpart of the VS Code extension's "Documentation Coverage" quick pick,
 * which its view labels "Search dependencies…". Picking one opens its note,
 * so this doubles as the way to find the dependency you want to write about
 * without scrolling the dependency manifest.
 *
 * Documented entries come first, each with the first line of its note beside
 * it; the mark at the left says which is which, the same filled and hollow
 * mark that the dependency manifest draws.
 */
object DependencyPicker {
    private data class Item(
        val name: String,
        val section: String,
        val preview: String,
        val documented: Boolean,
    )

    fun show(project: Project, manifestFile: VirtualFile? = null, editor: Editor? = null) {
        val manifest = manifestFile?.takeIf { it.isValid } ?: PacmonCommands.defaultManifest(project)
        if (manifest == null) {
            Messages.showInfoMessage(project, "No supported dependency manifest found in this project.", "Pacmon")
            return
        }
        val notes = project.getService(PacmonProjectService::class.java)
        val items = notes.dependencies(manifest).map { dependency ->
            val note = notes.noteFor(manifest, dependency.name)
            Item(
                dependency.name,
                dependency.section,
                if (note == null) "" else NotesCore.preview(note.layers, notes.inlineSource(), 96),
                note != null,
            )
        }
        if (items.isEmpty()) {
            Messages.showInfoMessage(project, "This dependency manifest has no supported dependencies.", "Pacmon")
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
    private fun open(project: Project, manifest: VirtualFile, name: String, editor: Editor?) {
        val dependency = project.getService(PacmonProjectService::class.java)
            .dependencies(manifest).firstOrNull { it.name == name } ?: return
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
