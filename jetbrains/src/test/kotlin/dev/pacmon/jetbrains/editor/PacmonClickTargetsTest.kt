package dev.pacmon.jetbrains.editor

import com.intellij.codeInsight.hints.BlockConstraints
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.VirtualFile
import dev.pacmon.jetbrains.service.PacmonProjectService
import dev.pacmon.jetbrains.settings.Decorations
import dev.pacmon.jetbrains.settings.NoteButtons

/**
 * The five click targets and the three note-marker choices the Pacmon tool
 * window offers, each drawn only when it is turned on — the JetBrains side of
 * the VS Code extension's `pacmon.noteButtons` and `pacmon.decorations`.
 */
class PacmonClickTargetsTest : BasePlatformTestCase() {
    private class RecordingSink : InlayHintsSink {
        val inline = mutableListOf<Pair<Int, Boolean>>()
        var blocks = 0

        override fun addInlineElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            presentation: InlayPresentation,
            placeAtTheEndOfLine: Boolean,
        ) {
            inline.add(offset to placeAtTheEndOfLine)
        }

        override fun addBlockElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            showAbove: Boolean,
            priority: Int,
            presentation: InlayPresentation,
        ) {
            blocks++
        }

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

    // The light project — and with it the settings service — is shared between
    // test classes, so every class that writes a setting starts from defaults
    // rather than from whatever the class before it left behind.
    override fun setUp() {
        super.setUp()
        service().resetViewSettings()
    }

    private fun service() = project.getService(PacmonProjectService::class.java)

    private fun collect(vararg buttons: String): RecordingSink {
        service().setNoteButtons(buttons.toList())
        val provider = PacmonInlayHintsProvider()
        val sink = RecordingSink()
        val collector = provider.getCollectorFor(myFixture.file, myFixture.editor, provider.createSettings(), sink)
            ?: return sink
        collector.collect(myFixture.file, myFixture.editor, sink)
        return sink
    }

    private fun configurePackageJson(): VirtualFile {
        myFixture.configureByText(
            "package.json",
            """
            {
              "dependencies": {
                "vue": "^3"
              }
            }
            """.trimIndent(),
        )
        return myFixture.file.virtualFile
    }

    fun testEachInlayClickTargetIsDrawnOnlyWhenItIsTurnedOn() {
        configurePackageJson()
        val dependency = DependencyPsi.all(myFixture.file).single()

        val icon = collect(NoteButtons.ICON_LEFT)
        assertEquals(listOf(dependency.iconRange.offset to false), icon.inline)
        assertEquals(0, icon.blocks)

        val lens = collect(NoteButtons.CODE_LENS)
        assertEmpty(lens.inline)
        assertEquals(1, lens.blocks)

        val chip = collect(NoteButtons.INLAY_HINT)
        val lineEnd = myFixture.editor.document.getLineEndOffset(
            myFixture.editor.document.getLineNumber(dependency.primaryRange.offset),
        )
        assertEquals(listOf(lineEnd to true), chip.inline)
        assertEquals(0, chip.blocks)

        val all = collect(NoteButtons.ICON_LEFT, NoteButtons.CODE_LENS, NoteButtons.INLAY_HINT)
        assertEquals(2, all.inline.size)
        assertEquals(1, all.blocks)
    }

    fun testNoInlayCollectorAtAllWhenEveryInlayTargetIsOff() {
        configurePackageJson()
        // "link" and "lightbulb" are the two targets that are not inlays, so
        // the provider must step aside entirely rather than walk the file.
        service().setNoteButtons(listOf(NoteButtons.LINK, NoteButtons.LIGHTBULB))
        val provider = PacmonInlayHintsProvider()
        assertNull(
            provider.getCollectorFor(myFixture.file, myFixture.editor, provider.createSettings(), RecordingSink()),
        )
    }

    fun testCtrlClickTargetFollowsTheLinkSettingAndOnlyTheNameHalf() {
        configurePackageJson()
        val dependency = DependencyPsi.all(myFixture.file).single()
        val nameOffset = dependency.primaryRange.offset + 1
        val versionOffset = myFixture.file.text.indexOf("^3")
        val handler = PacmonNoteDeclarationHandler()

        service().setNoteButtons(listOf(NoteButtons.LINK))
        val targets = handler.getGotoDeclarationTargets(
            myFixture.file.findElementAt(nameOffset),
            nameOffset,
            myFixture.editor,
        )
        assertEquals(1, targets?.size)
        val target = targets!!.single() as com.intellij.pom.Navigatable
        assertTrue(target.canNavigate())
        // Claiming no file is what keeps go-to-declaration from "navigating" by
        // moving the caret onto the name that was just clicked.
        assertNull(targets.single().containingFile)

        assertNull(
            handler.getGotoDeclarationTargets(
                myFixture.file.findElementAt(versionOffset),
                versionOffset,
                myFixture.editor,
            ),
        )

        service().setNoteButtons(listOf(NoteButtons.ICON_LEFT))
        assertNull(
            handler.getGotoDeclarationTargets(myFixture.file.findElementAt(nameOffset), nameOffset, myFixture.editor),
        )
    }

    fun testLightbulbFollowsItsSettingAndTheCaretLine() {
        configurePackageJson()
        val dependency = DependencyPsi.all(myFixture.file).single()
        val intention = PacmonAddNoteIntention()

        service().setNoteButtons(listOf(NoteButtons.LIGHTBULB))
        myFixture.editor.caretModel.moveToOffset(dependency.primaryRange.offset + 1)
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertEquals("Add a Pacmon note for vue", intention.text)

        myFixture.editor.caretModel.moveToOffset(0)
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))

        service().setNoteButtons(listOf(NoteButtons.ICON_LEFT))
        myFixture.editor.caretModel.moveToOffset(dependency.primaryRange.offset + 1)
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun testNoteMarkersShowThePreview_thenTheBareBadge_thenNothing() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """{ "dependencies": { "vue": "^3" } }""",
        )
        myFixture.tempDirFixture.createFile(".pacmon/DEPENDENCY-NOTES.md", "## vue\n\nFrontend framework\n")
        myFixture.configureFromExistingVirtualFile(packageJson)
        val line = myFixture.editor.document.getLineNumber(
            DependencyPsi.all(myFixture.file).single().primaryRange.offset,
        )
        val painter = PacmonLinePainter()

        service().state.decorations = Decorations.PREVIEW
        assertEquals(
            "   ▪ Frontend framework",
            painter.getLineExtensions(project, packageJson, line).single().text,
        )

        // The badge is the marker AND the word, the way the VS Code extension
        // writes it — the marker on its own reads as a stray character.
        service().state.decorations = Decorations.BADGE
        assertEquals("   ▪ note", painter.getLineExtensions(project, packageJson, line).single().text)

        service().state.decorations = Decorations.OFF
        assertEmpty(painter.getLineExtensions(project, packageJson, line))
    }

    fun testCargoAndMavenUseTheSameInlayLinkAndDecorationSurfaces() {
        val cargo = myFixture.tempDirFixture.createFile("Cargo.toml", "[dependencies]\nserde = \"1\"\n")
        myFixture.tempDirFixture.createFile(
            ".pacmon/cargo/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: cargo\nlang: en\n---\n# Dependency Notes\n\n## serde\n\nSerialization\n",
        )
        myFixture.configureFromExistingVirtualFile(cargo)
        val cargoDependency = DependencyPsi.all(myFixture.file).single()
        service().setNoteButtons(listOf(NoteButtons.ICON_LEFT, NoteButtons.LINK))
        assertEquals(listOf(cargoDependency.iconRange.offset to false), collect(NoteButtons.ICON_LEFT).inline)
        val cargoLine = myFixture.editor.document.getLineNumber(cargoDependency.primaryRange.offset)
        assertEquals("   ▪ Serialization", PacmonLinePainter().getLineExtensions(project, cargo, cargoLine).single().text)

        val pom = myFixture.tempDirFixture.createFile(
            "pom.xml",
            """
            <project><dependencies>
              <dependency>
                <groupId>org.example</groupId>
                <artifactId>core</artifactId>
              </dependency>
            </dependencies></project>
            """.trimIndent(),
        )
        myFixture.tempDirFixture.createFile(
            ".pacmon/maven/DEPENDENCY-NOTES.md",
            "---\nformat: dependency-notes/2\necosystem: maven\nlang: en\n---\n# Dependency Notes\n\n## org.example:core\n\nCore library\n",
        )
        myFixture.configureFromExistingVirtualFile(pom)
        val mavenDependency = DependencyPsi.all(myFixture.file).single()
        assertEquals(myFixture.file.text.indexOf("<dependency>"), mavenDependency.iconRange.offset)
        assertEquals(listOf(mavenDependency.iconRange.offset to false), collect(NoteButtons.ICON_LEFT).inline)
        assertTrue(
            myFixture.editor.document.getLineNumber(mavenDependency.iconRange.offset) !=
                myFixture.editor.document.getLineNumber(mavenDependency.primaryRange.offset),
        )
        val handler = PacmonNoteDeclarationHandler()
        service().setNoteButtons(listOf(NoteButtons.LINK))
        for (value in listOf("org.example", "core")) {
            val offset = myFixture.file.text.indexOf(value) + 1
            assertEquals(
                1,
                handler.getGotoDeclarationTargets(
                    myFixture.file.findElementAt(offset),
                    offset,
                    myFixture.editor,
                )?.size,
            )
        }
    }
}
