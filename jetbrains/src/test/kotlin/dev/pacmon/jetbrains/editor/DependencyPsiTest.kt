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
}
