package dev.pacmon.jetbrains.service

import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.pacmon.jetbrains.settings.Decorations
import dev.pacmon.jetbrains.settings.NoteButtons

/**
 * What the editor needs before it will show a changed setting.
 *
 * The inlay pass stamps the project's PSI modification count onto an editor as
 * it collects hints and skips that editor while the count still matches, so a
 * setting written without moving the count leaves the marks in `package.json`
 * exactly as they were — until the file is edited, or closed and reopened.
 * These tests pin the count moving, which is the contract the pass reads.
 */
class PacmonRefreshTest : BasePlatformTestCase() {
    // The light project — and with it the settings service — is shared between
    // test classes, so every class that writes a setting starts from defaults
    // rather than from whatever the class before it left behind.
    override fun setUp() {
        super.setUp()
        service().resetViewSettings()
    }

    private fun service() = project.getService(PacmonProjectService::class.java)

    private fun modificationCount(): Long =
        PsiModificationTracker.getInstance(project).modificationCount

    fun testChangingASettingMovesThePsiModificationCountTheInlayPassReads() {
        myFixture.configureByText("package.json", """{ "dependencies": { "vue": "^3" } }""")
        val service = service()

        val before = modificationCount()
        service.setNoteButtons(listOf(NoteButtons.CODE_LENS))
        service.settingsChanged()
        assertTrue(
            "Toggling a click target must invalidate the cached hints",
            modificationCount() != before,
        )

        val beforeMarkers = modificationCount()
        service.state.decorations = Decorations.BADGE
        service.settingsChanged()
        assertTrue(
            "Changing the note markers must invalidate the cached hints",
            modificationCount() != beforeMarkers,
        )
    }

    fun testSavingANoteMovesTheCountOnItsOwn() {
        val packageJson = myFixture.tempDirFixture.createFile(
            "package.json",
            """{ "dependencies": { "vue": "^3" } }""",
        )
        val before = modificationCount()
        // Writing the notes file is a PSI change in its own right, which is why
        // the autosave path does not have to drop any caches to be seen.
        service().saveNoteLayers(packageJson, "vue", "Frontend framework", "")
        assertTrue(
            "A saved note must invalidate the cached hints without dropping PSI caches",
            modificationCount() != before,
        )
    }
}
