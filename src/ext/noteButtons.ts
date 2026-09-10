import * as vscode from 'vscode';
import type { DepEntry } from '../core/model';
import { findSection } from '../core/parseNotes';
import { isPackageJson, type NoteButton, noteButtonEnabled } from './config';
import { logError } from './log';
import { resolveNotesFileFor } from './resolveNotesFile';
import type { Store } from './state';
import { S } from './strings';

/**
 * Ways to reach a dependency's note straight from package.json, chosen with
 * `pacmon.noteButtons` (and from the Pacmon view in the activity bar).
 *
 * ON BY DEFAULT — the pair that won the 2026-09-04 comparison:
 *
 *   iconLeft  a single glyph just before the package name (✎ documented,
 *             + undocumented), with the words in the hover. A DECORATION, not
 *             an inlay hint: only decorations expose `cursor`, so only they can
 *             turn the mouse into a hand, and the styling is ours. Like an
 *             inlay hint it is injected into the rendered line, so each
 *             dependency key is pushed right while `name`/`version` stay put —
 *             the indent column survives, the keys no longer align, and one
 *             glyph keeps that to a single column.
 *             Decorations take no click event, so the plain click is read off
 *             the caret: a click still moves it, and a mouse-kind caret landing
 *             at or before the key opens the note. The target is exactly the
 *             strip the glyph occupies; clicking the name, the version or
 *             anywhere else on the line just places the caret, and keyboard or
 *             programmatic moves are ignored. The hover carries a command link
 *             as a keyboard-reachable fallback.
 *   link      the package NAME itself, opened with Ctrl+click. Adds no pixels
 *             to the file. VS Code renders command targets as editor links and
 *             fixes those at Ctrl+click; extensions cannot change that gesture.
 *
 * OFF BY DEFAULT — lost the comparison, kept because the trade is a matter of
 * taste and some people will want them:
 *
 *   codelens   a clickable line above each dependency. Unmissable, and it
 *              roughly doubles the apparent height of package.json.
 *   inlayHint  the same chip as iconLeft but at the END of the line, with the
 *              words spelled out. Competes with the note preview for that
 *              space, and being an inlay hint it needs Ctrl+click.
 *   lightbulb  a code action, so the bulb shows only on the line the cursor is
 *              on. Marks nothing at rest — the quietest option — but costs two
 *              clicks (bulb, then the action).
 *
 * One idea was built and dropped outright: a plain click ANYWHERE on the line,
 * retargeting an open panel. It took clicks the user meant for the text.
 * The Comments API gutter "+" was never a candidate: its button cannot be
 * rebound to a command, so it always opens a comment thread rather than the
 * note editor. It remains available as `pacmon.noteEntry: "comments"`.
 *
 * NOTE that everything except `lightbulb` marks dependencies with no note yet —
 * the one thing Pacmon otherwise never does. That is why these are switchable.
 */

const SELECTOR: vscode.DocumentSelector = [
  { language: 'json', pattern: '**/package.json' },
  { language: 'jsonc', pattern: '**/package.json' },
];

/** Every affordance fires the same command, so they differ only in shape. */
function noteCommand(name: string, documented: boolean): vscode.Command {
  return {
    command: 'pacmon.addOrEditNote',
    title: documented ? S.buttonEdit : S.buttonAdd,
    tooltip: S.buttonTooltip(name),
    arguments: [name],
  };
}

export class NoteButtons implements vscode.Disposable {
  private readonly refresh = new vscode.EventEmitter<void>();
  private readonly disposables: vscode.Disposable[] = [];
  /**
   * The link provider is registered and dropped by hand. Unlike code lenses
   * and inlay hints, DocumentLinkProvider has NO change event and VS Code
   * caches what it returns, so returning [] does not make a switched-off link
   * disappear — only replacing the registration does.
   */
  private linkReg: vscode.Disposable | undefined;
  /**
   * The iconLeft glyph. `cursor` turns the mouse into a hand over the
   * decorated character; the `textDecoration` escape hatch carries the same
   * rule onto the injected glyph itself, which has no property of its own.
   */
  private readonly iconType = vscode.window.createTextEditorDecorationType({
    before: {
      color: new vscode.ThemeColor('textLink.foreground'),
      margin: '0 0.35em 0 0',
      textDecoration: 'none; cursor: pointer',
    },
    cursor: 'pointer',
    rangeBehavior: vscode.DecorationRangeBehavior.ClosedClosed,
  });

