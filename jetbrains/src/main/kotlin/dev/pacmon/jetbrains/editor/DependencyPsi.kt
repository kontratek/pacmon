package dev.pacmon.jetbrains.editor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.pacmon.jetbrains.core.DependencyEntry
import dev.pacmon.jetbrains.core.ManifestRegistry
import dev.pacmon.jetbrains.core.SourceRange

data class DependencyRef(
    val manifest: VirtualFile,
    val entry: DependencyEntry,
) {
    val name: String get() = entry.noteKey
    val displayName: String get() = entry.displayName
    val section: String get() = entry.scope
    val primaryRange: SourceRange get() = entry.primaryRange
    val iconRange: SourceRange get() = entry.iconRange
    val sourceRanges: List<SourceRange> get() = entry.sourceRanges
}

/** Language-independent bridge from IntelliJ PSI entry points to source ranges. */
object DependencyPsi {
    fun atOffset(file: PsiFile, offset: Int): DependencyRef? {
        val manifest = file.virtualFile ?: return null
        val entries = all(file).map { it.entry }
        val safeOffset = offset.coerceIn(0, file.textLength.coerceAtLeast(0))
        val entry = ManifestRegistry.dependencyAtOffset(entries, safeOffset) ?: return null
        return DependencyRef(manifest, entry)
    }

    fun fromElement(element: PsiElement?): DependencyRef? {
        val value = element ?: return null
        val file = value.containingFile ?: return null
        return atOffset(file, value.textRange.startOffset)
    }

    fun all(file: PsiFile): List<DependencyRef> {
        val manifest = file.virtualFile ?: return emptyList()
        val adapter = ManifestRegistry.forFileName(file.name) ?: return emptyList()
        return adapter.extractDependencies(file.text).map { DependencyRef(manifest, it) }
    }

    fun isManifest(file: PsiFile?): Boolean = file != null && ManifestRegistry.forFileName(file.name) != null

    fun isManifest(file: VirtualFile?): Boolean = file != null && !file.isDirectory &&
        ManifestRegistry.forFileName(file.name) != null

    fun anchorElement(file: PsiFile, dependency: DependencyRef): PsiElement? =
        file.findElementAt(dependency.primaryRange.offset.coerceIn(0, (file.textLength - 1).coerceAtLeast(0)))
}
