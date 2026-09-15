package dev.pacmon.jetbrains.core

object NotesCore {
    const val NOTES_DIRECTORY = ".pacmon"
    const val NOTES_FILE_NAME = "DEPENDENCY-NOTES.md"
    const val NOTES_RELATIVE_PATH = "$NOTES_DIRECTORY/$NOTES_FILE_NAME"
    const val AGENT_NOTES_HEADING = "### Agent notes"
    const val GENERATED_HEADING = "### Generated"

    private val heading = Regex("^##(?!#)\\s+(.+?)\\s*$")
    private val subheading = Regex("^###(?!#)\\s+(.+?)\\s*$")
    private val fence = Regex("^\\s{0,3}(`{3,}|~{3,})")
    private val agentField = Regex("^\\s*[-*]\\s+([a-z][a-z0-9_-]*)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    fun normalizeName(raw: String): String {
        var value = raw.trim()
        if (value.length >= 2) {
            val first = value.first()
            if ((first == '`' || first == '\'' || first == '"') && value.last() == first) {
                value = value.substring(1, value.length - 1).trim()
            }
        }
        return value.lowercase()
    }

    fun parse(textValue: String): NotesFileModel {
        var text = textValue
        val hadBom = text.startsWith('\uFEFF')
        if (hadBom) text = text.substring(1)
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split(Regex("\\r\\n|\\n"))
        val sections = mutableListOf<NoteSection>()
        var current: NoteSection? = null
        var inFence = false

        lines.forEachIndexed { lineNumber, line ->
            if (fence.containsMatchIn(line)) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed

            if (current != null) {
                val sub = subheading.matchEntire(line)?.groupValues?.get(1)?.trim()?.lowercase()
                if (sub == "agent notes" && current?.agentHeadingLine == null && current?.generatedHeadingLine == null) {
                    current?.agentHeadingLine = lineNumber
                    return@forEachIndexed
                }
                if (sub == "generated" && current?.generatedHeadingLine == null) {
                    current?.generatedHeadingLine = lineNumber
                    return@forEachIndexed
                }
            }

            val name = heading.matchEntire(line)?.groupValues?.get(1) ?: return@forEachIndexed
            current?.bodyEnd = lineNumber
            current = NoteSection(name, lineNumber, lineNumber + 1, lines.size)
            sections.add(current!!)
        }

        val firstByName = mutableMapOf<String, NoteSection>()
        val problems = mutableListOf<NotesProblem>()
        sections.forEach { section ->
            val key = normalizeName(section.name)
            val first = firstByName[key]
            if (first == null) firstByName[key] = section
            else problems.add(NotesProblem(section.name, section.headingLine, first.headingLine))
        }

        return NotesFileModel(eol, hadBom, lines, sections, problems)
    }

    fun findSection(model: NotesFileModel, name: String): NoteSection? {
        val key = normalizeName(name)
        return model.sections.firstOrNull { normalizeName(it.name) == key }
    }

    fun layers(model: NotesFileModel, section: NoteSection): SectionLayers {
        val humanEnd = section.agentHeadingLine ?: section.generatedHeadingLine ?: section.bodyEnd
        val agentStart = section.agentHeadingLine?.plus(1)
        val agentEnd = if (agentStart == null) null else section.generatedHeadingLine ?: section.bodyEnd
        return SectionLayers(
            slice(model.lines, section.bodyStart, humanEnd),
            slice(model.lines, agentStart, agentEnd),
            slice(model.lines, section.generatedHeadingLine, section.generatedHeadingLine?.let { section.bodyEnd }),
        )
    }

    fun orderedLayers(layers: SectionLayers, source: InlineSource): List<String> {
        val ordered = if (source == InlineSource.AI_FIRST || source == InlineSource.AI_ONLY) {
            listOf(layers.agent, layers.human)
        } else {
            listOf(layers.human, layers.agent)
        }
        return ordered.filter { it.isNotBlank() }
    }

