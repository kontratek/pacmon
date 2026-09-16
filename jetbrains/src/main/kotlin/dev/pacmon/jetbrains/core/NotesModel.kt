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

/** Inclusive-inclusive, 0-based, like the TypeScript core's `LineRange`. */
data class LineRange(
    val startLine: Int,
    val endLine: Int,
)

data class Frontmatter(
    val startLine: Int,
    val endLine: Int,
    val formatVersion: String? = null,
    val lang: String? = null,
)

data class NotesFileModel(
    val eol: String,
    val hadBom: Boolean,
    val lines: List<String>,
    val frontmatter: Frontmatter? = null,
    val aiComment: LineRange? = null,
    val titleLine: Int? = null,
    /** Free text between the header block and the first section; may be empty. */
    val intro: LineRange? = null,
    val sections: List<NoteSection>,
    val problems: List<NotesProblem>,
)

/** Columns [start, end) within a finding's line: the key, or the value at fault. */
data class Span(
    val start: Int,
    val end: Int,
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
