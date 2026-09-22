package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCoreTest {
    @Test
    fun `registry matches only supported manifests in priority order`() {
        assertEquals(listOf("package.json", "Cargo.toml", "pom.xml"), ManifestRegistry.adapters.map { it.fileName })
        assertEquals(ManifestKind.NPM, ManifestRegistry.forFileName("package.json")?.kind)
        assertEquals(ManifestKind.CARGO, ManifestRegistry.forFileName("Cargo.toml")?.kind)
        assertEquals(ManifestKind.MAVEN, ManifestRegistry.forFileName("pom.xml")?.kind)
        assertNull(ManifestRegistry.forFileName("build.gradle.kts"))
    }

    @Test
    fun `npm extracts four dependency scopes with source ranges`() {
        val text = """
            {
              // jsonc is accepted
              "dependencies": { "vue": "^3", },
              "devDependencies": { "vitest": "^4" },
              "peerDependencies": { "react": "^19" },
              "optionalDependencies": { "fsevents": "^2" },
              "scripts": { "test": "vitest" }
            }
        """.trimIndent()
        val dependencies = NpmManifestAdapter.extractDependencies(text)
        assertEquals(listOf("vue", "vitest", "react", "fsevents"), dependencies.map { it.noteKey })
        assertEquals(
            listOf("dependencies", "devDependencies", "peerDependencies", "optionalDependencies"),
            dependencies.map { it.scope },
        )
        val vue = dependencies.first()
        assertEquals("\"vue\"", text.substring(vue.primaryRange.offset, vue.primaryRange.offset + vue.primaryRange.length))
        assertEquals(vue, ManifestRegistry.dependencyAtOffset(dependencies, vue.primaryRange.offset + 2))
    }

    @Test
    fun `cargo extracts direct target table alias and workspace declarations`() {
        val text = """
            [dependencies]
            serde = "1"
            local = { package = "upstream", version = "2" }
            "quoted.name" = { workspace = true }
            workspace-crate.workspace = true

            [dev-dependencies]
            insta = "1"

            [build-dependencies.bindgen]
            version = "0.70"

            [target.'cfg(unix)'.dependencies]
            nix = {
              version = "0.29"
            }

            [target."cfg(windows)".dependencies.windows-sys]
            version = "0.59"

            [workspace.dependencies]
            ignored = "1"
        """.trimIndent()
        val dependencies = CargoManifestAdapter.extractDependencies(text)
        assertEquals(
            listOf("serde", "local", "quoted.name", "workspace-crate", "insta", "bindgen", "nix", "windows-sys"),
            dependencies.map { it.noteKey },
        )
        assertEquals("local", dependencies[1].displayName)
        assertEquals("build-dependencies", dependencies[5].scope)
        assertEquals("target:cfg(unix)/dependencies", dependencies[6].scope)
        assertEquals("target:cfg(windows)/dependencies", dependencies[7].scope)
        dependencies.forEach { dependency ->
            assertTrue(text.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length).isNotBlank())
        }
    }

    @Test
    fun `maven extracts project and profile dependencies and excludes managed and plugins`() {
        val text = """
            <?xml version="1.0"?>
            <m:project xmlns:m="urn:test">
              <m:dependencies>
                <m:dependency>
                  <m:groupId>org.example</m:groupId>
                  <m:artifactId><![CDATA[core-lib]]></m:artifactId>
                </m:dependency>
                <!-- retained coordinates -->
                <m:dependency>
                  <m:groupId>${'$'}{company.group}</m:groupId>
                  <m:artifactId>api&amp;client</m:artifactId>
                  <m:scope>runtime</m:scope>
                </m:dependency>
              </m:dependencies>
              <m:dependencyManagement><m:dependencies><m:dependency>
                <m:groupId>ignored</m:groupId><m:artifactId>managed</m:artifactId>
              </m:dependency></m:dependencies></m:dependencyManagement>
              <m:build><m:plugins><m:plugin><m:dependencies><m:dependency>
                <m:groupId>ignored</m:groupId><m:artifactId>plugin</m:artifactId>
              </m:dependency></m:dependencies></m:plugin></m:plugins></m:build>
              <m:profiles><m:profile><m:id>fast</m:id><m:dependencies><m:dependency>
                <m:groupId>org.junit</m:groupId><m:artifactId>junit</m:artifactId><m:scope>test</m:scope>
              </m:dependency></m:dependencies></m:profile></m:profiles>
            </m:project>
        """.trimIndent()
        val dependencies = MavenManifestAdapter.extractDependencies(text)
        assertEquals(
            listOf("org.example:core-lib", "${'$'}{company.group}:api&client", "org.junit:junit"),
            dependencies.map { it.noteKey },
        )
        assertEquals(listOf("compile", "runtime", "profile:fast/test"), dependencies.map { it.scope })
        assertEquals(2, dependencies.first().sourceRanges.size)
        assertEquals("core-lib", text.substring(
            dependencies.first().primaryRange.offset,
            dependencies.first().primaryRange.offset + dependencies.first().primaryRange.length,
        ))
    }

    @Test
    fun `malformed input is safe`() {
        assertTrue(NpmManifestAdapter.extractDependencies("{\"dependencies\": {").isEmpty())
        assertTrue(CargoManifestAdapter.extractDependencies("[dependencies\nserde = {").isEmpty())
        assertTrue(MavenManifestAdapter.extractDependencies("<project><dependencies><dependency>").isEmpty())
    }
}
