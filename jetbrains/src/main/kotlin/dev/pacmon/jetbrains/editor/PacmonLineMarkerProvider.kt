package dev.pacmon.jetbrains.editor

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import dev.pacmon.jetbrains.action.NoteEditor
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.service.PacmonProjectService

class PacmonLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = "Pacmon dependency notes"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val dependency = DependencyPsi.fromElement(element) ?: return null
        if (element != dependency.property.nameElement) return null
        val packageJson = dependency.property.containingFile.virtualFile ?: return null
        val service = element.project.getService(PacmonProjectService::class.java)
        val note = service.noteFor(packageJson, dependency.name)
        val tooltip = if (note == null) {
            "Add a note for ${dependency.name}"
        } else {
            NotesCore.preview(note.layers, service.inlineSource())
        }
        val navigation = GutterIconNavigationHandler<PsiElement> { _, _ ->
            NoteEditor.open(element.project, dependency)
        }
        return LineMarkerInfo(
            element,
            element.textRange,
            PacmonIcons.Mark,
            { tooltip },
            navigation,
            GutterIconRenderer.Alignment.LEFT,
            { "Pacmon dependency note" },
        )
    }
}
