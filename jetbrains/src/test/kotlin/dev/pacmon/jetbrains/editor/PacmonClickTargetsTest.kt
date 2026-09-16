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

    private fun service() = project.getService(PacmonProjectService::class.java)

    private fun collect(vararg buttons: String): RecordingSink {
        service().setNoteButtons(buttons.toList())
        val provider = PacmonInlayHintsProvider()
        val sink = RecordingSink()
        val collector = provider.getCollectorFor(myFixture.file, myFixture.editor, provider.createSettings(), sink)
            ?: return sink
        val dependency = DependencyPsi.all(myFixture.file).single()
        collector.collect(dependency.property.nameElement.firstChild, myFixture.editor, sink)
        return sink
    }

    private fun configurePackageJson(): VirtualFile {
        myFixture.configureByText("package.json", """{ "dependencies": { "vue": "^3" } }""")
        return myFixture.file.virtualFile
    }

    fun testEachInlayClickTargetIsDrawnOnlyWhenItIsTurnedOn() {
        configurePackageJson()
        val dependency = DependencyPsi.all(myFixture.file).single()

        val icon = collect(NoteButtons.ICON_LEFT)
        assertEquals(listOf(dependency.property.nameElement.textRange.startOffset to false), icon.inline)
        assertEquals(0, icon.blocks)

        val lens = collect(NoteButtons.CODE_LENS)
        assertEmpty(lens.inline)
        assertEquals(1, lens.blocks)

        val chip = collect(NoteButtons.INLAY_HINT)
        assertEquals(listOf(dependency.property.textRange.endOffset to true), chip.inline)
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
        val nameOffset = dependency.property.nameElement.textRange.startOffset + 1
        val versionOffset = dependency.property.value!!.textRange.startOffset + 1
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
        myFixture.editor.caretModel.moveToOffset(dependency.property.textRange.startOffset + 1)
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertEquals("Add a Pacmon note for vue", intention.text)

        myFixture.editor.caretModel.moveToOffset(0)
        assertFalse(intention.isAvailable(project, myFixture.editor, myFixture.file))

        service().setNoteButtons(listOf(NoteButtons.ICON_LEFT))
        myFixture.editor.caretModel.moveToOffset(dependency.property.textRange.startOffset + 1)
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
            DependencyPsi.all(myFixture.file).single().property.textOffset,
        )
        val painter = PacmonLinePainter()

        service().state.decorations = Decorations.PREVIEW
        assertEquals(
            "   ▪ Frontend framework",
            painter.getLineExtensions(project, packageJson, line).single().text,
        )

        service().state.decorations = Decorations.BADGE
        assertEquals("   ▪", painter.getLineExtensions(project, packageJson, line).single().text)

        service().state.decorations = Decorations.OFF
        assertEmpty(painter.getLineExtensions(project, packageJson, line))
    }
}
