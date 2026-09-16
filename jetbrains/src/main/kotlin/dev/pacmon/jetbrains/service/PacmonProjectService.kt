package dev.pacmon.jetbrains.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import dev.pacmon.jetbrains.core.AiInstructions
import dev.pacmon.jetbrains.core.InlineSource
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.core.NotesFileModel
import dev.pacmon.jetbrains.core.SectionLayers
import dev.pacmon.jetbrains.settings.Decorations
import dev.pacmon.jetbrains.settings.InlineSources
import dev.pacmon.jetbrains.settings.MonorepoModes
import dev.pacmon.jetbrains.settings.NoteButtons
import dev.pacmon.jetbrains.settings.NoteEntries

@Service(Service.Level.PROJECT)
@State(name = "PacmonSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class PacmonProjectService(private val project: Project) :
    com.intellij.openapi.components.PersistentStateComponent<PacmonProjectService.Settings>,
    Disposable {
    /**
     * The same five settings the VS Code extension contributes under `pacmon.*`,
     * with the same names and the same defaults — see `package.json` there and
     * `dev.pacmon.jetbrains.settings.PacmonOptions` for the values each accepts.
     * `noteButtons` is a list because the click targets are not exclusive: any
     * combination of them may be on at once, including none.
     */
    data class Settings(
        var monorepoMode: String = MonorepoModes.NEAREST,
        var inlineSource: String = InlineSources.HUMAN_FIRST,
        var noteEntry: String = NoteEntries.PANEL,
        var decorations: String = Decorations.PREVIEW,
        var noteButtons: MutableList<String> = NoteButtons.DEFAULT.toMutableList(),
    )

    data class Note(
        val file: VirtualFile,
        val model: NotesFileModel,
        val layers: SectionLayers,
    )

    private var settings = Settings()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.none { isRelevantPath(it.path) }) return
                val changedPacmonFiles = events.map { it.path.replace('\\', '/') }
                    .filter { it.endsWith("/package.json") || it.endsWith("/${NotesCore.NOTES_RELATIVE_PATH}") }
                    .toSet()
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    refreshOpenEditors()
                    if (changedPacmonFiles.isNotEmpty()) {
                        project.messageBus.syncPublisher(PacmonNotesListener.TOPIC).notesChanged(changedPacmonFiles)
                    }
                }
            }
        })
    }

    override fun getState(): Settings = settings

    override fun loadState(state: Settings) {
        settings = state
    }

    override fun dispose() = Unit

    /**
     * Every setting in the tool window changes what `package.json` looks like,
     * so the open editors have to be told — and restarting the daemon on its
     * own does not tell them.
     *
     * The inlay pass stamps the project's PSI modification count onto each
     * editor as it collects hints, and skips the editor entirely on the next
     * pass while that count still matches. Nothing about ticking a click
     * target moves it, so the marks stayed exactly as they were until the file
     * was edited, or closed and reopened. Dropping the PSI caches moves the
     * count, which is what makes the pass run again. It is a heavier hammer
     * than the job wants, but the alternative is the internal API the platform
     * uses for its own inlay settings, and this happens once per click in a
     * settings panel, never in a loop.
     */
    fun settingsChanged() {
        PsiManager.getInstance(project).dropPsiCaches()
        refreshOpenEditors()
    }

    /**
     * The daemon for everything it produces, and a repaint for the end-of-line
     * markers, which [dev.pacmon.jetbrains.editor.PacmonLinePainter] draws
     * straight onto the editor rather than through a pass — nothing the daemon
     * does would bring them back.
     */
    private fun refreshOpenEditors() {
        DaemonCodeAnalyzer.getInstance(project).restart()
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project == project) editor.contentComponent.repaint()
        }
    }

    fun inlineSource(): InlineSource = when (settings.inlineSource) {
        InlineSources.AI_FIRST -> InlineSource.AI_FIRST
        InlineSources.HUMAN_ONLY -> InlineSource.HUMAN_ONLY
        InlineSources.AI_ONLY -> InlineSource.AI_ONLY
        else -> InlineSource.HUMAN_FIRST
    }

    /** Whether one of the five click targets is on. Unknown ids are off. */
    fun noteButtonEnabled(id: String): Boolean = id in settings.noteButtons

    fun noteButtons(): List<String> = NoteButtons.ALL.filter(::noteButtonEnabled)

    /**
     * Writes the click targets in the canonical order, so the stored value reads
     * the same however the boxes were ticked, and unknown ids never survive.
     */
    fun setNoteButtons(ids: Collection<String>) {
        settings.noteButtons = NoteButtons.ALL.filter { it in ids }.toMutableList()
    }

    fun decorations(): String = settings.decorations

    fun noteEntry(): String = settings.noteEntry

    /** Everything the tool window controls, back to the shipped defaults. */
    fun resetViewSettings() {
        val defaults = Settings()
        settings.inlineSource = defaults.inlineSource
        settings.noteEntry = defaults.noteEntry
        settings.decorations = defaults.decorations
        settings.noteButtons = defaults.noteButtons
    }

    fun noteFor(packageJson: VirtualFile, dependency: String): Note? {
        val notesFile = resolveNotesFile(packageJson) ?: return null
        val text = textOf(notesFile)
        val model = NotesCore.parse(text)
        val section = NotesCore.findSection(model, dependency) ?: return null
        val layers = NotesCore.layers(model, section)
        if (layers.human.isBlank() && layers.agent.isBlank() && layers.generated.isBlank()) return null
        return Note(notesFile, model, layers)
    }

    fun resolveNotesFile(packageJson: VirtualFile): VirtualFile? {
        val root = projectRoot() ?: return null
        if (settings.monorepoMode == MonorepoModes.ROOT_ONLY) return notesIn(root)
        var directory: VirtualFile? = packageJson.parent
        repeat(64) {
            val current = directory ?: return null
            notesIn(current)?.let { return it }
            if (current == root || !VfsUtilCore.isAncestor(root, current, false)) return null
            directory = current.parent
        }
        return null
    }

    fun creationTargetDirectory(packageJson: VirtualFile): VirtualFile = packageJson.parent

    fun saveNoteLayers(packageJson: VirtualFile, dependency: String, human: String, agent: String): VirtualFile {
        var result: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Update dependency note", null, {
            val existing = resolveNotesFile(packageJson)
            val notesFile = existing ?: createNotesFile(creationTargetDirectory(packageJson))
            val current = if (existing == null) "" else textOf(notesFile)
            val updated = if (existing == null || current.isBlank()) {
                NotesCore.newNotesFile(dependency, human, agent)
            } else {
                NotesCore.upsertNoteLayers(current, dependency, human, agent)
            }
            val document = FileDocumentManager.getInstance().getDocument(notesFile)
                ?: error("Could not open the dependency notes file.")
            // IntelliJ documents always use LF internally. FileDocumentManager
            // restores the file's detected separator (for example CRLF) when
            // saving it back to disk.
            document.setText(StringUtil.convertLineSeparators(updated))
            FileDocumentManager.getInstance().saveDocument(document)
            ensureAgentRules()
            result = notesFile
        })
        val saved = checkNotNull(result)
        notifyNotesChanged(saved.path)
        return saved
    }

    fun open(file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /**
     * Two writes: `.pacmon/AGENT-RULES.md` is rewritten from the plugin's own
     * copy (the rules and the field list, owned by Pacmon), and each of
     * [targets] (relative to the project root, e.g. `AGENTS.md`) gets the
     * three-line pointer to it, inserted or updated between markers. Returns
     * every path actually written, `AGENT-RULES.md` first.
     */
    fun setUpAiInstructions(targets: List<String>): List<String> {
        val root = projectRoot() ?: return emptyList()
        val written = mutableListOf<String>()
        WriteCommandAction.runWriteCommandAction(project, "Set Up AI Instructions", null, {
            ensureAgentRules(overwrite = true)
            written.add(NotesCore.AGENT_RULES_RELATIVE_PATH)
            for (relative in targets) {
                val parts = relative.split('/')
                var directory = root
                for (part in parts.dropLast(1)) {
                    directory = directory.findChild(part) ?: directory.createChildDirectory(this, part)
                }
                val fileName = parts.last()
                val file = directory.findChild(fileName) ?: directory.createChildData(this, fileName)
                val document = FileDocumentManager.getInstance().getDocument(file)
                    ?: error("Could not open $relative.")
                document.setText(StringUtil.convertLineSeparators(AiInstructions.upsert(document.text)))
                FileDocumentManager.getInstance().saveDocument(document)
                written.add(relative)
            }
        })
        return written
    }

    private fun createNotesFile(packageDirectory: VirtualFile): VirtualFile {
        val notesDirectory = packageDirectory.findChild(NotesCore.NOTES_DIRECTORY)
            ?: packageDirectory.createChildDirectory(this, NotesCore.NOTES_DIRECTORY)
        return notesDirectory.findChild(NotesCore.NOTES_FILE_NAME)
            ?: notesDirectory.createChildData(this, NotesCore.NOTES_FILE_NAME)
    }

    private fun projectRoot(): VirtualFile? = project.basePath
        ?.replace('\\', '/')
        ?.let { LocalFileSystem.getInstance().findFileByPath(it) }

    private fun ensureAgentRules(overwrite: Boolean = false) {
        val root = projectRoot() ?: return
        val notesDirectory = root.findChild(NotesCore.NOTES_DIRECTORY)
            ?: root.createChildDirectory(this, NotesCore.NOTES_DIRECTORY)
        val existing = notesDirectory.findChild(NotesCore.AGENT_RULES_FILE_NAME)
        if (existing != null && !overwrite) return
        val rules = javaClass.getResourceAsStream("/pacmon/AGENT-RULES.md")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Bundled AGENT-RULES.md is missing.")
        val file = existing ?: notesDirectory.createChildData(this, NotesCore.AGENT_RULES_FILE_NAME)
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: error("Could not create AGENT-RULES.md.")
        document.setText(rules.replace("\r\n", "\n"))
        FileDocumentManager.getInstance().saveDocument(document)
    }

    /**
     * A saved note moves the PSI modification count by itself — the notes file
     * was just written — so this path only has to ask for the refresh, not
     * force one. It runs on every autosave, which is roughly every keystroke,
     * so it must stay cheap.
     */
    private fun notifyNotesChanged(path: String) {
        refreshOpenEditors()
        project.messageBus.syncPublisher(PacmonNotesListener.TOPIC)
            .notesChanged(setOf(path.replace('\\', '/')))
    }

    private fun notesIn(directory: VirtualFile): VirtualFile? =
        directory.findChild(NotesCore.NOTES_DIRECTORY)?.findChild(NotesCore.NOTES_FILE_NAME)

    private fun textOf(file: VirtualFile): String = ReadAction.compute<String, RuntimeException> {
        FileDocumentManager.getInstance().getCachedDocument(file)?.text ?: VfsUtilCore.loadText(file)
    }

    private fun isRelevantPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.endsWith("/package.json") || normalized.endsWith("/${NotesCore.NOTES_RELATIVE_PATH}")
    }
}
