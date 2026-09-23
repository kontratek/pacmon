import * as vscode from 'vscode';
import { AgentBlockLenses } from './agentBlockLenses';
import { setExtensionRoot } from './agentRules';
import { fixAgentNotes, registerFixProviders } from './codeActions';
import { NoteComments } from './comments';
import { NOTES_GLOB, isManifest, isNotesFile } from './config';
import { DecorationController } from './decorations';
import { NotesDiagnostics } from './diagnostics';
import { registerHover } from './hover';
import { logError, logInfo } from './log';
import { NoteButtons } from './noteButtons';
import { NotePanel } from './notePanel';
import { OrphanMarkers } from './orphanMarkers';
import { ProblemDecorations } from './problemDecorations';
import { SettingsView, openSettings, resetView } from './settingsView';
import { clearResolverCache } from './resolveNotesFile';
import { Store } from './state';
import { StatusBarController } from './statusBar';
import { addOrEditNote } from './commands/addOrEditNote';
import { setupAiInstructions } from './commands/aiSetup';
import { showCoverage } from './commands/coverage';
import { normalizeNotesFile, openManifest, openNotesFile, toggleDecorations } from './commands/simple';

export function activate(context: vscode.ExtensionContext): void {
  setExtensionRoot(context.extensionUri);
  // activate() does no IO: providers are registered, state is built lazily
  // when a relevant editor first appears.
  logInfo(`Pacmon ${context.extension.packageJSON.version as string} activated (${vscode.env.appName})`);
  const store = new Store();
  const rememberActiveManifest = (): void => {
    const uri = vscode.window.activeTextEditor?.document.uri;
    if (uri) store.rememberManifest(uri);
  };
  rememberActiveManifest();
  context.subscriptions.push(store);
  context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(rememberActiveManifest));
  const statusBar = new StatusBarController(store);
  context.subscriptions.push(statusBar);
  const noteComments = new NoteComments(store);
  context.subscriptions.push(noteComments);
  const notePanel = new NotePanel(store);
  context.subscriptions.push(notePanel);
  context.subscriptions.push(new NoteButtons(store));
  context.subscriptions.push(new SettingsView(store));

  context.subscriptions.push(registerHover(store));

  const decorations = new DecorationController(store);
  context.subscriptions.push(decorations);

  context.subscriptions.push(new NotesDiagnostics(store));
  context.subscriptions.push(...registerFixProviders());
  // Problems in the notes file are made hard to miss: the message at the end
  // of the line, a lens above the block, a count in the status bar.
  context.subscriptions.push(new ProblemDecorations());
  context.subscriptions.push(new AgentBlockLenses(store));
  // Sections whose package is not in package.json: a marker, not a problem.
  context.subscriptions.push(new OrphanMarkers(store));

  // Invalidate caches when relevant documents change (debounced inside Store).
  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument((e) => {
      if (isManifest(e.document.uri) || isNotesFile(e.document.uri)) {
        store.invalidate(e.document.uri);
      }
    }),
  );

  // Watch for external changes (git pull, agents editing files on disk).
  const registerWatcher = (glob: string): void => {
    const w = vscode.workspace.createFileSystemWatcher(glob);
    const onAny = (uri: vscode.Uri): void => {
      clearResolverCache();
      store.invalidate(uri);
    };
    w.onDidCreate(onAny);
    w.onDidChange(onAny);
    w.onDidDelete(onAny);
    context.subscriptions.push(w);
  };
  registerWatcher('**/package.json');
  registerWatcher('**/Cargo.toml');
  registerWatcher('**/pom.xml');
  registerWatcher('**/build.gradle.kts');
  registerWatcher('**/build.gradle');
  registerWatcher(NOTES_GLOB);

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('pacmon')) {
        clearResolverCache();
        store.invalidateAll();
      }
    }),
  );

  // Every command is wrapped: a failing command must never die silently
  // (errors land in Output → Pacmon plus a brief status bar note).
  const command = (id: string, fn: (...args: unknown[]) => Promise<void> | void): vscode.Disposable =>
    vscode.commands.registerCommand(id, async (...args: unknown[]) => {
      try {
        await fn(...args);
      } catch (e) {
        logError(`command.${id}`, e);
        vscode.window.setStatusBarMessage('Pacmon: command failed — see Output → Pacmon', 5000);
      }
    });

  context.subscriptions.push(
    command('pacmon.addOrEditNote', (nameArg, bodyArg) => {
      // Editor context menus invoke commands with a Uri argument — only accept
      // real strings as the package name / body.
      const name = typeof nameArg === 'string' ? nameArg : undefined;
      const body = typeof bodyArg === 'string' ? bodyArg : undefined;
      return addOrEditNote(store, notePanel, name, body);
    }),
    command('pacmon.resetView', () => resetView()),
    command('pacmon.openSettings', () => openSettings(context.extension.id)),
    command('pacmon.openNotesFile', () => openNotesFile(store)),
    command('pacmon.openManifest', () => openManifest(store)),
    command('pacmon.openPackageJson', () => openManifest(store)),
    command('pacmon.normalizeNotesFile', () => normalizeNotesFile(store)),
    command('pacmon.showCoverage', () => showCoverage(store, notePanel)),
    command('pacmon.setupAiInstructions', (targets) =>
      // Programmatic callers (tests, scripts) may name the files to write.
      setupAiInstructions(
        store,
        Array.isArray(targets) ? targets.filter((t): t is string => typeof t === 'string') : undefined,
      ),
    ),
    command('pacmon.toggleDecorations', () =>
      toggleDecorations(context, () => {
        decorations.refreshVisible();
        statusBar.refreshNow();
      }),
    ),
    command('pacmon.saveNoteComment', (reply) => noteComments.saveFromReply(reply as vscode.CommentReply)),
    // Behind the code lens above an agent block; not in the palette.
    command('pacmon.fixAgentNotes', (uri) => fixAgentNotes(uri)),
  );
}

export function deactivate(): void {
  // Nothing to clean up beyond context.subscriptions.
}
