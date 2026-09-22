import * as vscode from 'vscode';
import { MANIFEST_ADAPTERS, manifestAdapterForFileName, manifestAdapterForKind } from '../core/manifest';
import type { DependencyEntry, ManifestKind } from '../core/model';
import { AGENT_RULES_REL_PATH } from '../core/template';
import { monorepoMode, uriBasename } from './config';

const existsCache = new Map<string, boolean>();
const manifestsForNotesCache = new Map<string, Promise<vscode.Uri[]>>();

export function clearResolverCache(): void {
  existsCache.clear();
  manifestsForNotesCache.clear();
}

async function exists(uri: vscode.Uri): Promise<boolean> {
  const key = uri.toString();
  const hit = existsCache.get(key);
  if (hit !== undefined) return hit;
  try {
    await vscode.workspace.fs.stat(uri);
    existsCache.set(key, true);
    return true;
  } catch {
    existsCache.set(key, false);
    return false;
  }
}

function parentDir(uri: vscode.Uri): vscode.Uri {
  return vscode.Uri.joinPath(uri, '..');
}

export function notesUriIn(dir: vscode.Uri, kind: ManifestKind = 'npm'): vscode.Uri {
  return vscode.Uri.joinPath(dir, ...manifestAdapterForKind(kind).notesRelativePath.split('/'));
}

export function notesKind(uri: vscode.Uri): ManifestKind | undefined {
  const normalized = uri.path.replace(/\\/g, '/');
  if (normalized.endsWith('/.pacmon/DEPENDENCY-NOTES.md')) return 'npm';
  if (normalized.endsWith('/.pacmon/cargo/DEPENDENCY-NOTES.md')) return 'cargo';
  if (normalized.endsWith('/.pacmon/maven/DEPENDENCY-NOTES.md')) return 'maven';
  return undefined;
}

/** The manifest beside the directory that owns a notes file. */
export function manifestForNotes(notesUri: vscode.Uri): vscode.Uri | undefined {
  const kind = notesKind(notesUri);
  if (!kind) return undefined;
  const levels = kind === 'npm' ? ['..', '..'] : ['..', '..', '..'];
  return vscode.Uri.joinPath(notesUri, ...levels, manifestAdapterForKind(kind).fileName);
}

/** Backward-compatible npm-only name. */
export function packageJsonFor(notesUri: vscode.Uri): vscode.Uri {
  return manifestForNotes(notesUri) ?? vscode.Uri.joinPath(notesUri, '..', '..', 'package.json');
}

export function agentRulesUriFor(anyUri: vscode.Uri): vscode.Uri | undefined {
  const folder = vscode.workspace.getWorkspaceFolder(anyUri) ?? vscode.workspace.workspaceFolders?.[0];
  return folder ? vscode.Uri.joinPath(folder.uri, ...AGENT_RULES_REL_PATH.split('/')) : undefined;
}

export async function resolveNotesFileFor(manifestUri: vscode.Uri): Promise<vscode.Uri | undefined> {
  const adapter = manifestAdapterForFileName(uriBasename(manifestUri));
  const folder = vscode.workspace.getWorkspaceFolder(manifestUri);
  if (!adapter || !folder) return undefined;
  if (monorepoMode() === 'rootOnly') {
    const candidate = notesUriIn(folder.uri, adapter.kind);
    return (await exists(candidate)) ? candidate : undefined;
  }
  let dir = parentDir(manifestUri);
  const rootPath = folder.uri.path.replace(/\/+$/, '');
  for (let i = 0; i < 64; i++) {
    const candidate = notesUriIn(dir, adapter.kind);
    if (await exists(candidate)) return candidate;
    const dirPath = dir.path.replace(/\/+$/, '');
    if (dirPath === rootPath || dirPath.length <= rootPath.length) break;
    dir = parentDir(dir);
  }
  return undefined;
}

export function creationTargetFor(manifestUri: vscode.Uri): vscode.Uri {
  const adapter = manifestAdapterForFileName(uriBasename(manifestUri));
  return notesUriIn(parentDir(manifestUri), adapter?.kind ?? 'npm');
}

export async function defaultManifest(store: {
  getText(uri: vscode.Uri): Promise<string | undefined>;
  getLastManifest?(): vscode.Uri | undefined;
}): Promise<vscode.Uri | undefined> {
  const last = store.getLastManifest?.();
  if (last && (await store.getText(last)) !== undefined) return last;
  const folder = vscode.workspace.workspaceFolders?.[0];
  if (!folder) return undefined;
  for (const adapter of MANIFEST_ADAPTERS) {
    const candidate = vscode.Uri.joinPath(folder.uri, adapter.fileName);
    if ((await store.getText(candidate)) !== undefined) return candidate;
  }
  return undefined;
}

/** Backward-compatible name; now returns the default supported manifest. */
export const defaultPackageJson = defaultManifest;

async function manifestsForNotes(notesUri: vscode.Uri): Promise<vscode.Uri[]> {
  const key = notesUri.toString();
  const cached = manifestsForNotesCache.get(key);
  if (cached) return cached;
  const pending = (async () => {
    const kind = notesKind(notesUri);
    const folder = vscode.workspace.getWorkspaceFolder(notesUri);
    if (!kind || !folder) return [];
    const adapter = manifestAdapterForKind(kind);
    const found = await vscode.workspace.findFiles(
      new vscode.RelativePattern(folder, `**/${adapter.fileName}`),
      '**/{node_modules,target,.git}/**',
    );
    const matching: vscode.Uri[] = [];
    for (const uri of found) {
      const resolved = await resolveNotesFileFor(uri);
      if (resolved?.toString() === key) matching.push(uri);
    }
    const sibling = manifestForNotes(notesUri);
    if (matching.length === 0 && sibling && (await exists(sibling))) matching.push(sibling);
    return matching;
  })();
  manifestsForNotesCache.set(key, pending);
  return pending;
}

export async function dependenciesForNotes(
  store: { getDeps(uri: vscode.Uri): Promise<DependencyEntry[]> },
  notesUri: vscode.Uri,
): Promise<DependencyEntry[]> {
  const groups = await Promise.all((await manifestsForNotes(notesUri)).map((uri) => store.getDeps(uri)));
  return groups.flat();
}
