import * as vscode from 'vscode';
import { orphanMarkers } from '../core/orphans';
import { parseNotes } from '../core/parseNotes';
import { isNotesFile } from './config';
import { logError } from './log';
import { dependenciesForNotes } from './resolveNotesFile';
import type { Store } from './state';
import { S } from './strings';

/**
 * A yellow glyph before the heading of every section whose package is not in
 * package.json. A marker, not a diagnostic: nothing in the Problems panel,
 * nothing to fix — a removed package's notes are worth keeping, and a marker
 * does not nag. The hover says what is known: a near-miss of a real
 * dependency ("did you mean …"), or that the section is marked removed.
 */
export class OrphanMarkers implements vscode.Disposable {
  private readonly type = vscode.window.createTextEditorDecorationType({
    before: {
      contentText: S.orphanGlyph,
      color: new vscode.ThemeColor('editorWarning.foreground'),
      fontWeight: 'bold',
      margin: '0 0.45em 0 0',
    },
    rangeBehavior: vscode.DecorationRangeBehavior.ClosedClosed,
  });
  private readonly disposables: vscode.Disposable[] = [];

  constructor(private readonly store: Store) {
    this.disposables.push(
      store.onDidChange(() => this.refreshVisible()),
      vscode.window.onDidChangeVisibleTextEditors(() => this.refreshVisible()),
      vscode.window.onDidChangeActiveTextEditor(() => this.refreshVisible()),
    );
    this.refreshVisible();
  }

  dispose(): void {
    this.type.dispose();
    for (const d of this.disposables) d.dispose();
  }

  private refreshVisible(): void {
    for (const editor of vscode.window.visibleTextEditors) {
      void this.decorate(editor).catch((e: unknown) => logError('orphanMarkers.refresh', e));
    }
  }

  private async decorate(editor: vscode.TextEditor): Promise<void> {
    const doc = editor.document;
    if (!isNotesFile(doc.uri)) return;
    const deps = await dependenciesForNotes(this.store, doc.uri);
    // No package.json to compare against: nothing can be called an orphan.
    if (deps.length === 0) {
      editor.setDecorations(this.type, []);
      return;
    }
    const options: vscode.DecorationOptions[] = [];
    for (const m of orphanMarkers(deps, parseNotes(doc.getText()))) {
      if (m.line >= doc.lineCount) continue;
      const text = m.removed
        ? S.orphanHoverRemoved(m.name)
        : m.guess === undefined
          ? S.orphanHover(m.name)
          : S.orphanHoverTypo(m.name, m.guess);
      options.push({ range: new vscode.Range(m.line, 0, m.line, 0), hoverMessage: new vscode.MarkdownString(text) });
    }
    editor.setDecorations(this.type, options);
  }
}
