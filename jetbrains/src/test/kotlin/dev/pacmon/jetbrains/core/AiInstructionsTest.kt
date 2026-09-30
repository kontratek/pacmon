package dev.pacmon.jetbrains.core

import org.junit.Assert.assertTrue
import org.junit.Test

class AiInstructionsTest {
    @Test
    fun `generated block lists Gleam notes`() {
        assertTrue(AiInstructions.block().contains("`.pacmon/gleam/DEPENDENCY-NOTES.md` for Gleam"))
    }
}