  constructor(private readonly store: Store) {
    this.disposables.push(
      this.refresh,
      // A note written elsewhere flips + to ✎ (and "Add note" to "Edit note");
      // a config change switches whole affordances on and off.
      store.onDidChange(() => this.onChanged()),
      vscode.window.onDidChangeTextEditorSelection((e) => void this.onIconClick(e)),
      vscode.window.onDidChangeActiveTextEditor(() => this.refreshIcons()),
      vscode.window.onDidChangeVisibleTextEditors(() => this.refreshIcons()),
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('pacmon.noteButtons')) this.onChanged();
      }),

      vscode.languages.registerCodeLensProvider(SELECTOR, {
        onDidChangeCodeLenses: this.refresh.event,
        provideCodeLenses: (doc) => this.guard('codelens', doc, (deps, has) =>
          deps.map((d) => {
            const line = doc.positionAt(d.keyOffset).line;
            return new vscode.CodeLens(new vscode.Range(line, 0, line, 0), noteCommand(d.name, has(d)));
          }),
        ),
      }),

      vscode.languages.registerInlayHintsProvider(SELECTOR, {
        onDidChangeInlayHints: this.refresh.event,
        provideInlayHints: (doc, range) => this.guard('inlayHint', doc, (deps, has) =>
          deps
            .filter((d) => range.contains(doc.positionAt(d.keyOffset)))
            .map((d) => {
              const line = doc.positionAt(d.keyOffset).line;
              const part = new vscode.InlayHintLabelPart(has(d) ? S.buttonEdit : S.buttonAdd);
              part.command = noteCommand(d.name, has(d));
              part.tooltip = S.buttonTooltip(d.name);
              const hint = new vscode.InlayHint(doc.lineAt(line).range.end, [part]);
              hint.paddingLeft = true;
              return hint;
            }),
        ),
      }),

      vscode.languages.registerCodeActionsProvider(
        SELECTOR,
        {
          provideCodeActions: (doc, range) => this.guard('lightbulb', doc, (deps, has) => {
            const lineStart = doc.offsetAt(new vscode.Position(range.start.line, 0));
            const lineEnd = doc.offsetAt(doc.lineAt(range.start.line).range.end);
            const dep = deps.find((d) => d.keyOffset >= lineStart && d.keyOffset <= lineEnd);
            if (!dep) return [];
            const action = new vscode.CodeAction(
              has(dep) ? S.buttonEdit : S.buttonAdd,
              vscode.CodeActionKind.QuickFix,
            );
            action.command = noteCommand(dep.name, has(dep));
            return [action];
          }),
        },
        { providedCodeActionKinds: [vscode.CodeActionKind.QuickFix] },
      ),

    );
    this.refreshIcons();
    this.syncLinks();
  }

  dispose(): void {
    this.linkReg?.dispose();
    this.iconType.dispose();
    for (const d of this.disposables) d.dispose();
    this.disposables.length = 0;
  }

  private onChanged(): void {
    this.refresh.fire();
    this.refreshIcons();
    this.syncLinks();
  }

  private syncLinks(): void {
    this.linkReg?.dispose();
    this.linkReg = undefined;
    if (!noteButtonEnabled('link')) return;
    this.linkReg = vscode.languages.registerDocumentLinkProvider(SELECTOR, {
      provideDocumentLinks: (doc) => this.guard('link', doc, (deps, has) =>
        deps.map((d) => {
          // Inside the quotes: the name text itself, nothing more.
          const start = doc.positionAt(d.keyOffset + 1);
          const end = doc.positionAt(d.keyOffset + d.keyLength - 1);
          const args = encodeURIComponent(JSON.stringify([d.name]));
          const link = new vscode.DocumentLink(
            new vscode.Range(start, end),
            vscode.Uri.parse(`command:pacmon.addOrEditNote?${args}`),
          );
          link.tooltip = has(d) ? S.buttonEdit : S.buttonAdd;
          return link;
        }),
      ),
    });
  }

  private refreshIcons(): void {
    for (const editor of vscode.window.visibleTextEditors) {
      void this.drawIcons(editor);
    }
  }

  /** Draw (or clear) the iconLeft glyph on one editor. */
  private async drawIcons(editor: vscode.TextEditor): Promise<void> {
    const doc = editor.document;
    if (!isPackageJson(doc.uri)) return;
    const options = await this.guard('iconLeft', doc, (deps, has) =>
      deps.map((d) => {
        const keyPos = doc.positionAt(d.keyOffset);
        const documented = has(d);
        const hover = new vscode.MarkdownString(undefined, true);
        hover.isTrusted = { enabledCommands: ['pacmon.addOrEditNote'] };
        const arg = encodeURIComponent(JSON.stringify([d.name]));
        hover.appendMarkdown(
          `[${documented ? S.buttonEdit : S.buttonAdd}](command:pacmon.addOrEditNote?${arg}) — ${d.name}`,
        );
        return {
          // One character wide: the opening quote of the key. The glyph is
          // rendered just before it, and the hand cursor covers both.
          range: new vscode.Range(keyPos, keyPos.translate(0, 1)),
          hoverMessage: hover,
          renderOptions: {
            before: { contentText: documented ? S.buttonIconEdit : S.buttonIconAdd },
          },
        } satisfies vscode.DecorationOptions;
      }),
    );
    editor.setDecorations(this.iconType, options);
  }

  /**
   * Plain click on the glyph. A decoration takes no click event, so this reads
   * the caret instead: a mouse click lands a caret, and one that lands at or
   * before the key of a dependency line is a click on the strip the glyph
   * occupies. Every other click on the line — the name, the version, the
   * trailing comma — is left alone, and keyboard and programmatic moves are
   * ignored outright.
   */
  private async onIconClick(e: vscode.TextEditorSelectionChangeEvent): Promise<void> {
    try {
      // No glyph drawn, no click target.
      if (!noteButtonEnabled('iconLeft')) return;
      if (e.kind !== vscode.TextEditorSelectionChangeKind.Mouse) return;
      if (e.selections.length !== 1) return;
      const sel = e.selections[0]!;
      if (!sel.isEmpty) return;
      const doc = e.textEditor.document;
      if (!isPackageJson(doc.uri)) return;

      const deps = this.store.depsForDocument(doc);
      const dep = deps.find((d) => doc.positionAt(d.keyOffset).line === sel.active.line);
      if (!dep) return;
      if (sel.active.character > doc.positionAt(dep.keyOffset).character) return;

      await vscode.commands.executeCommand('pacmon.addOrEditNote', dep.name);
    } catch (err) {
      logError('noteButtons.iconLeft.click', err);
    }
  }

  /**
   * Shared front door: honours the setting, resolves the deps and the notes
   * file once, and never lets a provider throw into the editor.
   */
  private async guard<T>(
    which: NoteButton,
    doc: vscode.TextDocument,
    build: (deps: readonly DepEntry[], has: (d: DepEntry) => boolean) => T[],
  ): Promise<T[]> {
    try {
      if (!noteButtonEnabled(which)) return [];
      const deps = this.store.depsForDocument(doc);
      if (deps.length === 0) return [];
      const notesUri = await resolveNotesFileFor(doc.uri);
      const notes = notesUri ? await this.store.getNotes(notesUri) : undefined;
      const has = (d: DepEntry): boolean => (notes ? findSection(notes, d.name) !== undefined : false);
      return build(deps, has);
    } catch (e) {
      logError(`noteButtons.${which}`, e);
      return [];
    }
  }
}
