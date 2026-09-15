package dev.pacmon.jetbrains.core

data class NoteSection(
    val name: String,
    val headingLine: Int,
    val bodyStart: Int,
    var bodyEnd: Int,
    var agentHeadingLine: Int? = null,
    var generatedHeadingLine: Int? = null,
)

data class NotesProblem(
    val name: String,
    val line: Int,
    val firstLine: Int,
)

data class NotesFileModel(
    val eol: String,
    val hadBom: Boolean,
    val lines: List<String>,
    val sections: List<NoteSection>,
    val problems: List<NotesProblem>,
)

data class SectionLayers(
    val human: String,
    val agent: String,
    val generated: String,
)

enum class InlineSource {
    HUMAN_FIRST,
    AI_FIRST,
    HUMAN_ONLY,
    AI_ONLY,
}
