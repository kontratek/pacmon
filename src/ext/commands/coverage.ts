import * as vscode from 'vscode';
import { analyze } from '../../core/analyze';
import { normalizeName } from '../../core/match';
import { notePreview, sectionLayers } from '../../core/layers';
import { inlineSource, isPackageJson } from '../config';
import type { NotePanel } from '../notePanel';
import { defaultPackageJson, resolveNotesFileFor } from '../resolveNotesFile';
import type { Store } from '../state';
import { S } from '../strings';
import { addOrEditNote } from './addOrEditNote';

interface CoverageItem extends vscode.QuickPickItem {
  dep?: string;
  documented?: boolean;
}

export async function showCoverage(store: Store, panel: NotePanel): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  const pkgUri =
    editor && isPackageJson(editor.document.uri) ? editor.document.uri : await defaultPackageJson(store);
  if (!pkgUri) {
    void vscode.window.showInformationMessage(S.noPackageJson);
    return;
  }

  const deps = await store.getDeps(pkgUri);
  const notesUri = await resolveNotesFileFor(pkgUri);
  const notes = notesUri ? await store.getNotes(notesUri) : undefined;
  const { documented, undocumented, byDep } = analyze(deps, notes);

  const source = inlineSource();
  const items: CoverageItem[] = [];
  items.push({ label: S.coverageDocumented, kind: vscode.QuickPickItemKind.Separator });
  for (const d of documented) {
    const section = byDep.get(normalizeName(d.name));
    const detail = notes && section ? notePreview(sectionLayers(notes, section), source, 96) : '';
    items.push({ label: `$(pass) ${d.name}`, description: d.section, detail, dep: d.name, documented: true });
  }
  items.push({ label: S.coverageUndocumented, kind: vscode.QuickPickItemKind.Separator });
  for (const d of undocumented) {
    items.push({ label: `$(circle-large-outline) ${d.name}`, description: d.section, dep: d.name, documented: false });
  }

  const picked = await vscode.window.showQuickPick(items, {
    placeHolder: S.coveragePlaceholder(documented.length, deps.length),
    matchOnDescription: true,
  });
  if (!picked?.dep) return;
  await addOrEditNote(store, panel, picked.dep);
}
