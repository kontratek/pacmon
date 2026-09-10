import * as vscode from 'vscode';
import { orderedLayers, sectionLayers } from '../core/layers';
import { depAtOffset } from '../core/packageJson';
import { findSection } from '../core/parseNotes';
import { inlineSource, notesFileLabel } from './config';
import { logError } from './log';
import { resolveNotesFileFor } from './resolveNotesFile';
import type { Store } from './state';
import { S } from './strings';

export function registerHover(store: Store): vscode.Disposable {
  const selector: vscode.DocumentSelector = [
    { language: 'json', pattern: '**/package.json' },
    { language: 'jsonc', pattern: '**/package.json' },
  ];

  return vscode.languages.registerHoverProvider(selector, {
    async provideHover(doc, position) {
      try {
        return await provide(doc, position);
      } catch (e) {
        logError('hover.provideHover', e);
        return undefined;
      }
    },
  });

  async function provide(doc: vscode.TextDocument, position: vscode.Position): Promise<vscode.Hover | undefined> {
    {
      const deps = store.depsForDocument(doc);
      if (deps.length === 0) return undefined;
      const offset = doc.offsetAt(position);
      const dep = depAtOffset(deps, offset);
      if (!dep) return undefined;

      const notesUri = await resolveNotesFileFor(doc.uri);
      if (!notesUri) return undefined;
      const notes = await store.getNotes(notesUri);
      if (!notes) return undefined;
      const section = findSection(notes, dep.name);
      if (!section) return undefined; // undocumented lines stay silent

      // Both layers, in the order the setting asks for, a rule between them.
      const parts = orderedLayers(sectionLayers(notes, section), inlineSource());
      if (parts.length === 0) return undefined; // a heading with nothing under it is no note
      const md = new vscode.MarkdownString(undefined, true);
      md.isTrusted = { enabledCommands: ['pacmon.addOrEditNote', 'pacmon.openNotesFile'] };
      md.appendMarkdown(`**${dep.name}** ${S.hoverSourceSuffix(notesFileLabel())}\n\n`);
      if (parts.length > 0) md.appendMarkdown(`${parts.map((p) => p.text).join('\n\n---\n\n')}\n\n`);
      const arg = encodeURIComponent(JSON.stringify([dep.name]));
      md.appendMarkdown(
        `---\n\n[${S.hoverEditNote}](command:pacmon.addOrEditNote?${arg}) · [${S.hoverOpenFile(notesFileLabel())}](command:pacmon.openNotesFile)`,
      );

      const start = doc.positionAt(dep.keyOffset);
      const end = doc.positionAt(dep.keyOffset + dep.keyLength);
      return new vscode.Hover(md, new vscode.Range(start, end));
    }
  }
}
