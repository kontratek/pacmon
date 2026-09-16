package dev.pacmon.jetbrains.core

/**
 * Format lint — every finding is a mistake with a fix, never a matter of taste.
 * A line-for-line port of the TypeScript core's `lintNotes` (`src/core/lint.ts`),
 * so the two engines agree on what `docs/format.md` requires:
 * - the frontmatter and its `format:` version; the one `# Dependency Notes` title;
 * - `## name` is a package (so a dependency name under another level, or
 *   `##name`, is a mistake), and inside a section the only heading is
 *   `### Agent notes` — anything else is plain text;
 * - the agent layer has a fixed vocabulary: unknown keys, empty values,
 *   enumerated values of the wrong shape, and `status: removed` on a package
 *   that is still in package.json.
 * The human text itself is never looked at, and prose lines in the agent block
 * are fine.
 */
object NotesLint {
    private val anyHeading = Regex("^(#{1,6})(?!#)\\s+(.+?)\\s*$")
    private val noSpaceH2 = Regex("^##(?=[^#\\s])(.+?)\\s*$")
    private val fence = Regex("^\\s{0,3}(`{3,}|~{3,})")
    private val removedStatus = Regex("^removed\\b", RegexOption.IGNORE_CASE)

    fun lint(model: NotesFileModel, depNames: Collection<String>): List<LintFinding> {
        val findings = mutableListOf<LintFinding>()
        val deps = depNames.map { NotesCore.normalizeName(it) }.toSet()

        model.problems.forEach {
            findings.add(LintFinding.DuplicateSection(it.line, it.name, it.firstLine))
        }

        if (model.frontmatter == null) {
            findings.add(LintFinding.MissingFrontmatter(0))
        } else if (model.frontmatter.formatVersion != null && model.frontmatter.formatVersion != NotesCore.FORMAT_VERSION) {
            var line = model.frontmatter.startLine
            for (i in model.frontmatter.startLine..model.frontmatter.endLine) {
                if (Regex("^format\\s*:").containsMatchIn(model.lines.getOrElse(i) { "" })) {
                    line = i
                    break
                }
            }
            findings.add(LintFinding.UnknownFormat(line, model.frontmatter.formatVersion))
        }

        // A `# lodash` where lodash is a dependency is a package heading at the
        // wrong level, not a title; the loop below reports it as such.
        val titleText = model.titleLine?.let { model.lines.getOrElse(it) { "" }.replace(Regex("^#\\s+"), "").trim() }
        val titleIsDep = titleText != null && NotesCore.normalizeName(titleText) in deps
        if (model.titleLine == null) {
            val after = model.aiComment?.endLine ?: model.frontmatter?.endLine
            val line = if (after == null) 0 else minOf(after + 1, maxOf(model.lines.size - 1, 0))
            findings.add(LintFinding.MissingTitle(line))
        } else if (titleText != null && titleText != NotesCore.TITLE_TEXT && !titleIsDep) {
            findings.add(LintFinding.WrongTitle(model.titleLine, titleText))
        }

        fun inHeaderBlock(line: Int): Boolean {
            model.frontmatter?.let { if (line in it.startLine..it.endLine) return true }
            model.aiComment?.let { if (line in it.startLine..it.endLine) return true }
            return false
        }
        fun sectionAt(line: Int) = model.sections.firstOrNull { line >= it.bodyStart && line < it.bodyEnd }

        var inFence = false
        for (i in model.lines.indices) {
            val line = model.lines[i]
            if (fence.containsMatchIn(line)) {
                inFence = !inFence
                continue
            }
            if (inFence || inHeaderBlock(i) || (i == model.titleLine && !titleIsDep)) continue

            val noSpace = noSpaceH2.matchEntire(line)
            if (noSpace != null) {
                val name = noSpace.groupValues[1]
                if (NotesCore.normalizeName(name) in deps) {
                    findings.add(LintFinding.MissingSpaceAfterHashes(i, name.trim()))
                    continue
                }
            }

            val h = anyHeading.matchEntire(line) ?: continue
            val level = h.groupValues[1].length
            val text = h.groupValues[2].trim()
            if (level != 2 && NotesCore.normalizeName(text) in deps) {
                findings.add(LintFinding.WrongHeadingLevel(i, text, level))
                continue
            }
            if (level == 1) {
                findings.add(LintFinding.ExtraTitle(i, text))
                continue
            }
            if (level >= 3) {
                val section = sectionAt(i) ?: continue
                if (i == section.agentHeadingLine) continue
                val meant = if (i == section.generatedHeadingLine) {
                    LintFinding.StrayHeadingMeant.GENERATED
                } else {
                    classifyStrayHeading(text)
                }
                findings.add(LintFinding.StrayHeading(i, text, level, meant))
            }
        }

        for (section in model.sections) {
            val agentStart = section.agentHeadingLine?.plus(1) ?: continue
            val agentEnd = section.generatedHeadingLine ?: section.bodyEnd
            var agentFence = false
            for (i in agentStart until agentEnd) {
                val raw = model.lines.getOrElse(i) { "" }
                if (fence.containsMatchIn(raw)) {
                    agentFence = !agentFence
                    continue
                }
                if (agentFence) continue
                val m = AgentNotes.field.matchEntire(raw) ?: continue
                val rawKey = m.groupValues[2]
                val rawValue = m.groupValues[3]
                if (rawValue.startsWith("//")) continue
                val key = rawKey.lowercase()
                val value = rawValue.trim()
                val keyStart = raw.indexOf(rawKey)
                val keySpan = Span(keyStart, keyStart + rawKey.length)
                // `(.*)$` runs to the end of the line, so the raw value starts there.
                val valueStart = raw.length - rawValue.length
                val valueSpan = Span(valueStart, valueStart + value.length)

                if (key !in AgentNotes.fieldSet) {
                    findings.add(LintFinding.UnknownAgentKey(i, key, value, keySpan, AgentNotes.closest(key)))
                    continue
                }
                if (AgentNotes.emptyValue.matches(value)) {
                    findings.add(LintFinding.EmptyAgentValue(i, key, keySpan))
                    continue
                }
                if (key == "status" && removedStatus.containsMatchIn(value) &&
                    NotesCore.normalizeName(section.name) in deps
                ) {
                    findings.add(LintFinding.RemovedButPresent(i, section.name, valueSpan))
                    continue
                }
                val expected = AgentNotes.valueProblem(key, value)
                if (expected != null) {
                    findings.add(LintFinding.BadAgentValue(i, key, value, expected, valueSpan))
                }
            }
        }

        return findings
    }

    /** `### Agent Note`, `### AI notes` → the agent heading; `### Generated` → the reserved one. */
    private fun classifyStrayHeading(text: String): LintFinding.StrayHeadingMeant? {
        val t = text.lowercase().replace(Regex("[^a-z ]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (t == "generated") return LintFinding.StrayHeadingMeant.GENERATED
        if (Regex("\\bagents?\\b|\\bai\\b").containsMatchIn(t) || AgentNotes.levenshtein(t, "agent notes") <= 3) {
            return LintFinding.StrayHeadingMeant.AGENT_NOTES
        }
        return null
    }
}
