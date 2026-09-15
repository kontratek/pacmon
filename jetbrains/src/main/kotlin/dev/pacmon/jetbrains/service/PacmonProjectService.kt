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
import dev.pacmon.jetbrains.core.InlineSource
import dev.pacmon.jetbrains.core.NotesCore
import dev.pacmon.jetbrains.core.NotesFileModel
import dev.pacmon.jetbrains.core.SectionLayers

@Service(Service.Level.PROJECT)
@State(name = "PacmonSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class PacmonProjectService(private val project: Project) :
    com.intellij.openapi.components.PersistentStateComponent<PacmonProjectService.Settings>,
    Disposable {
    data class Settings(
        var monorepoMode: String = "nearest",
        var inlineSource: String = "human-first",
        var showIcons: Boolean = true,
        var showPreviews: Boolean = true,
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
                    DaemonCodeAnalyzer.getInstance(project).restart()
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

    fun settingsChanged() {
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    fun inlineSource(): InlineSource = when (settings.inlineSource) {
        "ai-first" -> InlineSource.AI_FIRST
        "human-only" -> InlineSource.HUMAN_ONLY
        "ai-only" -> InlineSource.AI_ONLY
        else -> InlineSource.HUMAN_FIRST
    }

    fun iconsEnabled(): Boolean = settings.showIcons

    fun previewsEnabled(): Boolean = settings.showPreviews

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
        val root = project.basePath
            ?.replace('\\', '/')
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return null
        if (settings.monorepoMode == "rootOnly") return notesIn(root)
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

    private fun createNotesFile(packageDirectory: VirtualFile): VirtualFile {
        val notesDirectory = packageDirectory.findChild(NotesCore.NOTES_DIRECTORY)
            ?: packageDirectory.createChildDirectory(this, NotesCore.NOTES_DIRECTORY)
        return notesDirectory.findChild(NotesCore.NOTES_FILE_NAME)
            ?: notesDirectory.createChildData(this, NotesCore.NOTES_FILE_NAME)
    }

    private fun ensureAgentRules() {
        val root = project.basePath
            ?.replace('\\', '/')
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return
        val notesDirectory = root.findChild(NotesCore.NOTES_DIRECTORY)
            ?: root.createChildDirectory(this, NotesCore.NOTES_DIRECTORY)
        if (notesDirectory.findChild("AGENT-RULES.md") != null) return
        val rules = javaClass.getResourceAsStream("/pacmon/AGENT-RULES.md")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Bundled AGENT-RULES.md is missing.")
        val file = notesDirectory.createChildData(this, "AGENT-RULES.md")
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: error("Could not create AGENT-RULES.md.")
        document.setText(rules.replace("\r\n", "\n"))
        FileDocumentManager.getInstance().saveDocument(document)
    }

    private fun notifyNotesChanged(path: String) {
        DaemonCodeAnalyzer.getInstance(project).restart()
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
