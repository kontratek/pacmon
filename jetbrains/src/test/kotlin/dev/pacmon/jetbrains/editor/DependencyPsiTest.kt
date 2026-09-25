package dev.pacmon.jetbrains.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DependencyPsiTest : BasePlatformTestCase() {
    fun testFindsDependenciesAtCaretAcrossSupportedSections() {
        val file = myFixture.configureByText(
            "package.json",
            """
            {
              "dependencies": { "vue": "^3.0.0" },
              "devDependencies": { "vitest": "^4.0.0" },
              "peerDependencies": { "react": "^19.0.0" },
              "optionalDependencies": { "fsevents": "^2.0.0" }
            }
            """.trimIndent(),
        )

        val vueOffset = file.text.indexOf("\"vue\"") + 2
        assertEquals("vue", DependencyPsi.atOffset(file, vueOffset)?.name)
        assertEquals(
            listOf("vue", "vitest", "react", "fsevents"),
            DependencyPsi.all(file).map { it.name },
        )
    }

    fun testIgnoresPropertiesOutsideDependencySections() {
        val file = myFixture.configureByText(
            "package.json",
            """
            {
              "scripts": { "test": "vitest" },
              "dependencies": { "vitest": "^4.0.0" }
            }
            """.trimIndent(),
        )

        val scriptOffset = file.text.indexOf("\"test\"") + 2
        assertNull(DependencyPsi.atOffset(file, scriptOffset))
        assertEquals(listOf("vitest"), DependencyPsi.all(file).map { it.name })
    }

    fun testIgnoresJsonFilesWithOtherNames() {
        val file = myFixture.configureByText("config.json", """{ "dependencies": { "vue": "^3" } }""")
        assertEmpty(DependencyPsi.all(file))
    }

    fun testFindsCargoDependencyBySourceRangeWithoutTomlPsi() {
        val file = myFixture.configureByText(
            "Cargo.toml",
            """
            [dependencies]
            serde = "1"
            "quoted.name" = { version = "2" }
            """.trimIndent(),
        )
        assertEquals(listOf("serde", "quoted.name"), DependencyPsi.all(file).map { it.name })
        assertEquals("serde", DependencyPsi.atOffset(file, file.text.indexOf("serde") + 2)?.name)
    }

    fun testFindsMavenDependencyFromGroupAndArtifactRanges() {
        val file = myFixture.configureByText(
            "pom.xml",
            """
            <project><dependencies><dependency>
              <groupId>org.example</groupId><artifactId>core</artifactId>
            </dependency></dependencies></project>
            """.trimIndent(),
        )
        assertEquals("org.example:core", DependencyPsi.atOffset(file, file.text.indexOf("org.example") + 2)?.name)
        assertEquals("org.example:core", DependencyPsi.atOffset(file, file.text.indexOf("core") + 1)?.name)
    }

    fun testFindsGradleDependencyFromCoordinatesAndCatalogAlias() {
        val file = myFixture.configureByText(
            "build.gradle.kts",
            """
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.17")
                testImplementation(libs.junit.jupiter)
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf("org.slf4j:slf4j-api", "libs.junit.jupiter"),
            DependencyPsi.all(file).map { it.name },
        )
        assertEquals(
            "org.slf4j:slf4j-api",
            DependencyPsi.atOffset(file, file.text.indexOf("slf4j-api") + 2)?.name,
        )
        assertEquals(
            "libs.junit.jupiter",
            DependencyPsi.atOffset(file, file.text.indexOf("libs.junit") + 2)?.name,
        )
    }

    fun testFindsMixDependencyFromAtomRangeWithoutElixirPsi() {
        val file = myFixture.configureByText(
            "mix.exs",
            "defmodule Demo.MixProject do\n  defp deps, do: [{:phoenix, \"~> 1.8\"}]\nend",
        )
        assertEquals(listOf("phoenix"), DependencyPsi.all(file).map { it.name })
        assertEquals("phoenix", DependencyPsi.atOffset(file, file.text.indexOf(":phoenix") + 2)?.name)
        assertNull(DependencyPsi.atOffset(file, file.text.indexOf("~>")))
    }

    fun testFindsZigDependencyFromFieldRangeWithoutZigBrains() {
        val file = myFixture.configureByText(
            "build.zig.zon",
            ".{ .dependencies = .{ .known_folders = .{ .url = \"https://example.test/pkg.tar.gz\" } } }",
        )
        assertEquals(listOf("known_folders"), DependencyPsi.all(file).map { it.name })
        assertEquals(
            "known_folders",
            DependencyPsi.atOffset(file, file.text.indexOf("known_folders") + 2)?.name,
        )
        assertNull(DependencyPsi.atOffset(file, file.text.indexOf("url") + 1))
    }

    fun testFindsPythonDependenciesInPyprojectAndRequirementsFiles() {
        val pyproject = myFixture.configureByText(
            "pyproject.toml",
            "[project]\ndependencies = [\"Requests>=2\", \"importlib_metadata>=7\"]",
        )
        assertEquals(listOf("requests", "importlib-metadata"), DependencyPsi.all(pyproject).map { it.name })
        assertEquals("requests", DependencyPsi.atOffset(pyproject, pyproject.text.indexOf("Requests") + 2)?.name)

        val requirements = myFixture.configureByText("requirements-dev.txt", "pytest>=8\n-r base.txt\n")
        assertEquals(listOf("pytest"), DependencyPsi.all(requirements).map { it.name })
        assertEquals("pytest", DependencyPsi.atOffset(requirements, requirements.text.indexOf("pytest") + 2)?.name)
    }

    fun testFindsNugetDependenciesAtAttributeValues() {
        val project = myFixture.configureByText(
            "App.csproj",
            """
            <Project><ItemGroup>
              <PackageReference Include="Newtonsoft.Json" />
              <PackageReference Update="Imported.Package" />
            </ItemGroup></Project>
            """.trimIndent(),
        )
        assertEquals(listOf("Newtonsoft.Json"), DependencyPsi.all(project).map { it.name })
        assertEquals(
            "Newtonsoft.Json",
            DependencyPsi.atOffset(project, project.text.indexOf("Newtonsoft.Json") + 2)?.name,
        )
        assertNull(DependencyPsi.atOffset(project, project.text.indexOf("Imported.Package") + 2))

        val central = myFixture.configureByText(
            "Directory.Packages.props",
            "<Project><PackageVersion Update=\"Serilog\" /></Project>",
        )
        assertEquals("Serilog", DependencyPsi.atOffset(central, central.text.indexOf("Serilog") + 2)?.name)
    }

    fun testUsesDifferentIconsForDocumentedAndUndocumentedDependencies() {
        assertSame(PacmonIcons.Documented, PacmonIcons.forNote(true))
        assertSame(PacmonIcons.Undocumented, PacmonIcons.forNote(false))
        assertNotSame(PacmonIcons.Documented, PacmonIcons.Undocumented)
    }
}
