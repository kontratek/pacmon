package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesLintTest {
    @Test
    fun `validates v2 ecosystem against notes path`() {
        val missing = NotesCore.parse("---\nformat: dependency-notes/2\nlang: en\n---\n# Dependency Notes\n")
        assertTrue(NotesLint.lint(missing, emptyList(), ManifestKind.CARGO).any { it is LintFinding.MissingEcosystem })

        val wrong = NotesCore.parse(
            "---\nformat: dependency-notes/2\necosystem: cargo\nlang: en\n---\n# Dependency Notes\n",
        )
        assertTrue(NotesLint.lint(wrong, emptyList(), ManifestKind.MAVEN).any { it is LintFinding.WrongEcosystem })

        val zig = NotesCore.parse(
            "---\nformat: dependency-notes/2\necosystem: zig\nlang: en\n---\n# Dependency Notes\n",
        )
        assertTrue(NotesLint.lint(zig, emptyList(), ManifestKind.ZIG).none { it is LintFinding.WrongEcosystem })
        assertTrue(NotesLint.lint(zig, emptyList(), ManifestKind.CARGO).any { it is LintFinding.WrongEcosystem })
    }

    private val deps = listOf("express", "@scope/util", "lodash")
    private val head = listOf("---", "format: dependency-notes/1", "---", "", "# Dependency Notes", "")

    private inline fun <reified T : LintFinding> of(text: String, names: List<String> = deps): List<T> =
        NotesLint.lint(NotesCore.parse(text), names).filterIsInstance<T>()

    @Test
    fun `flags wrong heading level only for known dependency names`() {
        val text = (head + listOf("### express", "x", "", "#### random-thing", "y")).joinToString("\n")
        val findings = of<LintFinding.WrongHeadingLevel>(text)
        assertEquals(1, findings.size)
        assertEquals(6, findings[0].line)
        assertEquals("express", findings[0].name)
        assertEquals(3, findings[0].level)
    }

    @Test
    fun `flags a title-level heading that is actually a dependency`() {
        val findings = of<LintFinding.WrongHeadingLevel>("# lodash\nbody")
        assertEquals(1, findings.size)
        assertEquals(0, findings[0].line)
        assertEquals("lodash", findings[0].name)
        assertEquals(1, findings[0].level)
    }

    @Test
    fun `flags missing space for known deps`() {
        val findings = of<LintFinding.MissingSpaceAfterHashes>("---\nf: 1\n---\n##express\nx")
        assertEquals(1, findings.size)
        assertEquals(3, findings[0].line)
        assertEquals("express", findings[0].name)
    }

    @Test
    fun `ignores fenced code`() {
        val text = (head + listOf("## express", "", "```", "### express", "# not a title", "```")).joinToString("\n")
        val model = NotesCore.parse(text)
        val findings = NotesLint.lint(model, deps).filter {
            it is LintFinding.WrongHeadingLevel || it is LintFinding.ExtraTitle || it is LintFinding.StrayHeading
        }
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports missing frontmatter once, and an unknown format version`() {
        assertEquals(1, of<LintFinding.MissingFrontmatter>("## express\nx").size)
        assertTrue(of<LintFinding.MissingFrontmatter>("---\nformat: dependency-notes/1\n---\n## express\nx").isEmpty())
        assertTrue(of<LintFinding.UnknownFormat>("---\nformat: dependency-notes/1\n---\n## express\nx").isEmpty())

        val findings = of<LintFinding.UnknownFormat>("---\nlang: en\nformat: dependency-notes/999\n---\n# Dependency Notes\n")
        assertEquals(1, findings.size)
        assertEquals(2, findings[0].line)
        assertEquals("dependency-notes/999", findings[0].version)
    }

    @Test
    fun `wants exactly one title worded Dependency Notes`() {
        val model = NotesCore.parse(head.joinToString("\n"))
        val findings = NotesLint.lint(model, deps).filter {
            it is LintFinding.MissingTitle || it is LintFinding.WrongTitle || it is LintFinding.ExtraTitle
        }
        assertTrue(findings.isEmpty())

        val missing = of<LintFinding.MissingTitle>("---\nformat: dependency-notes/1\n---\n\n## express\nx")
        assertEquals(1, missing.size)
        assertEquals(3, missing[0].line)

        val wrong = of<LintFinding.WrongTitle>("---\nformat: dependency-notes/1\n---\n\n# My Deps\n")
        assertEquals(1, wrong.size)
        assertEquals(4, wrong[0].line)
        assertEquals("My Deps", wrong[0].text)

        val extraText = (head + listOf("## express", "x", "", "# Second")).joinToString("\n")
        val extra = of<LintFinding.ExtraTitle>(extraText)
        assertEquals(1, extra.size)
        assertEquals(9, extra[0].line)
        assertEquals("Second", extra[0].text)
    }

    private fun sectionWith(vararg body: String): String =
        (head + listOf("Intro text.", "", "### Intro headings are free", "", "## express", "") + body).joinToString("\n")

    @Test
    fun `allows only Agent notes, everything else is a classified stray heading`() {
        val text = sectionWith(
            "text", "### Known quirks", "#### Deeper", "### Agent Note", "### AI notes",
            "### Agent notes", "- purpose: p", "### Generated", "- x: 1",
        )
        val findings = of<LintFinding.StrayHeading>(text)
        assertEquals(5, findings.size)
        assertEquals(13, findings[0].line); assertEquals("Known quirks", findings[0].text); assertEquals(3, findings[0].level); assertNull(findings[0].meant)
        assertEquals(14, findings[1].line); assertEquals("Deeper", findings[1].text); assertEquals(4, findings[1].level); assertNull(findings[1].meant)
        assertEquals(15, findings[2].line); assertEquals("Agent Note", findings[2].text); assertEquals(LintFinding.StrayHeadingMeant.AGENT_NOTES, findings[2].meant)
        assertEquals(16, findings[3].line); assertEquals("AI notes", findings[3].text); assertEquals(LintFinding.StrayHeadingMeant.AGENT_NOTES, findings[3].meant)
        assertEquals(19, findings[4].line); assertEquals("Generated", findings[4].text); assertEquals(LintFinding.StrayHeadingMeant.GENERATED, findings[4].meant)
    }

    @Test
    fun `does not double-flag a dependency name under the wrong level`() {
        assertTrue(of<LintFinding.StrayHeading>(sectionWith("### lodash")).isEmpty())
        assertEquals(1, of<LintFinding.WrongHeadingLevel>(sectionWith("### lodash")).size)
        assertTrue(of<LintFinding.WrongTitle>("# lodash\nbody").isEmpty())
    }

    private fun agentFile(agent: String): NotesFileModel {
        val lines = head + listOf("## express", "", "- foo: bar (human text, never checked)", "", "### Agent notes", "") +
            agent.split("\n") + listOf("")
        return NotesCore.parse(lines.joinToString("\n"))
    }

    private fun agentFindings(agent: String, names: List<String> = deps): List<LintFinding> =
        NotesLint.lint(agentFile(agent), names).filter {
            it is LintFinding.UnknownAgentKey || it is LintFinding.EmptyAgentValue ||
                it is LintFinding.BadAgentValue || it is LintFinding.RemovedButPresent
        }

    @Test
    fun `accepts every vocabulary key in any case, prose, bare URLs and note`() {
        val agent = "- purpose: p\n- Note: free text\n- BUMP-WITH: zod\nplain prose here\n" +
            "- 2026-03 not a key\n- https://example.com/changelog"
        assertTrue(agentFindings(agent).isEmpty())
    }

    @Test
    fun `flags an unknown key with its line, key and value, no guess for gibberish`() {
        val findings = agentFindings("- purpose: p\n- dafdsf: fdsaf")
        assertEquals(1, findings.size)
        val f = findings[0] as LintFinding.UnknownAgentKey
        assertEquals(13, f.line)
        assertEquals("dafdsf", f.key)
        assertEquals("fdsaf", f.value)
        assertEquals(Span(2, 8), f.span)
        assertNull(f.suggestion)
    }

    @Test
    fun `suggests the field a misspelling meant`() {
        val agent = "- contraint: x\n- bump_with: zod\n- notes: y\n- Verfied: 1.0"
        val findings = agentFindings(agent).filterIsInstance<LintFinding.UnknownAgentKey>()
        assertEquals(4, findings.size)
        assertEquals("constraint", findings[0].suggestion)
        assertEquals(Span(2, 11), findings[0].span)
        assertEquals("bump-with", findings[1].suggestion)
        assertEquals("note", findings[2].suggestion)
        assertEquals("verified", findings[3].suggestion)
        assertNull(AgentNotes.closest("why"))
        assertNull(AgentNotes.closest("caution"))
        assertEquals("owner", AgentNotes.closest("owner"))
    }

    @Test
    fun `flags empty and dash-only values`() {
        val findings = agentFindings("- remove-when: —\n- owner:")
        assertEquals(2, findings.size)
        assertTrue(findings.all { it is LintFinding.EmptyAgentValue })
    }

    @Test
    fun `checks the enumerated values and the version shape`() {
        val good = "- runtime: server, client\n- exposure: internal\n- status: removal-planned\n- status: dead\n- verified: v4.18.2"
        assertTrue(agentFindings(good).isEmpty())

        val bad = agentFindings("- runtime: browser\n- exposure: public\n- status: gone\n- verified: latest")
        assertEquals(4, bad.size)
        assertTrue(bad.all { it is LintFinding.BadAgentValue })
        val first = bad[0] as LintFinding.BadAgentValue
        assertEquals("runtime", first.key)
        assertEquals("browser", first.value)
        assertEquals("one of server | client | build | dev | deploy", first.expected)
        assertEquals(Span(11, 18), first.span)
    }

    @Test
    fun `flags status removed on a package still in package json, and only then`() {
        val findings = agentFindings("- status: removed 2026-06 — replaced")
        assertEquals(1, findings.size)
        val f = findings[0] as LintFinding.RemovedButPresent
        assertEquals(12, f.line)
        assertEquals("express", f.name)
        assertEquals(Span(10, 36), f.span)

        assertTrue(agentFindings("- status: removed 2026-06 — replaced", listOf("lodash")).isEmpty())
        assertTrue(agentFindings("- status: removed 2026-06 — replaced", emptyList()).isEmpty())
    }

    @Test
    fun `reads status removed as a word, so a value that merely starts with it is a bad value`() {
        val findings = agentFindings("- status: removedish")
        assertEquals(1, findings.size)
        assertTrue(findings[0].toString(), findings[0] is LintFinding.BadAgentValue)
    }

    @Test
    fun `never looks at the human text nor fenced code inside the block`() {
        assertTrue(agentFindings("```\n- weird: thing\n```").isEmpty())
        val plain = NotesCore.parse("## express\n\n- weird: thing\n")
        assertTrue(NotesLint.lint(plain, deps).filterIsInstance<LintFinding.UnknownAgentKey>().isEmpty())
    }
}
