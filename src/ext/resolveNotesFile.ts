import * as vscode from 'vscode';
import { AGENT_RULES_REL_PATH, NOTES_REL_PATH } from '../core/template';
import { monorepoMode } from './config';

const existsCache = new Map<string, boolean>();

export function clearResolverCache(): void {
  existsCache.clear();
}

async function exists(uri: vscode.Uri): Promise<boolean> {
  const key = uri.toString();
  const hit = existsCache.get(key);
  if (hit !== undefined) return hit;
  let ok = false;
  try {
    await vscode.workspace.fs.stat(uri);
    ok = true;
  } catch {
    ok = false;
  }
  existsCache.set(key, ok);
  return ok;
}

function parentDir(uri: vscode.Uri): vscode.Uri {
  return vscode.Uri.joinPath(uri, '..');
}

/** The notes file that belongs to a directory: `<dir>/.pacmon/DEPENDENCY-NOTES.md`. */
export function notesUriIn(dir: vscode.Uri): vscode.Uri {
  return vscode.Uri.joinPath(dir, ...NOTES_REL_PATH.split('/'));
}

/** The package.json a notes file describes — `.pacmon/` sits right next to it. */
export function packageJsonFor(notesUri: vscode.Uri): vscode.Uri {
  return vscode.Uri.joinPath(notesUri, '..', '..', 'package.json');
}

/** The one agent-rules file of a workspace: `<root>/.pacmon/AGENT-RULES.md`. */
export function agentRulesUriFor(anyUri: vscode.Uri): vscode.Uri | undefined {
  const folder = vscode.workspace.getWorkspaceFolder(anyUri) ?? vscode.workspace.workspaceFolders?.[0];
  return folder ? vscode.Uri.joinPath(folder.uri, ...AGENT_RULES_REL_PATH.split('/')) : undefined;
}

/**
 * The notes file that applies to a given package.json: the nearest one walking
 * up from the package.json's directory to the workspace folder root ("nearest"),
 * or only the workspace root file ("rootOnly").
 */
export async function resolveNotesFileFor(pkgUri: vscode.Uri): Promise<vscode.Uri | undefined> {
  const folder = vscode.workspace.getWorkspaceFolder(pkgUri);
  if (!folder) return undefined;

  if (monorepoMode() === 'rootOnly') {
    const candidate = notesUriIn(folder.uri);
    return (await exists(candidate)) ? candidate : undefined;
  }

  let dir = parentDir(pkgUri);
  const rootPath = folder.uri.path.replace(/\/+$/, '');
  for (let i = 0; i < 64; i++) {
    const candidate = notesUriIn(dir);
    if (await exists(candidate)) return candidate;
    const dirPath = dir.path.replace(/\/+$/, '');
    if (dirPath === rootPath || dirPath.length <= rootPath.length) break;
    dir = parentDir(dir);
  }
  return undefined;
}

/** Where a new notes file should be created for a package.json (its own directory). */
export function creationTargetFor(pkgUri: vscode.Uri): vscode.Uri {
  return notesUriIn(parentDir(pkgUri));
}

/** Best-effort default package.json for commands invoked without context. */
export async function defaultPackageJson(store: {
  getText(uri: vscode.Uri): Promise<string | undefined>;
}): Promise<vscode.Uri | undefined> {
  const folder = vscode.workspace.workspaceFolders?.[0];
  if (!folder) return undefined;
  const candidate = vscode.Uri.joinPath(folder.uri, 'package.json');
  return (await store.getText(candidate)) !== undefined ? candidate : undefined;
}
