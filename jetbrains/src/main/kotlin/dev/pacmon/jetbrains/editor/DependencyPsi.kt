package dev.pacmon.jetbrains.editor

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

data class DependencyRef(
    val name: String,
    val section: String,
    val property: JsonProperty,
)

object DependencyPsi {
    private val dependencySections = setOf(
        "dependencies",
        "devDependencies",
        "peerDependencies",
        "optionalDependencies",
    )

    fun atOffset(file: PsiFile, offset: Int): DependencyRef? {
        if (!isPackageJson(file)) return null
        val safeOffset = offset.coerceIn(0, (file.textLength - 1).coerceAtLeast(0))
        return fromElement(file.findElementAt(safeOffset))
    }

    fun fromElement(element: PsiElement?): DependencyRef? {
        val property = PsiTreeUtil.getParentOfType(element, JsonProperty::class.java, false) ?: return null
        val sectionObject = property.parent as? JsonObject ?: return null
        val sectionProperty = sectionObject.parent as? JsonProperty ?: return null
        if (sectionProperty.name !in dependencySections) return null
        if (!isPackageJson(property.containingFile)) return null
        return DependencyRef(property.name, sectionProperty.name, property)
    }

    fun all(file: PsiFile): List<DependencyRef> {
        if (!isPackageJson(file)) return emptyList()
        val root = (file as? JsonFile)?.topLevelValue as? JsonObject ?: return emptyList()
        return root.propertyList.flatMap { section ->
            if (section.name !in dependencySections) return@flatMap emptyList()
            val value = section.value as? JsonObject ?: return@flatMap emptyList()
            value.propertyList.map { DependencyRef(it.name, section.name, it) }
        }
    }

    fun isPackageJson(file: PsiFile?): Boolean = file?.name == "package.json" && file is JsonFile
}
