package dev.pacmon.jetbrains.editor

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.service.PacmonProjectService

class PacmonDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? = DependencyPsi.atOffset(file, targetOffset)?.property?.nameElement

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = documentation(element)

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? = documentation(element)

    private fun documentation(element: PsiElement): String? {
        val dependency = DependencyPsi.fromElement(element) ?: return null
        val packageJson = dependency.property.containingFile.virtualFile ?: return null
        val service = element.project.getService(PacmonProjectService::class.java)
        val note = service.noteFor(packageJson, dependency.name) ?: return null
        val layers = NotesCore.orderedLayers(note.layers, service.inlineSource())
        if (layers.isEmpty()) return null

        val content = layers.joinToString("<hr>") { markdownAsSafeHtml(it) }
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append(StringUtil.escapeXmlEntities(dependency.name))
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append(content)
            append(DocumentationMarkup.CONTENT_END)
            append(DocumentationMarkup.SECTIONS_START)
            append(DocumentationMarkup.SECTION_HEADER_START)
            append("Source")
            append(DocumentationMarkup.SECTION_SEPARATOR)
            append(NotesCore.NOTES_RELATIVE_PATH)
            append(DocumentationMarkup.SECTION_END)
            append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun markdownAsSafeHtml(markdown: String): String = markdown
        .lineSequence()
        .joinToString("<br>") { StringUtil.escapeXmlEntities(it) }
}
