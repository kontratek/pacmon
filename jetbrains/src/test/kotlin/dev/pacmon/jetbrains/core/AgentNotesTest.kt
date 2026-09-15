package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentNotesTest {
    @Test
    fun `validates the same vocabulary and enumerated values as vscode`() {
        val problems = AgentNotes.lint(
            "- contraint: keep v4\n- runtime: browser\n- exposure: internal\n- verified: -\n- https://example.com",
        )
        assertEquals(
            listOf(AgentProblemKind.UNKNOWN_KEY, AgentProblemKind.BAD_VALUE, AgentProblemKind.EMPTY_VALUE),
            problems.map { it.kind },
        )
        assertEquals("constraint", problems.first().suggestion)
        assertEquals("runtime", problems[1].key)
    }

    @Test
    fun `fix renames close keys and keeps unknown content as note`() {
        val fixed = AgentNotes.fix("- contraint: v4\n- mystery: value")
        assertEquals("- constraint: v4\n- note: mystery: value", fixed)
        assertNull(AgentNotes.lint("- runtime: server/client").firstOrNull())
    }
}
