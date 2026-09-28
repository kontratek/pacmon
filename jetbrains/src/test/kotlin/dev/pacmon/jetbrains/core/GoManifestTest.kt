package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoManifestTest {
    private val goMod = listOf(
        "module example.com/app // the app",
        "",
        "go 1.24",
        "",
        "toolchain go1.24.2",
        "",
        "require github.com/spf13/cobra v1.8.1",
        "",
        "require (",
        "\tgithub.com/jackc/pgx/v5 v5.7.1",
        "\tgolang.org/x/tools v0.28.0 // indirect",
        "\t\"github.com/Quoted/Mod\" v1.0.0 // indirect; kept for tests",
        "\tgithub.com/not/indirect v1.0.0 // indirectly used",
        "\tgithub.com/spf13/cobra v1.9.0",
        ")",
        "",
        "require ()",
        "",
        "tool golang.org/x/tools/cmd/stringer",
        "",
        "tool (",
        "\tgithub.com/golangci/golangci-lint/cmd/golangci-lint",
        "\tgolang.org/x/tools/cmd/goimports",
        ")",
        "",
        "replace github.com/replaced/mod => ../local",
        "exclude github.com/excluded/mod v1.0.0",
        "retract v1.0.0",
        "godebug default=go1.21",
        "// require github.com/commented/out v1.0.0",
        "",
    ).joinToString("\n")

    private val deps = GoManifestAdapter.extractDependencies(goMod)

    @Test
    fun `matches go mod only`() {
        assertEquals(ManifestKind.GO, ManifestRegistry.forPath("/repo/go.mod")?.kind)
        assertNull(ManifestRegistry.forPath("/repo/go.sum"))
        assertNull(ManifestRegistry.forPath("/repo/go.work"))
    }

    @Test
    fun `reads single-line and block requires, first wins on duplicates`() {
        assertEquals(
            listOf(
                "github.com/spf13/cobra" to "require",
                "github.com/jackc/pgx/v5" to "require",
                "golang.org/x/tools" to "indirect",
                "github.com/Quoted/Mod" to "indirect",
                "github.com/not/indirect" to "require",
                "github.com/golangci/golangci-lint/cmd/golangci-lint" to "tool",
            ),
            deps.map { it.noteKey to it.scope },
        )
    }

    @Test
    fun `points ranges at the module path without quotes`() {
        for (dep in deps) {
            val range = dep.primaryRange
            assertEquals(dep.noteKey, goMod.substring(range.offset, range.offset + range.length))
        }
    }

    @Test
    fun `attaches tools to the module that contains them`() {
        val tools = deps.first { it.noteKey == "golang.org/x/tools" }
        assertEquals(3, tools.sourceRanges.size)
        val stringer = goMod.indexOf("golang.org/x/tools/cmd/stringer")
        assertTrue(tools.sourceRanges.any { it.contains(stringer + 3) })
    }

    @Test
    fun `reads CRLF files`() {
        val crlf = GoManifestAdapter.extractDependencies("module m\r\n\r\nrequire (\r\n\tgithub.com/a/b v1.0.0 // indirect\r\n)\r\n")
        assertEquals(listOf("github.com/a/b" to "indirect"), crlf.map { it.noteKey to it.scope })
    }

    @Test
    fun `returns nothing without requirements`() {
        assertTrue(GoManifestAdapter.extractDependencies("module m\n\ngo 1.22\n").isEmpty())
    }
}
