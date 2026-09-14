import * as vscode from 'vscode';
import { INLINE_SOURCES, type InlineSource } from '../core/layers';
import { NOTES_DIR, NOTES_FILE_NAME, NOTES_REL_PATH } from '../core/template';
import { DEFAULT_NOTE_BUTTONS, type NoteButton } from './noteButtonIds';

export { AGENT_RULES_REL_PATH, NOTES_REL_PATH } from '../core/template';

/** Where the notes live, wherever a package.json sits. The layout is part of
 *  the format, so it is not a setting. */
export const NOTES_GLOB = `**/${NOTES_REL_PATH}`;

/** How messages name the notes file. */
export function notesFileLabel(): string {
  return NOTES_REL_PATH;
}

/** Which layer of a note feeds the end-of-line preview and leads the hover. */
export function inlineSource(): InlineSource {
  const v = vscode.workspace.getConfiguration('pacmon').get<string>('inlineSource', 'human-first');
  return (INLINE_SOURCES as readonly string[]).includes(v) ? (v as InlineSource) : 'human-first';
}

export type DecorationStyle = 'preview' | 'badge' | 'off';

export function decorationsSetting(): DecorationStyle {
  return vscode.workspace.getConfiguration('pacmon').get<DecorationStyle>('decorations', 'preview');
}

export function monorepoMode(): 'nearest' | 'rootOnly' {
  return vscode.workspace.getConfiguration('pacmon').get<'nearest' | 'rootOnly'>('monorepo', 'nearest');
}

export type NoteEntryMode = 'panel' | 'peek' | 'input' | 'inputBeside' | 'comments';

export function noteEntryMode(): NoteEntryMode {
  return vscode.workspace.getConfiguration('pacmon').get<NoteEntryMode>('noteEntry', 'panel');
}

/** Clickable ways into a note from package.json (see noteButtons.ts). */
export { ALL_NOTE_BUTTONS, type NoteButton } from './noteButtonIds';

export function noteButtonEnabled(which: NoteButton): boolean {
  const configured = vscode.workspace
    .getConfiguration('pacmon')
    .get<NoteButton[]>('noteButtons', [...DEFAULT_NOTE_BUTTONS]);
  return configured.includes(which);
}

/** Remembers the style to come back to when markers are toggled off again. */
const LAST_STYLE_KEY = 'pacmon.lastDecorationStyle';

/**
 * The setting is the only source of truth. It used to be outranked by a hidden
 * per-workspace flag, which meant choosing "off" could do nothing at all.
 */
export function decorationsEnabled(): boolean {
  return decorationsSetting() !== 'off';
}

/** "Toggle Note Markers": flips the setting for THIS workspace, so the choice
 *  stays visible in settings instead of hiding in workspace state. */
export async function toggleDecorationsState(context: vscode.ExtensionContext): Promise<boolean> {
  const cfg = vscode.workspace.getConfiguration('pacmon');
  const style = decorationsSetting();
  if (style !== 'off') {
    await context.workspaceState.update(LAST_STYLE_KEY, style);
    await cfg.update('decorations', 'off', vscode.ConfigurationTarget.Workspace);
    return false;
  }
  const restore = context.workspaceState.get<DecorationStyle>(LAST_STYLE_KEY) ?? 'preview';
  await cfg.update('decorations', restore, vscode.ConfigurationTarget.Workspace);
  return true;
}

export function uriBasename(uri: vscode.Uri): string {
  const parts = uri.path.split('/');
  return parts[parts.length - 1] ?? '';
}

export function isPackageJson(uri: vscode.Uri): boolean {
  return uriBasename(uri) === 'package.json';
}

/** `DEPENDENCY-NOTES.md` inside a `.pacmon/` directory — nothing else counts, not
 *  even a `DEPENDENCY-NOTES.md` at the root. */
export function isNotesFile(uri: vscode.Uri): boolean {
  const parts = uri.path.split('/');
  return parts[parts.length - 1] === NOTES_FILE_NAME && parts[parts.length - 2] === NOTES_DIR;
}
