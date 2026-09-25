import * as vscode from 'vscode';
import { analyze } from '../../core/analyze';
import { manifestAdapterForPath } from '../../core/manifest';
import { normalizeNameForEcosystem } from '../../core/match';
import { notePreview, sectionLayers } from '../../core/layers';
import { uniqueNugetDependencies } from '../../core/nuget-manifest';
import { inlineSource, isManifest } from '../config';
import type { NotePanel } from '../notePanel';
import { creationTargetFor, defaultManifest, dependenciesForNotes, resolveNotesFileFor } from '../resolveNotesFile';
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
    editor && isManifest(editor.document.uri) ? editor.document.uri : await defaultManifest(store);
  if (!pkgUri) {
    void vscode.window.showInformationMessage(S.noManifest);
    return;
  }

  const notesUri = await resolveNotesFileFor(pkgUri);
  const ownDependencies = await store.getDeps(pkgUri);
  const isNuget = manifestAdapterForPath(pkgUri.path)?.kind === 'nuget';
  const sharedDependencies = isNuget
    ? await dependenciesForNotes(store, notesUri ?? await creationTargetFor(pkgUri))
    : [];
  const deps = isNuget
    ? uniqueNugetDependencies([...sharedDependencies, ...ownDependencies])
    : ownDependencies;
  const notes = notesUri ? await store.getNotes(notesUri) : undefined;
  const { documented, undocumented, byDep } = analyze(deps, notes);

  const source = inlineSource();
  const items: CoverageItem[] = [];
  items.push({ label: S.coverageDocumented, kind: vscode.QuickPickItemKind.Separator });
  for (const d of documented) {
    const section = byDep.get(normalizeNameForEcosystem(d.noteKey, notes?.frontmatter?.ecosystem));
    const detail = notes && section ? notePreview(sectionLayers(notes, section), source, 96) : '';
    items.push({ label: `$(pass) ${d.displayName}`, description: d.scope, detail, dep: d.noteKey, documented: true });
  }
  items.push({ label: S.coverageUndocumented, kind: vscode.QuickPickItemKind.Separator });
  for (const d of undocumented) {
    items.push({ label: `$(circle-large-outline) ${d.displayName}`, description: d.scope, dep: d.noteKey, documented: false });
  }

  const picked = await vscode.window.showQuickPick(items, {
    placeHolder: S.coveragePlaceholder(documented.length, deps.length),
    matchOnDescription: true,
  });
  if (!picked?.dep) return;
  await addOrEditNote(store, panel, picked.dep);
}
