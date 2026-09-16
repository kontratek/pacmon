package dev.pacmon.jetbrains.editor

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.pacmon.jetbrains.action.NoteEditor
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

class PacmonInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val key = SettingsKey<NoSettings>("pacmon.dependency.icons")

    override val name: String = "Pacmon dependency note icons"

    override val previewText: String = """{ "dependencies": { "vue": "^3" } }"""

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (!DependencyPsi.isPackageJson(file)) return null
        val packageJson = file.virtualFile ?: return null
        val notes = file.project.getService(PacmonProjectService::class.java)
        if (!notes.iconsEnabled()) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val dependency = DependencyPsi.fromElement(element) ?: return true
                if (element != dependency.property.nameElement.firstChild) return true

                val note = notes.noteFor(packageJson, dependency.name)
                val icon = factory.smallScaledIcon(PacmonIcons.forNote(note != null))
                val clickable = factory.withCursorOnHover(
                    factory.mouseHandling(
                        icon,
                        object : InlayPresentationFactory.ClickListener {
                            override fun onClick(event: MouseEvent, translated: Point) {
                                NoteEditor.open(file.project, dependency)
                            }
                        },
                        null,
                    ),
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                )
                sink.addInlineElement(
                    dependency.property.nameElement.textRange.startOffset,
                    false,
                    clickable,
                    false,
                )
                return true
            }
        }
    }
}
