import * as vscode from 'vscode';
import { normalizeText } from '../../core/serialize';
import { isNotesFile, isPackageJson, notesFileLabel, toggleDecorationsState } from '../config';
import { defaultPackageJson, notesUriIn, resolveNotesFileFor } from '../resolveNotesFile';
import type { Store } from '../state';
import { S } from '../strings';

async function findNotesUri(store: Store): Promise<vscode.Uri | undefined> {
  const editor = vscode.window.activeTextEditor;
  if (editor && isNotesFile(editor.document.uri)) return editor.document.uri;
  if (editor && isPackageJson(editor.document.uri)) {
    const resolved = await resolveNotesFileFor(editor.document.uri);
    if (resolved) return resolved;
  }
  const pkg = await defaultPackageJson(store);
  if (pkg) {
    const resolved = await resolveNotesFileFor(pkg);
    if (resolved) return resolved;
  }
  const folder = vscode.workspace.workspaceFolders?.[0];
  if (folder) {
    const candidate = notesUriIn(folder.uri);
    if ((await store.getText(candidate)) !== undefined) return candidate;
  }
  return undefined;
}

export async function openNotesFile(store: Store): Promise<void> {
  const uri = await findNotesUri(store);
  if (!uri) {
    void vscode.window.showInformationMessage(S.noNotesFile(notesFileLabel()));
    return;
  }
  const doc = await vscode.workspace.openTextDocument(uri);
  await vscode.window.showTextDocument(doc);
}

/** Open the package.json the notes belong to — the mirror of openNotesFile. */
export async function openPackageJson(store: Store): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (editor && isPackageJson(editor.document.uri)) {
    await vscode.window.showTextDocument(editor.document);
    return;
  }
  const pkg = await defaultPackageJson(store);
  if (!pkg) {
    void vscode.window.showInformationMessage(
      vscode.workspace.workspaceFolders?.length ? S.noPackageJson : S.noWorkspace,
    );
    return;
  }
  await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(pkg));
}

export async function normalizeNotesFile(store: Store): Promise<void> {
  const uri = await findNotesUri(store);
  if (!uri) {
    void vscode.window.showInformationMessage(S.noNotesFile(notesFileLabel()));
    return;
  }
  const doc = await vscode.workspace.openTextDocument(uri);
  const original = doc.getText();
  const canonical = normalizeText(original);
  if (canonical === original) {
    vscode.window.setStatusBarMessage(S.alreadyCanonical(notesFileLabel()), 3000);
    return;
  }
  const edit = new vscode.WorkspaceEdit();
  const fullRange = new vscode.Range(new vscode.Position(0, 0), doc.lineAt(doc.lineCount - 1).range.end);
  edit.replace(uri, fullRange, canonical);
  await vscode.workspace.applyEdit(edit);
  await vscode.window.showTextDocument(doc, { preserveFocus: true, preview: false });
  vscode.window.setStatusBarMessage(S.formatted(notesFileLabel()), 3000);
}

export async function toggleDecorations(context: vscode.ExtensionContext, refresh: () => void): Promise<void> {
  const on = await toggleDecorationsState(context);
  refresh();
  vscode.window.setStatusBarMessage(on ? S.decorationsOn : S.decorationsOff, 3000);
}
