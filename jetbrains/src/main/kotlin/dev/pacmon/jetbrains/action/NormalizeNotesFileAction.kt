package dev.pacmon.jetbrains.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.service.PacmonProjectService

/**
 * Rewrites `.pacmon/DEPENDENCY-NOTES.md` into `docs/format.md`'s canonical
 * form — the JetBrains counterpart of the VS Code extension's
 * "Pacmon: Format DEPENDENCY-NOTES.md" command.
 */
class NormalizeNotesFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val service = project.getService(PacmonProjectService::class.java)
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val notesFile = findNotesFile(project, service)
        if (notesFile == null) {
            statusBar?.info = "Pacmon: no ${NotesCore.NOTES_FILE_NAME} found. Use \"Add/Edit Dependency Note\" to create one."
            return
        }

        var changed = false
        WriteCommandAction.runWriteCommandAction(project, "Format DEPENDENCY-NOTES.md", null, {
            val document = FileDocumentManager.getInstance().getDocument(notesFile) ?: return@runWriteCommandAction
            val canonical = NotesCore.normalizeText(document.text)
            if (canonical != document.text) {
                document.setText(canonical)
                FileDocumentManager.getInstance().saveDocument(document)
                changed = true
            }
        })
        statusBar?.info = if (changed) {
            "Pacmon: ${NotesCore.NOTES_FILE_NAME} formatted."
        } else {
            "Pacmon: ${NotesCore.NOTES_FILE_NAME} is already in canonical form."
        }
        service.open(notesFile)
    }

    /** The active editor's notes file, or the one for its package.json, or the project root's. */
    private fun findNotesFile(project: Project, service: PacmonProjectService): VirtualFile? {
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected?.name == NotesCore.NOTES_FILE_NAME) return selected
        if (selected?.name == "package.json") service.resolveNotesFile(selected)?.let { return it }

        val root = project.basePath?.replace('\\', '/')?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return null
        root.findChild("package.json")?.let { pkg -> service.resolveNotesFile(pkg)?.let { return it } }
        return root.findChild(NotesCore.NOTES_DIRECTORY)?.findChild(NotesCore.NOTES_FILE_NAME)
    }
}
