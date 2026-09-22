package dev.pacmon.jetbrains.service

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.pacmon.jetbrains.settings.MonorepoModes

class ManifestResolutionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        service().state.monorepoMode = MonorepoModes.NEAREST
    }

    private fun service() = project.getService(PacmonProjectService::class.java)

    fun testThreeManifestsResolveOnlyTheirOwnNotesNamespace() {
        val npm = myFixture.tempDirFixture.createFile("package.json", """{"dependencies":{"vue":"3"}}""")
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]\nserde = \"1\"")
        val maven = myFixture.tempDirFixture.createFile(
            "pom.xml",
            "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId></dependency></dependencies></project>",
        )
        myFixture.tempDirFixture.createFile(".pacmon/DEPENDENCY-NOTES.md", "## vue\n\nnpm\n")
        myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\ncargo\n")
        myFixture.tempDirFixture.createFile(".pacmon/maven/DEPENDENCY-NOTES.md", "## g:a\n\nmaven\n")

        assertTrue(service().resolveNotesFile(npm)!!.path.endsWith(".pacmon/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(cargo)!!.path.endsWith(".pacmon/cargo/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(maven)!!.path.endsWith(".pacmon/maven/DEPENDENCY-NOTES.md"))
        assertEquals("cargo", service().noteFor(cargo, "serde")?.layers?.human)
        assertNull(service().noteFor(cargo, "vue"))
    }

    fun testNearestAndRootOnlyAreAppliedPerEcosystem() {
        val cargo = myFixture.tempDirFixture.createFile("crates/app/Cargo.toml", "[dependencies]\nserde = \"1\"")
        val rootNotes = myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nroot\n")
        val nearNotes = myFixture.tempDirFixture.createFile("crates/app/.pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nnear\n")

        assertEquals(nearNotes.path, service().resolveNotesFile(cargo)?.path)
        service().state.monorepoMode = MonorepoModes.ROOT_ONLY
        assertEquals(rootNotes.path, service().resolveNotesFile(cargo)?.path)
    }

    fun testSavingCargoAndMavenNotesCreatesV2Frontmatter() {
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]\nserde = \"1\"")
        val maven = myFixture.tempDirFixture.createFile(
            "pom.xml",
            "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId></dependency></dependencies></project>",
        )
        val cargoNotes = service().saveNoteLayers(cargo, "serde", "Serialization", "")
        val mavenNotes = service().saveNoteLayers(maven, "g:a", "Library", "")

        assertTrue(cargoNotes.path.endsWith(".pacmon/cargo/DEPENDENCY-NOTES.md"))
        assertTrue(String(cargoNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: cargo"))
        assertTrue(mavenNotes.path.endsWith(".pacmon/maven/DEPENDENCY-NOTES.md"))
        assertTrue(String(mavenNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: maven"))
        assertNull(cargo.parent.findChild(".pacmon")?.findChild("DEPENDENCY-NOTES.md"))
    }

    fun testDefaultManifestUsesActiveThenRootPriority() {
        val npm = myFixture.tempDirFixture.createFile("package.json", "{}")
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]")
        myFixture.tempDirFixture.createFile("pom.xml", "<project/>")
        val editors = FileEditorManager.getInstance(project)
        editors.openFiles.forEach(editors::closeFile)
        assertEquals(npm.path, service().defaultManifest()?.path)

        myFixture.configureFromExistingVirtualFile(cargo)
        assertEquals(cargo.path, service().defaultManifest()?.path)
    }

    fun testSharedAncestorNotesSeeAllResolvingCargoManifests() {
        val notes = myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nSerialization\n")
        myFixture.tempDirFixture.createFile("crates/a/Cargo.toml", "[dependencies]\nserde = \"1\"")
        myFixture.tempDirFixture.createFile("crates/b/Cargo.toml", "[dependencies]\ntokio = \"1\"")
        assertEquals(setOf("serde", "tokio"), service().dependenciesForNotes(notes).map { it.name }.toSet())
    }

    fun testManifestWatcherInvalidatesTheWorkspaceIndex() {
        val notes = myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nSerialization\n")
        assertEmpty(service().dependenciesForNotes(notes))

        myFixture.tempDirFixture.createFile("crates/new/Cargo.toml", "[dependencies]\nserde = \"1\"")

        assertEquals(listOf("serde"), service().dependenciesForNotes(notes).map { it.name })
    }
}
