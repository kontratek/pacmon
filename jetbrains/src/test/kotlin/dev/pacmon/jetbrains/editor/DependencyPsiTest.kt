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

    fun testUsesDifferentIconsForDocumentedAndUndocumentedDependencies() {
        assertSame(PacmonIcons.Documented, PacmonIcons.forNote(true))
        assertSame(PacmonIcons.Undocumented, PacmonIcons.forNote(false))
        assertNotSame(PacmonIcons.Documented, PacmonIcons.Undocumented)
    }
}
