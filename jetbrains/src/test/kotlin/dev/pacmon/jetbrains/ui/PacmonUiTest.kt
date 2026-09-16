package dev.pacmon.jetbrains.ui

import com.intellij.codeInsight.hints.BlockConstraints
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import dev.pacmon.jetbrains.editor.DependencyPsi
import dev.pacmon.jetbrains.editor.PacmonInlayHintsProvider
import dev.pacmon.jetbrains.editor.PacmonLinePainter
import dev.pacmon.jetbrains.service.PacmonProjectService
import java.awt.Font

class PacmonUiTest : BasePlatformTestCase() {
    private class RecordingSink : InlayHintsSink {
        var inlineOffset: Int? = null

        override fun addInlineElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            presentation: InlayPresentation,
            placeAtTheEndOfLine: Boolean,
        ) {
            inlineOffset = offset
        }

        override fun addBlockElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            showAbove: Boolean,
            priority: Int,
            presentation: InlayPresentation,
        ) = Unit

        override fun addInlineElement(
            offset: Int,
            presentation: RootInlayPresentation<*>,
            constraints: HorizontalConstraints?,
        ) = Unit

        override fun addBlockElement(
            logicalLine: Int,
            showAbove: Boolean,
            presentation: RootInlayPresentation<*>,
            constraints: BlockConstraints?,
        ) = Unit
    }

    fun testLinePainterShowsAnItalicPreviewOnlyForDocumentedDependencies() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """
            {
              "dependencies": {
                "vue": "^3",
                "react": "^19"
              }
            }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(packageJson)
        val psi = myFixture.file
        myFixture.tempDirFixture.createFile(
            ".pacmon/DEPENDENCY-NOTES.md",
            "## vue\n\nFrontend framework\n",
        )
        val dependencies = DependencyPsi.all(psi).associateBy { it.name }
        val document = myFixture.editor.document
        val painter = PacmonLinePainter()
        val vueLine = document.getLineNumber(dependencies.getValue("vue").property.textOffset)
        val reactLine = document.getLineNumber(dependencies.getValue("react").property.textOffset)

        val preview = painter.getLineExtensions(project, packageJson, vueLine).single()
        assertEquals("   \u25AA Frontend framework", preview.text)
        assertEquals(Font.ITALIC, preview.fontType)
        assertEmpty(painter.getLineExtensions(project, packageJson, reactLine))
    }

    fun testInlayIconProviderOnlyRunsInPackageJson() {
        val provider = PacmonInlayHintsProvider()
        myFixture.configureByText("package.json", """{ "dependencies": { "vue": "^3" } }""")
        val dependency = DependencyPsi.all(myFixture.file).single()
        val sink = RecordingSink()
        val collector = provider.getCollectorFor(myFixture.file, myFixture.editor, provider.createSettings(), sink)
        assertNotNull(collector)
        assertTrue(collector!!.collect(dependency.property.nameElement.firstChild, myFixture.editor, sink))
        assertEquals(dependency.property.nameElement.textRange.startOffset, sink.inlineOffset)

        myFixture.configureByText("config.json", """{ "dependencies": { "vue": "^3" } }""")
        assertNull(provider.getCollectorFor(myFixture.file, myFixture.editor, provider.createSettings(), RecordingSink()))
    }

    fun testProjectUsesOneToolWindowService() {
        assertSame(
            project.getService(PacmonToolWindowService::class.java),
            project.getService(PacmonToolWindowService::class.java),
        )
    }

    fun testDashboardCoverageMatchesTheActivePackageAndKeepsItsContext() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "packages/app/package.json",
            """{ "dependencies": { "vue": "^3", "react": "^19" } }""",
        )
        myFixture.tempDirFixture.createFile(
            "packages/app/.pacmon/DEPENDENCY-NOTES.md",
            "## vue\n\nFrontend framework\n",
        )
        myFixture.configureFromExistingVirtualFile(packageJson)
        val dashboard = PacmonDashboardPanel(project)
        dashboard.refresh()

        val coverage = dashboard.coverageForTest()
        assertEquals("1 of 2", coverage.ratio)
        assertTrue(coverage.packagePath.endsWith("packages/app/package.json"))
        assertEquals(50, coverage.percent)
        assertEquals("1 dependency has no note yet.", coverage.status)
        assertTrue(coverage.barVisible)

        myFixture.configureByText("README.txt", "project")
        dashboard.refresh()
        assertEquals(coverage.packagePath, dashboard.coverageForTest().packagePath)
    }

    fun testPanelAutosavesBothLayersAndCreatesAgentRules() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """{ "dependencies": { "vue": "^3" } }""",
        )
        val panel = PacmonToolWindowPanel(project)
        try {
            panel.showTarget(PacmonTarget(packageJson, "vue", "dependencies"))
            panel.setLayersForTest("Frontend framework", "- purpose: UI\n- runtime: client")

            val service = project.getService(PacmonProjectService::class.java)
            var note = service.noteFor(packageJson, "vue")
            var attempts = 0
            while (note == null && attempts++ < 20) {
                Thread.sleep(100)
                UIUtil.dispatchAllInvocationEvents()
                note = service.noteFor(packageJson, "vue")
            }

            assertEquals("Frontend framework", note?.layers?.human)
            assertEquals("- purpose: UI\n- runtime: client", note?.layers?.agent)
            val rulesPath = project.basePath?.replace('\\', '/') + "/.pacmon/AGENT-RULES.md"
            assertNotNull(com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(rulesPath))
        } finally {
            panel.dispose()
        }
    }

    fun testSavingAnExistingCrLfNotesFileDoesNotPassCrLfToTheDocument() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """{ "dependencies": { "vue": "^3" } }""",
        )
        val notesFile = myFixture.tempDirFixture.createFile(
            ".pacmon/DEPENDENCY-NOTES.md",
            "---\r\nformat: dependency-notes/1\r\nlang: en\r\n---\r\n\r\n" +
                "# Dependency Notes\r\n\r\n## vue\r\n\r\nold note\r\n",
        )

        project.getService(PacmonProjectService::class.java)
            .saveNoteLayers(packageJson, "vue", "new note", "- purpose: UI framework")

        val saved = String(notesFile.contentsToByteArray(), Charsets.UTF_8)
        assertTrue(saved.contains("\r\n"))
        assertFalse(Regex("(?<!\\r)\\n").containsMatchIn(saved))
        assertTrue(saved.contains("new note\r\n\r\n### Agent notes\r\n"))
    }

    fun testExternalChangeDoesNotOverwriteDirtyPanel() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """{ "dependencies": { "vue": "^3" } }""",
        )
        val panel = PacmonToolWindowPanel(project)
        try {
            panel.showTarget(PacmonTarget(packageJson, "vue", "dependencies"))
            panel.setLayersForTest("local edit", "")
            val notesPath = "${packageJson.parent.path}/.pacmon/DEPENDENCY-NOTES.md"
            panel.externalNotesChanged(setOf(notesPath))
            UIUtil.dispatchAllInvocationEvents()
            assertTrue(panel.hasConflictForTest())

            panel.saveForTest()
            assertEquals(
                "local edit",
                project.getService(PacmonProjectService::class.java).noteFor(packageJson, "vue")?.layers?.human,
            )
        } finally {
            panel.dispose()
        }
    }
}
