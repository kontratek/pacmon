package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PythonManifestTest {
    @Test
    fun `matches common Python manifest layouts`() {
        assertEquals(ManifestKind.PYTHON, ManifestRegistry.forPath("/repo/pyproject.toml")?.kind)
        assertEquals(ManifestKind.PYTHON, ManifestRegistry.forPath("/repo/requirements-dev.txt")?.kind)
        assertEquals(ManifestKind.PYTHON, ManifestRegistry.forPath("/repo/requirements/docs.txt")?.kind)
        assertNull(ManifestRegistry.forPath("/repo/dependencies.txt"))
        assertNull(ManifestRegistry.forPath("/repo/requirements.in"))
    }

    @Test
    fun `extracts standard Poetry uv and build dependencies`() {
        val text = """
            [build-system]
            requires = ["setuptools>=68", "wheel"]

            [project]
            dependencies = [
              "Requests[security]>=2.31; python_version >= '3.10'",
              "importlib_metadata @ https://example.test/importlib.whl",
            ]

            [project.optional-dependencies]
            docs = ["Sphinx>=7"]

            [dependency-groups]
            test = ["pytest>=8", { include-group = "coverage" }]

            [tool.poetry.dependencies]
            python = "^3.12"
            Django = "^5"
            local_lib = { path = "../local-lib" }

            [tool.poetry.dev-dependencies]
            Black = "^24"

            [tool.poetry.group.lint.dependencies]
            Ruff = "^0.9"

            [tool.uv]
            dev-dependencies = ["mypy>=1"]

            [tool.uv.sources]
            requests = { git = "https://example.test/requests.git" }
        """.trimIndent()

        val dependencies = PythonManifestAdapter.extractPyproject(text)
        assertEquals(
            listOf(
                "build-system:setuptools",
                "build-system:wheel",
                "project:requests",
                "project:importlib-metadata",
                "extra:docs:sphinx",
                "group:test:pytest",
                "poetry:main:django",
                "poetry:main:local-lib",
                "poetry:dev:black",
                "poetry:group:lint:ruff",
                "uv:dev:mypy",
            ),
            dependencies.map { "${it.scope}:${it.noteKey}" },
        )
        dependencies.forEach { dependency ->
            assertEquals(
                dependency.displayName,
                text.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length),
            )
        }
    }

    @Test
    fun `extracts named pip requirements and ignores directives`() {
        val text = """
            # application dependencies
            Requests[security]>=2.31 ; python_version >= "3.10"
            urllib3 @ https://example.test/urllib3.whl --hash=sha256:abc
            -e git+https://example.test/acme.git#egg=Acme_Plugin
            -r base.txt
            --constraint constraints.txt
            --index-url https://example.test/simple
            ./local-project
            http://example.test/unnamed.whl
            typing_extensions==4.12 \
              --hash=sha256:def
        """.trimIndent()

        val dependencies = PythonManifestAdapter.extractRequirements(text, "requirements/dev.txt")
        assertEquals(
            listOf(
                "requirements/dev.txt:requests",
                "requirements/dev.txt:urllib3",
                "requirements/dev.txt:acme-plugin",
                "requirements/dev.txt:typing-extensions",
            ),
            dependencies.map { "${it.scope}:${it.noteKey}" },
        )
        dependencies.forEach { dependency ->
            assertEquals(
                dependency.displayName,
                text.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length),
            )
        }
    }

    @Test
    fun `normalizes Python distribution names`() {
        assertEquals("importlib-metadata", PythonManifestAdapter.normalizePackageName("Importlib_Metadata"))
        assertEquals("zope-interface", PythonManifestAdapter.normalizePackageName("zope.interface"))
    }
}
