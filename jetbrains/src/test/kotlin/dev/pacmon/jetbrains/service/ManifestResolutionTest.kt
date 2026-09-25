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

    fun testManifestsResolveOnlyTheirOwnNotesNamespace() {
        val npm = myFixture.tempDirFixture.createFile("package.json", """{"dependencies":{"vue":"3"}}""")
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]\nserde = \"1\"")
        val maven = myFixture.tempDirFixture.createFile(
            "pom.xml",
            "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId></dependency></dependencies></project>",
        )
        val gradle = myFixture.tempDirFixture.createFile(
            "build.gradle.kts",
            "dependencies { implementation(\"g:a:1\") }",
        )
        val mix = myFixture.tempDirFixture.createFile(
            "mix.exs",
            "defmodule Demo.MixProject do\n  defp deps, do: [{:phoenix, \"~> 1.8\"}]\nend",
        )
        val zig = myFixture.tempDirFixture.createFile(
            "build.zig.zon",
            ".{ .dependencies = .{ .known_folders = .{ .path = \"../known-folders\" } } }",
        )
        val python = myFixture.tempDirFixture.createFile("pyproject.toml", "[project]\ndependencies = [\"requests>=2\"]")
        val nuget = myFixture.tempDirFixture.createFile(
            "App.csproj",
            "<Project><ItemGroup><PackageReference Include=\"Newtonsoft.Json\" /></ItemGroup></Project>",
        )
        myFixture.tempDirFixture.createFile(".pacmon/DEPENDENCY-NOTES.md", "## vue\n\nnpm\n")
        myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\ncargo\n")
        myFixture.tempDirFixture.createFile(".pacmon/maven/DEPENDENCY-NOTES.md", "## g:a\n\nmaven\n")
        myFixture.tempDirFixture.createFile(".pacmon/gradle/DEPENDENCY-NOTES.md", "## g:a\n\ngradle\n")
        myFixture.tempDirFixture.createFile(".pacmon/mix/DEPENDENCY-NOTES.md", "## phoenix\n\nmix\n")
        myFixture.tempDirFixture.createFile(".pacmon/zig/DEPENDENCY-NOTES.md", "## known_folders\n\nzig\n")
        myFixture.tempDirFixture.createFile(".pacmon/python/DEPENDENCY-NOTES.md", "## requests\n\npython\n")
        myFixture.tempDirFixture.createFile(".pacmon/nuget/DEPENDENCY-NOTES.md", "## newtonsoft.json\n\nnuget\n")

        assertTrue(service().resolveNotesFile(npm)!!.path.endsWith(".pacmon/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(cargo)!!.path.endsWith(".pacmon/cargo/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(maven)!!.path.endsWith(".pacmon/maven/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(gradle)!!.path.endsWith(".pacmon/gradle/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(mix)!!.path.endsWith(".pacmon/mix/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(zig)!!.path.endsWith(".pacmon/zig/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(python)!!.path.endsWith(".pacmon/python/DEPENDENCY-NOTES.md"))
        assertTrue(service().resolveNotesFile(nuget)!!.path.endsWith(".pacmon/nuget/DEPENDENCY-NOTES.md"))
        assertEquals("cargo", service().noteFor(cargo, "serde")?.layers?.human)
        assertEquals("mix", service().noteFor(mix, "phoenix")?.layers?.human)
        assertEquals("zig", service().noteFor(zig, "known_folders")?.layers?.human)
        assertEquals("python", service().noteFor(python, "requests")?.layers?.human)
        assertEquals("nuget", service().noteFor(nuget, "Newtonsoft.Json")?.layers?.human)
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

    fun testSavingEcosystemNotesCreatesV2Frontmatter() {
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]\nserde = \"1\"")
        val maven = myFixture.tempDirFixture.createFile(
            "pom.xml",
            "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId></dependency></dependencies></project>",
        )
        val cargoNotes = service().saveNoteLayers(cargo, "serde", "Serialization", "")
        val mavenNotes = service().saveNoteLayers(maven, "g:a", "Library", "")
        val gradle = myFixture.tempDirFixture.createFile(
            "build.gradle",
            "dependencies { implementation 'g:a:1' }",
        )
        val gradleNotes = service().saveNoteLayers(gradle, "g:a", "Library", "")
        val mix = myFixture.tempDirFixture.createFile(
            "mix.exs",
            "defmodule Demo.MixProject do\n  defp deps, do: [{:phoenix, \"~> 1.8\"}]\nend",
        )
        val mixNotes = service().saveNoteLayers(mix, "phoenix", "Framework", "")
        val zig = myFixture.tempDirFixture.createFile(
            "build.zig.zon",
            ".{ .dependencies = .{ .known_folders = .{ .path = \"../known-folders\" } } }",
        )
        val zigNotes = service().saveNoteLayers(zig, "known_folders", "Filesystem paths", "")
        val python = myFixture.tempDirFixture.createFile(
            "pyproject.toml",
            "[project]\ndependencies = [\"requests>=2\"]",
        )
        val pythonNotes = service().saveNoteLayers(python, "requests", "HTTP client", "")
        val nuget = myFixture.tempDirFixture.createFile(
            "App.csproj",
            "<Project><PackageReference Include=\"Newtonsoft.Json\" /></Project>",
        )
        val nugetNotes = service().saveNoteLayers(nuget, "Newtonsoft.Json", "JSON serialization", "")

        assertTrue(cargoNotes.path.endsWith(".pacmon/cargo/DEPENDENCY-NOTES.md"))
        assertTrue(String(cargoNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: cargo"))
        assertTrue(mavenNotes.path.endsWith(".pacmon/maven/DEPENDENCY-NOTES.md"))
        assertTrue(String(mavenNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: maven"))
        assertTrue(gradleNotes.path.endsWith(".pacmon/gradle/DEPENDENCY-NOTES.md"))
        assertTrue(String(gradleNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: gradle"))
        assertTrue(mixNotes.path.endsWith(".pacmon/mix/DEPENDENCY-NOTES.md"))
        assertTrue(String(mixNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: mix"))
        assertTrue(zigNotes.path.endsWith(".pacmon/zig/DEPENDENCY-NOTES.md"))
        assertTrue(String(zigNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: zig"))
        assertTrue(pythonNotes.path.endsWith(".pacmon/python/DEPENDENCY-NOTES.md"))
        assertTrue(String(pythonNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: python"))
        assertTrue(nugetNotes.path.endsWith(".pacmon/nuget/DEPENDENCY-NOTES.md"))
        assertTrue(String(nugetNotes.contentsToByteArray()).replace("\r\n", "\n").contains("format: dependency-notes/2\necosystem: nuget"))
        assertNull(cargo.parent.findChild(".pacmon")?.findChild("DEPENDENCY-NOTES.md"))
    }

    fun testZigNearestAndRootOnlyResolutionStaysInItsNamespace() {
        val zig = myFixture.tempDirFixture.createFile(
            "apps/zig-app/build.zig.zon",
            ".{ .dependencies = .{ .known_folders = .{ .path = \"../known-folders\" } } }",
        )
        val rootNotes = myFixture.tempDirFixture.createFile(
            ".pacmon/zig/DEPENDENCY-NOTES.md",
            "## known_folders\n\nroot\n",
        )
        val nearNotes = myFixture.tempDirFixture.createFile(
            "apps/zig-app/.pacmon/zig/DEPENDENCY-NOTES.md",
            "## known_folders\n\nnear\n",
        )
        myFixture.tempDirFixture.createFile("apps/zig-app/.pacmon/cargo/DEPENDENCY-NOTES.md", "## known_folders\n\ncargo\n")

        assertEquals(nearNotes.path, service().resolveNotesFile(zig)?.path)
        assertEquals("near", service().noteFor(zig, "known_folders")?.layers?.human)
        service().state.monorepoMode = MonorepoModes.ROOT_ONLY
        assertEquals(rootNotes.path, service().resolveNotesFile(zig)?.path)
        assertEquals("root", service().noteFor(zig, "known_folders")?.layers?.human)
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

    fun testGradleNotesPreferKotlinThenGroovyManifestBesideThem() {
        val notes = myFixture.tempDirFixture.createFile(
            ".pacmon/gradle/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: gradle\n---\n# Dependency Notes\n",
        )
        val groovy = myFixture.tempDirFixture.createFile("build.gradle", "dependencies {}")
        assertEquals(groovy.path, service().manifestBesideNotes(notes)?.path)
        val kotlin = myFixture.tempDirFixture.createFile("build.gradle.kts", "dependencies {}")
        assertEquals(kotlin.path, service().manifestBesideNotes(notes)?.path)
    }

    fun testSharedAncestorNotesSeeAllResolvingCargoManifests() {
        val notes = myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nSerialization\n")
        myFixture.tempDirFixture.createFile("crates/a/Cargo.toml", "[dependencies]\nserde = \"1\"")
        myFixture.tempDirFixture.createFile("crates/b/Cargo.toml", "[dependencies]\ntokio = \"1\"")
        assertEquals(setOf("serde", "tokio"), service().dependenciesForNotes(notes).map { it.name }.toSet())
    }

    fun testSharedAncestorNotesSeeAllResolvingUmbrellaMixManifests() {
        val notes = myFixture.tempDirFixture.createFile(".pacmon/mix/DEPENDENCY-NOTES.md", "## phoenix\n\nFramework\n")
        myFixture.tempDirFixture.createFile("apps/web/mix.exs", "defp deps, do: [{:phoenix, \"~> 1.8\"}]")
        myFixture.tempDirFixture.createFile("apps/accounts/mix.exs", "defp deps, do: [{:ecto_sql, \"~> 3.13\"}]")
        assertEquals(setOf("phoenix", "ecto_sql"), service().dependenciesForNotes(notes).map { it.name }.toSet())
    }

    fun testSplitRequirementsShareProjectPythonNotesAndPreferPyproject() {
        val notes = myFixture.tempDirFixture.createFile(
            ".pacmon/python/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: python\n---\n# Dependency Notes\n",
        )
        val pyproject = myFixture.tempDirFixture.createFile(
            "pyproject.toml",
            "[project]\ndependencies = [\"requests>=2\"]",
        )
        val requirements = myFixture.tempDirFixture.createFile("requirements/dev.txt", "pytest>=8\n")

        assertEquals(notes.path, service().resolveNotesFile(requirements)?.path)
        assertEquals(setOf("requests", "pytest"), service().dependenciesForNotes(notes).map { it.name }.toSet())
        assertEquals(pyproject.path, service().manifestBesideNotes(notes)?.path)
    }

    fun testZigPackageCacheManifestsAreExcludedFromNotesDiscovery() {
        val notes = myFixture.tempDirFixture.createFile(
            ".pacmon/zig/DEPENDENCY-NOTES.md",
            "## real_dep\n\nApplication dependency\n",
        )
        myFixture.tempDirFixture.createFile(
            "apps/demo/build.zig.zon",
            ".{ .dependencies = .{ .real_dep = .{ .path = \"../real\" } } }",
        )
        for (directory in listOf("zig-pkg", ".zig-cache", "zig-cache", "zig-out")) {
            myFixture.tempDirFixture.createFile(
                "$directory/downloaded/build.zig.zon",
                ".{ .dependencies = .{ .cached_dep = .{ .path = \"../cached\" } } }",
            )
        }

        assertEquals(listOf("real_dep"), service().dependenciesForNotes(notes).map { it.name })
    }

    fun testManifestWatcherInvalidatesTheWorkspaceIndex() {
        val notes = myFixture.tempDirFixture.createFile(".pacmon/cargo/DEPENDENCY-NOTES.md", "## serde\n\nSerialization\n")
        assertEmpty(service().dependenciesForNotes(notes))

        myFixture.tempDirFixture.createFile("crates/new/Cargo.toml", "[dependencies]\nserde = \"1\"")

        assertEquals(listOf("serde"), service().dependenciesForNotes(notes).map { it.name })
    }

    fun testNugetUsesTheClosestCentralManifestForResolutionAndCreation() {
        val rootCentral = myFixture.tempDirFixture.createFile(
            "Directory.Packages.props",
            "<Project><PackageVersion Include=\"Root.Package\" /></Project>",
        )
        val nestedCentral = myFixture.tempDirFixture.createFile(
            "apps/dotnet/Directory.Packages.props",
            "<Project><PackageVersion Include=\"Nested.Package\" /></Project>",
        )
        val rootProject = myFixture.tempDirFixture.createFile(
            "apps/root/App.csproj",
            "<Project><PackageReference Include=\"Root.Package\" /></Project>",
        )
        val nestedProject = myFixture.tempDirFixture.createFile(
            "apps/dotnet/src/App.csproj",
            "<Project><PackageReference Include=\"Nested.Package\" /></Project>",
        )
        val rootNotes = myFixture.tempDirFixture.createFile(
            ".pacmon/nuget/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: nuget\n---\n# Dependency Notes\n",
        )
        val nestedNotes = myFixture.tempDirFixture.createFile(
            "apps/dotnet/.pacmon/nuget/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: nuget\n---\n# Dependency Notes\n\n## nested.package\n\nShared note\n",
        )

        assertEquals(rootNotes.path, service().resolveNotesFile(rootCentral)?.path)
        assertEquals(rootNotes.path, service().resolveNotesFile(rootProject)?.path)
        assertEquals(nestedNotes.path, service().resolveNotesFile(nestedCentral)?.path)
        assertEquals(nestedNotes.path, service().resolveNotesFile(nestedProject)?.path)
        assertEquals("Shared note", service().noteFor(nestedCentral, "Nested.Package")?.layers?.human)
        assertEquals("Shared note", service().noteFor(nestedProject, "Nested.Package")?.layers?.human)

        service().state.monorepoMode = MonorepoModes.ROOT_ONLY
        assertEquals(rootNotes.path, service().resolveNotesFile(nestedProject)?.path)

    }

    fun testFirstNugetNoteIsCreatedBesideTheClosestCentralManifest() {
        myFixture.tempDirFixture.createFile(
            "apps/fresh/Directory.Packages.props",
            "<Project><PackageVersion Include=\"Fresh.Package\" /></Project>",
        )
        val freshProject = myFixture.tempDirFixture.createFile(
            "apps/fresh/src/App.vbproj",
            "<Project><PackageReference Include=\"Fresh.Package\" /></Project>",
        )

        val created = service().saveNoteLayers(freshProject, "Fresh.Package", "Fresh dependency", "")

        assertTrue(created.path.endsWith("apps/fresh/.pacmon/nuget/DEPENDENCY-NOTES.md"))
        assertTrue(
            String(created.contentsToByteArray()).replace("\r\n", "\n")
                .contains("format: dependency-notes/2\necosystem: nuget"),
        )
    }

    fun testNugetCoverageAggregatesBeforeNotesExistAndPrefersCentralCasing() {
        myFixture.tempDirFixture.createFile(
            "Directory.Packages.props",
            """
            <Project><ItemGroup>
              <PackageVersion Include="Newtonsoft.Json" />
              <GlobalPackageReference Include="Nerdbank.GitVersioning" />
            </ItemGroup></Project>
            """.trimIndent(),
        )
        val projectFile = myFixture.tempDirFixture.createFile(
            "src/App.csproj",
            """
            <Project><ItemGroup>
              <PackageReference Include="newtonsoft.json" />
              <PackageReference Include="Serilog" />
            </ItemGroup></Project>
            """.trimIndent(),
        )
        myFixture.tempDirFixture.createFile(
            "tests/Tests.fsproj",
            "<Project><PackageReference Include=\"SERILOG\" /></Project>",
        )

        val dependencies = service().dependenciesForCoverage(projectFile)
        assertEquals(
            listOf("Newtonsoft.Json", "Nerdbank.GitVersioning", "Serilog"),
            dependencies.map { it.displayName },
        )
        assertEquals("centralVersion", dependencies.first().section)
    }

    fun testNugetDiscoveryExcludesBinAndObj() {
        val notes = myFixture.tempDirFixture.createFile(
            ".pacmon/nuget/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: nuget\n---\n# Dependency Notes\n",
        )
        myFixture.tempDirFixture.createFile(
            "src/App.csproj",
            "<Project><PackageReference Include=\"Real.Package\" /></Project>",
        )
        myFixture.tempDirFixture.createFile(
            "src/bin/Generated.csproj",
            "<Project><PackageReference Include=\"Bin.Package\" /></Project>",
        )
        myFixture.tempDirFixture.createFile(
            "src/obj/Generated.vbproj",
            "<Project><PackageReference Include=\"Obj.Package\" /></Project>",
        )

        assertEquals(listOf("Real.Package"), service().dependenciesForNotes(notes).map { it.displayName })
    }
}
