package dev.pacmon.jetbrains.editor

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.Font

class PacmonLinePainter : EditorLinePainter() {
    override fun getLineExtensions(
        project: Project,
        file: VirtualFile,
        lineNumber: Int,
    ): Collection<LineExtensionInfo> {
        if (file.name != "package.json") return emptyList()
        return ReadAction.compute<Collection<LineExtensionInfo>, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@compute emptyList()
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@compute emptyList()
            val dependency = DependencyPsi.all(psiFile).firstOrNull {
                document.getLineNumber(it.property.textOffset) == lineNumber
            } ?: return@compute emptyList()

            val service = project.getService(PacmonProjectService::class.java)
            if (!service.previewsEnabled()) return@compute emptyList()
            val note = service.noteFor(file, dependency.name) ?: return@compute emptyList()
            val preview = NotesCore.preview(note.layers, service.inlineSource())
            val color = JBColor.namedColor(
                "Label.disabledForeground",
                JBColor(0x6F737A, 0xA0A0A0),
            )
            listOf(LineExtensionInfo("   \u25AA $preview", color, null, null, Font.ITALIC))
        }
    }
}
