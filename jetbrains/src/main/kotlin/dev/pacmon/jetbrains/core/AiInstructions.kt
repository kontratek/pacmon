package dev.pacmon.jetbrains.core

/**
 * The pointer block written into instruction files an agent already reads
 * (`AGENTS.md`, `CLAUDE.md`, …). Deliberately three lines: the rules
 * themselves live in `.pacmon/AGENT-RULES.md`, which Pacmon owns; these files
 * it does not. A line-for-line port of the TypeScript core's
 * `aiInstructionsBlock`/`upsertAiInstructions` (`src/core/template.ts`).
 */
object AiInstructions {
    /** Files "Set Up AI Instructions" offers to write the pointer block into. */
    val TARGETS = listOf("AGENTS.md", "CLAUDE.md", ".cursor/rules/dependency-notes.md", ".github/copilot-instructions.md")

    private const val BLOCK_START = "<!-- pacmon:start -->"
    private const val BLOCK_END = "<!-- pacmon:end -->"

    /** Written before the format was named `dependency-notes/1`. Still found, never written. */
    private val legacyBlock = "<!-- pacmon:deps-notes:start -->" to "<!-- pacmon:deps-notes:end -->"

    fun block(): String = listOf(
        BLOCK_START,
        "Dependency notes live in `${NotesCore.NOTES_RELATIVE_PATH}`: one `## <package>` section per dependency " +
            "— people write right under the heading, AI agents write under `${NotesCore.AGENT_NOTES_HEADING}`.",
        "Rules and the field list are in `${NotesCore.AGENT_RULES_RELATIVE_PATH}`; read a package's section " +
            "before adding, bumping or removing it.",
        "Update the notes in the same commit as `package.json`; a removed package keeps its section with " +
            "`- status: removed …`.",
        BLOCK_END,
    ).joinToString("\n")

    /** Insert or replace the marked block in an existing instructions file. */
    fun upsert(existing: String): String {
        val block = block()
        for ((startMark, endMark) in listOf(BLOCK_START to BLOCK_END, legacyBlock)) {
            val start = existing.indexOf(startMark)
            val end = existing.indexOf(endMark)
            if (start != -1 && end != -1 && end > start) {
                return existing.substring(0, start) + block + existing.substring(end + endMark.length)
            }
        }
        val separator = when {
            existing.isEmpty() -> ""
            existing.endsWith("\n\n") -> ""
            existing.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        return existing + separator + block + "\n"
    }
}
