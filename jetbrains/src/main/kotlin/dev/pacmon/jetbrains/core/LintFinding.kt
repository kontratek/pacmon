package dev.pacmon.jetbrains.core

/**
 * A mistake `docs/format.md` defines, with a fixed fix — never a matter of
 * taste. Mirrors the TypeScript core's `LintFinding` union (`src/core/lint.ts`)
 * field for field, so the two stay easy to compare by eye.
 */
sealed class LintFinding(val line: Int) {
    abstract fun message(): String

    class DuplicateSection(line: Int, val name: String, val firstLine: Int) : LintFinding(line) {
        override fun message(): String =
            "\"$name\" is already defined at line ${firstLine + 1} — only the first section is read."
    }

    class WrongHeadingLevel(line: Int, val name: String, val level: Int) : LintFinding(line) {
        override fun message(): String = "\"$name\" is a dependency — write \"## $name\" so tools find this note."
    }

    class MissingSpaceAfterHashes(line: Int, val name: String) : LintFinding(line) {
        override fun message(): String = "Missing space: write \"## $name\"."
    }

    class MissingFrontmatter(line: Int) : LintFinding(line) {
        override fun message(): String = "No frontmatter — normalize the file to add it."
    }

    class UnknownFormat(line: Int, val version: String) : LintFinding(line) {
        override fun message(): String = "Unknown format \"$version\" — this Pacmon reads ${NotesCore.FORMAT_VERSION}."
    }

    class MissingTitle(line: Int) : LintFinding(line) {
        override fun message(): String = "No \"${NotesCore.DEFAULT_TITLE}\" title — normalize the file to add it."
    }

    class WrongTitle(line: Int, val text: String) : LintFinding(line) {
        override fun message(): String = "The title is \"${NotesCore.DEFAULT_TITLE}\"."
    }

    class ExtraTitle(line: Int, val text: String) : LintFinding(line) {
        override fun message(): String = "Only one \"#\" heading, \"${NotesCore.DEFAULT_TITLE}\" — make this plain text."
    }

    enum class StrayHeadingMeant { AGENT_NOTES, GENERATED }

    class StrayHeading(
        line: Int,
        val text: String,
        val level: Int,
        val meant: StrayHeadingMeant? = null,
    ) : LintFinding(line) {
        override fun message(): String = when (meant) {
            StrayHeadingMeant.AGENT_NOTES -> "Did you mean \"${NotesCore.AGENT_NOTES_HEADING}\"?"
            StrayHeadingMeant.GENERATED -> "\"${NotesCore.GENERATED_HEADING}\" is reserved for tools — nothing writes it yet."
            null -> "Only \"${NotesCore.AGENT_NOTES_HEADING}\" may head a section — make this plain text."
        }
    }

    class UnknownAgentKey(
        line: Int,
        val key: String,
        val value: String,
        val span: Span,
        val suggestion: String? = null,
    ) : LintFinding(line) {
        override fun message(): String = suggestion?.let { "Unknown field \"$key:\" — did you mean \"$it:\"?" }
            ?: "Unknown field \"$key:\" — keep it as \"note:\" or remove it. Fields: ${NotesCore.AGENT_RULES_RELATIVE_PATH}"
    }

    class EmptyAgentValue(line: Int, val key: String, val span: Span) : LintFinding(line) {
        override fun message(): String = "\"$key:\" is empty — remove the line."
    }

    class BadAgentValue(
        line: Int,
        val key: String,
        val value: String,
        val expected: String,
        val span: Span,
    ) : LintFinding(line) {
        override fun message(): String = "\"$key:\" expects $expected."
    }

    class RemovedButPresent(line: Int, val name: String, val span: Span) : LintFinding(line) {
        override fun message(): String = "\"$name\" is in package.json — remove \"status: removed\"."
    }
}