    fun preview(layers: SectionLayers, source: InlineSource, maxLength: Int = 90): String {
        val human = layers.human.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.replace(Regex("^[-*]\\s+"), "")
            .orEmpty()
        val purpose = layers.agent.lineSequence().mapNotNull { line ->
            val match = agentField.matchEntire(line) ?: return@mapNotNull null
            if (match.groupValues[1].equals("purpose", ignoreCase = true)) match.groupValues[2].trim() else null
        }.firstOrNull().orEmpty()
        val value = when (source) {
            InlineSource.HUMAN_FIRST -> human.ifBlank { purpose }
            InlineSource.AI_FIRST -> purpose.ifBlank { human }
            InlineSource.HUMAN_ONLY -> human
            InlineSource.AI_ONLY -> purpose
        }.ifBlank { "note" }
        return if (value.length > maxLength) value.take(maxLength - 1) + "…" else value
    }

    fun newNotesFile(name: String, human: String): String {
        val body = human.trim()
        return buildString {
            append("---\nformat: dependency-notes/1\nlang: en\n---\n\n")
            append("<!-- Each \"## name\" below is a package from package.json. The text right under the\n")
            append("  heading is written by people. \"### Agent notes\" and everything below it is written\n")
            append("  by AI agents — rules in .pacmon/AGENT-RULES.md. -->\n\n")
            append("# Dependency Notes\n\n## ").append(name.trim()).append("\n\n")
            if (body.isNotEmpty()) append(body).append('\n')
        }
    }

    fun upsertHumanNote(text: String, name: String, human: String): String {
        val model = parse(text)
        val section = findSection(model, name)
        if (section == null) return insertSection(text, model, name, human)

        val humanEnd = section.agentHeadingLine ?: section.generatedHeadingLine ?: section.bodyEnd
        val hasTail = humanEnd < model.lines.size
        val region = mutableListOf("")
        val body = human.trim()
        if (body.isNotEmpty()) {
            region.addAll(body.split(Regex("\\r?\\n")))
            if (hasTail) region.add("")
        }
        return splice(model, section.headingLine + 1, humanEnd, region)
    }

    private fun insertSection(text: String, model: NotesFileModel, name: String, human: String): String {
        val normalizedNames = model.sections.map { normalizeName(it.name) }
        val sorted = normalizedNames.zipWithNext().all { (left, right) -> left <= right }
        val key = normalizeName(name)
        val before = if (sorted) model.sections.firstOrNull { normalizeName(it.name) > key }?.headingLine else null
        val snippet = buildString {
            append("## ").append(name.trim()).append(model.eol).append(model.eol)
            if (human.isNotBlank()) append(human.trim().replace(Regex("\\r?\\n"), model.eol)).append(model.eol)
        }
        if (before == null) {
            val base = text.removeSuffix("\r\n").removeSuffix("\n")
            return base + model.eol + model.eol + snippet
        }
        val prefix = model.lines.take(before).joinToString(model.eol) + if (before > 0) model.eol else ""
        val suffix = model.lines.drop(before).joinToString(model.eol)
        val separator = if (prefix.endsWith(model.eol + model.eol)) "" else model.eol
        return prefix + separator + snippet + suffix
    }

    private fun splice(model: NotesFileModel, from: Int, to: Int, region: List<String>): String {
        val rebuilt = model.lines.take(from) + region + model.lines.drop(to)
        var output = rebuilt.joinToString(model.eol)
        if (!output.endsWith(model.eol)) output += model.eol
        return output
    }

    private fun slice(lines: List<String>, startValue: Int?, endValue: Int?): String {
        if (startValue == null || endValue == null) return ""
        var start = startValue
        var end = endValue
        while (start < end && lines[start].isBlank()) start++
        while (end > start && lines[end - 1].isBlank()) end--
        return lines.subList(start, end).joinToString("\n")
    }
}
