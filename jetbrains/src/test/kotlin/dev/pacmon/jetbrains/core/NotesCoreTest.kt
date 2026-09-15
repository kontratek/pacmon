package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesCoreTest {
    @Test
    fun `parses human and agent layers`() {
        val model = NotesCore.parse(
            """
            ---
            format: dependency-notes/1
            lang: en
            ---

            # Dependency Notes

            ## express

            HTTP API layer.

            ### Agent notes

            - purpose: HTTP framework
            """.trimIndent(),
        )
        val section = NotesCore.findSection(model, "EXPRESS")!!
        val layers = NotesCore.layers(model, section)
        assertEquals("HTTP API layer.", layers.human)
        assertEquals("- purpose: HTTP framework", layers.agent)
        assertEquals("HTTP API layer.", NotesCore.preview(layers, InlineSource.HUMAN_FIRST))
        assertEquals("HTTP framework", NotesCore.preview(layers, InlineSource.AI_FIRST))
    }

    @Test
    fun `ignores headings inside fenced blocks`() {
        val model = NotesCore.parse("## real\n\n```md\n## fake\n```\nbody")
        assertEquals(listOf("real"), model.sections.map { it.name })
        assertTrue(NotesCore.layers(model, model.sections.single()).human.contains("## fake"))
    }

    @Test
    fun `reports duplicate sections and normalizes quoted names`() {
        val model = NotesCore.parse("## `Express`\na\n## other\nb\n## express\nc")
        assertEquals(1, model.problems.size)
        assertEquals(4, model.problems.single().line)
        assertEquals("`Express`", NotesCore.findSection(model, "express")?.name)
        assertNull(NotesCore.findSection(model, "missing"))
    }

    @Test
    fun `updates only human text and preserves agent and generated blocks`() {
        val original = listOf(
            "## alpha",
            "",
            "old human",
            "",
            "### Agent notes",
            "",
            "- purpose: p",
            "",
            "### Generated",
            "- installed: 1",
            "",
        ).joinToString("\n")
        val updated = NotesCore.upsertHumanNote(original, "alpha", "new human\nsecond line")
        assertEquals(
            "## alpha\n\nnew human\nsecond line\n\n### Agent notes\n\n- purpose: p\n\n### Generated\n- installed: 1\n",
            updated,
        )
    }

    @Test
    fun `inserts a new section in sorted order`() {
        val updated = NotesCore.upsertHumanNote("## alpha\n\na\n\n## charlie\n\nc\n", "bravo", "b")
        assertTrue(updated.indexOf("## alpha") < updated.indexOf("## bravo"))
        assertTrue(updated.indexOf("## bravo") < updated.indexOf("## charlie"))
    }

    @Test
    fun `preserves CRLF while updating`() {
        val original = "## a\r\n\r\nold\r\n\r\n### Agent notes\r\n\r\n- purpose: p\r\n"
        val updated = NotesCore.upsertHumanNote(original, "a", "new")
        assertEquals("## a\r\n\r\nnew\r\n\r\n### Agent notes\r\n\r\n- purpose: p\r\n", updated)
    }

    @Test
    fun `creates a canonical notes file`() {
        val created = NotesCore.newNotesFile("vue", "Frontend framework")
        assertTrue(created.startsWith("---\nformat: dependency-notes/1\nlang: en\n---"))
        assertTrue(created.contains("\n## vue\n\nFrontend framework\n"))
    }
}
