import * as assert from 'assert';
import * as vscode from 'vscode';

function fixtureUri(...parts: string[]): vscode.Uri {
  const folder = vscode.workspace.workspaceFolders![0]!;
  return vscode.Uri.joinPath(folder.uri, ...parts);
}

async function sleep(ms: number): Promise<void> {
  await new Promise((r) => setTimeout(r, ms));
}

async function poll<T>(
  fn: () => T | undefined | Promise<T | undefined>,
  timeoutMs = 5000,
): Promise<T> {
  const start = Date.now();
  for (;;) {
    const v = await fn();
    if (v !== undefined) return v;
    if (Date.now() - start > timeoutMs) throw new Error('poll timeout');
    await sleep(100);
  }
}

async function readText(uri: vscode.Uri): Promise<string> {
  return new TextDecoder().decode(await vscode.workspace.fs.readFile(uri));
}

suite('pacmon integration', () => {
  suiteSetup(async function () {
    this.timeout(30000);
    const ext = vscode.extensions.getExtension('kontra.pacmon');
    assert.ok(ext, 'extension not found');
    await ext.activate();
    // The default "panel" entry opens UI whose contents automated tests cannot
    // reach; pin the deterministic input mode for the note-writing tests
    // (isolated test profile — no user impact). The panel gets its own test.
    await vscode.workspace
      .getConfiguration('pacmon')
      .update('noteEntry', 'input', vscode.ConfigurationTarget.Global);
  });

  suiteTeardown(async () => {
    // Every note write brings .pacmon/AGENTS.md along; keep the fixture clean.
    try {
      await vscode.workspace.fs.delete(fixtureUri('.pacmon', 'AGENTS.md'));
    } catch {
      // not there
    }
  });

  test('hover on a documented dependency shows the note', async () => {
    const pkg = fixtureUri('package.json');
    const doc = await vscode.workspace.openTextDocument(pkg);
    await vscode.window.showTextDocument(doc);
    const offset = doc.getText().indexOf('"express"');
    assert.ok(offset > 0);
    const pos = doc.positionAt(offset + 2);
    const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
      'vscode.executeHoverProvider',
      pkg,
      pos,
    );
    const all = (hovers ?? [])
      .flatMap((h) => h.contents)
      .map((c) => (typeof c === 'string' ? c : c.value))
      .join('\n');
    assert.ok(all.includes('HTTP API layer'), `hover missing note, got: ${all}`);
    assert.ok(all.includes('Edit note'), 'hover missing edit link');
    // Both layers, human first by default, a rule between them.
    const human = all.indexOf('HTTP API layer');
    const agent = all.indexOf('- purpose:');
    assert.ok(agent > -1, `hover should carry the agent block too, got: ${all}`);
    assert.ok(human < agent, 'human text leads with the default inlineSource');
    assert.ok(all.slice(human, agent).includes('---'), 'a rule separates the layers');
  });

  test('hover on an undocumented dependency stays silent', async () => {
    const pkg = fixtureUri('package.json');
    const doc = await vscode.workspace.openTextDocument(pkg);
    const offset = doc.getText().indexOf('"@scope/util"');
    const pos = doc.positionAt(offset + 2);
    const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
      'vscode.executeHoverProvider',
      pkg,
      pos,
    );
    const ours = (hovers ?? [])
      .flatMap((h) => h.contents)
      .map((c) => (typeof c === 'string' ? c : c.value))
      .join('\n');
    assert.ok(
      !ours.includes('command:pacmon.') && !ours.includes('Edit note'),
      `expected silence, got: ${ours}`,
    );
  });

  test('sections not in package.json get no diagnostic, and everything Pacmon reports is a warning', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const doc = await vscode.workspace.openTextDocument(notes);
    const editor = await vscode.window.showTextDocument(doc);
    // Force one real diagnostic so we know the refresh has run.
    const line = doc.getText().split(/\r?\n/).findIndex((l) => l.startsWith('- verified: 4.18.0'));
    assert.ok(line > 0, 'fixture changed: no express agent block');
    await editor.edit((b) => b.insert(new vscode.Position(line, 0), '- dafdsf: fdsaf\n'));
    try {
      const mine = await poll(() => {
        const d = vscode.languages.getDiagnostics(notes).filter((x) => x.source === 'pacmon');
        return d.some((x) => x.code === 'unknown-agent-key') ? d : undefined;
      });
      // ghost-package (a real orphan) and old-package (marked removed) are markers, not problems.
      assert.deepStrictEqual(
        mine.filter((d) => d.message.includes('ghost-package') || d.message.includes('old-package')).map((d) => d.message),
        [],
      );
      for (const d of mine) {
        assert.strictEqual(d.severity, vscode.DiagnosticSeverity.Warning, `not a warning: ${d.message}`);
      }
    } finally {
      await vscode.commands.executeCommand('workbench.action.files.revert');
    }
  });

  test('a stray heading, a second title and a stale removed status are flagged with one-click fixes', async function () {
    this.timeout(20000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const doc = await vscode.workspace.openTextDocument(notes);
    const editor = await vscode.window.showTextDocument(doc);
    const lines = doc.getText().split(/\r?\n/);
    const humanLine = lines.findIndex((l) => l.startsWith('Do not upgrade to v5'));
    const verifiedLine = lines.findIndex((l) => l.startsWith('- verified: 4.18.0'));
    assert.ok(humanLine > 0 && verifiedLine > humanLine, 'fixture changed');
    await editor.edit((b) => {
      b.insert(new vscode.Position(humanLine, 0), '### Known quirks\n');
      b.insert(new vscode.Position(verifiedLine, 0), '- status: removed 2026-01 — test\n');
      b.insert(doc.lineAt(doc.lineCount - 1).range.end, '\n# Second title\n');
    });
    try {
      const mine = (): vscode.Diagnostic[] => vscode.languages.getDiagnostics(notes).filter((d) => d.source === 'pacmon');
      const found = await poll(() => {
        const d = mine();
        const stray = d.find((x) => x.code === 'stray-heading');
        const removed = d.find((x) => x.code === 'removed-but-present');
        const extra = d.find((x) => x.code === 'extra-title');
        return stray && removed && extra ? { stray, removed, extra } : undefined;
      }, 10000);
      assert.ok(found.stray.message.includes('Agent notes'), found.stray.message);
      assert.ok(found.removed.message.includes('"express"'), found.removed.message);

      const fixes = async (d: vscode.Diagnostic): Promise<vscode.CodeAction[]> =>
        (await vscode.commands.executeCommand<vscode.CodeAction[]>('vscode.executeCodeActionProvider', notes, d.range)) ?? [];
      // Bottom-up, so no fix shifts the lines a later one still points at.
      const secondTitle = (await fixes(found.extra)).find((a) => a.title === 'Make it plain text');
      assert.ok(secondTitle?.edit, 'extra title: make it plain text');
      await vscode.workspace.applyEdit(secondTitle.edit);
      assert.ok(doc.getText().includes('**Second title**'), 'second title should be plain text now');

      const removeStatus = (await fixes(found.removed)).find((a) => a.title === 'Remove this line');
      assert.ok(removeStatus?.isPreferred && removeStatus.edit, 'removed-but-present: remove the line');
      await vscode.workspace.applyEdit(removeStatus.edit);
      assert.ok(!doc.getText().includes('status: removed 2026-01'), 'status line should be gone');

      const plain = (await fixes(found.stray)).find((a) => a.title === 'Make it plain text');
      assert.ok(plain?.isPreferred && plain.edit, 'stray heading: make it plain text');
      await vscode.workspace.applyEdit(plain.edit);
      assert.strictEqual(doc.lineAt(humanLine).text, '**Known quirks**');
    } finally {
      await vscode.commands.executeCommand('workbench.action.files.revert');
    }
  });

  test('an unknown key in the agent block gets a diagnostic and a keep-as-note fix', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const doc = await vscode.workspace.openTextDocument(notes);
    const editor = await vscode.window.showTextDocument(doc);
    const line = doc.getText().split(/\r?\n/).findIndex((l) => l.startsWith('- verified: 4.18.0'));
    assert.ok(line > 0, 'fixture changed: no express agent block');
    await editor.edit((b) =>
      b.insert(new vscode.Position(line, 0), '- dafdsf: fdsaf\n- contraint: stay on ^4\n'),
    );
    try {
      // Only the two inserted lines matter; the fixture may carry other unknown keys.
      const { gibberish, typo } = await poll(() => {
        const d = vscode.languages
          .getDiagnostics(notes)
          .filter((x) => x.source === 'pacmon' && x.code === 'unknown-agent-key');
        const g = d.find((x) => x.range.start.line === line);
        const t = d.find((x) => x.range.start.line === line + 1);
        return g && t ? { gibberish: g, typo: t } : undefined;
      }, 10000);
      assert.ok(gibberish.message.includes('dafdsf') && !gibberish.message.includes('did you mean'), gibberish.message);
      assert.ok(typo.message.includes('"constraint:"'), typo.message);
      // A warning (yellow, counted, tinting the tab), on the key alone.
      assert.strictEqual(typo.severity, vscode.DiagnosticSeverity.Warning);
      assert.strictEqual(typo.range.start.character, 2);
      assert.strictEqual(typo.range.end.character, 2 + 'contraint'.length);

      // The typo's preferred fix is the rename; gibberish is kept as a note.
      const forTypo = await vscode.commands.executeCommand<vscode.CodeAction[]>(
        'vscode.executeCodeActionProvider',
        notes,
        typo.range,
      );
      const rename = (forTypo ?? []).find((a) => a.title.includes('constraint'));
      assert.ok(rename?.isPreferred, `rename should be the preferred fix among: ${(forTypo ?? []).map((a) => a.title).join(', ')}`);
      const forGibberish = await vscode.commands.executeCommand<vscode.CodeAction[]>(
        'vscode.executeCodeActionProvider',
        notes,
        gibberish.range,
      );
      assert.ok((forGibberish ?? []).some((a) => a.title.includes('note') && a.isPreferred), 'keep-as-note is preferred for gibberish');
      // The squiggle is on the key, but the fix is there with the caret anywhere on the line.
      const atEnd = new vscode.Position(line + 1, doc.lineAt(line + 1).text.length);
      const forCaret = await vscode.commands.executeCommand<vscode.CodeAction[]>(
        'vscode.executeCodeActionProvider',
        notes,
        new vscode.Range(atEnd, atEnd),
      );
      assert.ok((forCaret ?? []).some((a) => a.title.includes('constraint')), 'fix offered at the end of the line too');

      // Fix All exists — the on-save path.
      const all = await vscode.commands.executeCommand<vscode.CodeAction[]>(
        'vscode.executeCodeActionProvider',
        notes,
        new vscode.Range(0, 0, doc.lineCount, 0),
        'source.fixAll.pacmon',
      );
      assert.ok(
        (all ?? []).some((a) => a.kind?.value === 'source.fixAll.pacmon' && a.edit),
        `no fix-all among: ${(all ?? []).map((a) => `${a.kind?.value ?? '?'}:${a.title}`).join(', ')}`,
      );

      // The code lens above the block names the count and fixes both lines in one click.
      const lenses = await vscode.commands.executeCommand<vscode.CodeLens[]>('vscode.executeCodeLensProvider', notes);
      const lens = (lenses ?? []).find((l) => l.command?.command === 'pacmon.fixAgentNotes');
      assert.ok(lens?.command, `no fix lens among: ${(lenses ?? []).map((l) => l.command?.title ?? '?').join(', ')}`);
      const fileLines = doc.getText().split(/\r?\n/);
      const headingLine = fileLines.findIndex((l) => l === '### Agent notes');
      const blockEnd = fileLines.findIndex((l, i) => i > headingLine && l.startsWith('## '));
      assert.strictEqual(lens.range.start.line, headingLine, 'lens sits on the block heading');
      // The count is whatever the block holds right now — at least our two.
      const inBlock = vscode.languages
        .getDiagnostics(notes)
        .filter((d) => d.source === 'pacmon' && d.severity === vscode.DiagnosticSeverity.Warning)
        .filter((d) => d.range.start.line > headingLine && d.range.start.line < blockEnd).length;
      assert.ok(inBlock >= 2, `expected at least the two inserted problems, got ${inBlock}`);
      assert.ok(lens.command.title.includes(`${inBlock} problem`), `${lens.command.title} (block has ${inBlock})`);
      await vscode.commands.executeCommand(lens.command.command, ...(lens.command.arguments ?? []));
      await poll(() => (doc.lineAt(line).text === '- note: dafdsf: fdsaf' ? true : undefined));
      assert.strictEqual(doc.lineAt(line + 1).text, '- constraint: stay on ^4');
    } finally {
      await vscode.commands.executeCommand('workbench.action.files.revert');
    }
  });

  test('normalize canonicalizes a messy heading and is undoable (buffer only)', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const doc = await vscode.workspace.openTextDocument(notes);
    const editor = await vscode.window.showTextDocument(doc);
    await editor.edit((b) => {
      b.insert(doc.lineAt(doc.lineCount - 1).range.end, '\n##   zzz-messy  \nbody text\n');
    });
    await vscode.commands.executeCommand('pacmon.normalizeNotesFile');
    await poll(() => (doc.getText().includes('## zzz-messy') ? true : undefined));
    const text = doc.getText();
    assert.ok(text.includes('## zzz-messy'), 'heading not canonicalized');
    assert.ok(!text.includes('##   zzz-messy'), 'messy heading still present');
    await vscode.commands.executeCommand('workbench.action.files.revert');
  });

  test('addOrEditNote with a body writes inline and stays in package.json', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const original = await vscode.workspace.fs.readFile(notes);
    try {
      const pkg = fixtureUri('package.json');
      const pdoc = await vscode.workspace.openTextDocument(pkg);
      await vscode.window.showTextDocument(pdoc);

      await vscode.commands.executeCommand('pacmon.addOrEditNote', 'vitest', 'why: unit test runner');

      const text = new TextDecoder().decode(await vscode.workspace.fs.readFile(notes));
      assert.ok(text.includes('## vitest'), 'section missing on disk');
      assert.ok(text.includes('why: unit test runner'), 'body missing on disk');
      assert.ok(text.indexOf('## vitest') > text.indexOf('## lodash'), 'vitest should sort after lodash');
      assert.strictEqual(
        vscode.window.activeTextEditor?.document.uri.toString(),
        pkg.toString(),
        'focus must stay in package.json',
      );
    } finally {
      await vscode.workspace.fs.writeFile(notes, original);
    }
  });

  test('context-menu style invocation (Uri as first arg) resolves the dep from the cursor', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const original = await vscode.workspace.fs.readFile(notes);
    try {
      const pkg = fixtureUri('package.json');
      const doc = await vscode.workspace.openTextDocument(pkg);
      const ed = await vscode.window.showTextDocument(doc);
      // showTextDocument can resolve a beat before the editor is ACTIVE. This
      // test reads the dep off the cursor, so a stale active editor sends it
      // down the "pick a dependency" path and nothing is written.
      await poll(() =>
        vscode.window.activeTextEditor?.document.uri.toString() === pkg.toString() ? true : undefined,
      );
      const off = doc.getText().indexOf('"vitest"') + 2;
      const pos = doc.positionAt(off);
      ed.selection = new vscode.Selection(pos, pos);
      // Editor context menus pass the resource Uri as the first argument.
      await vscode.commands.executeCommand('pacmon.addOrEditNote', pkg, 'ctx-menu body');
      // The write lands either straight on disk or through the open document's
      // save, depending on whether the previous test's restore has settled —
      // so wait for the content rather than reading once.
      const text = await poll(async () => {
        const t = new TextDecoder().decode(await vscode.workspace.fs.readFile(notes));
        return t.includes('## vitest') ? t : undefined;
      });
      assert.ok(text.includes('ctx-menu body'), 'body missing');
    } finally {
      await vscode.workspace.fs.writeFile(notes, original);
    }
  });

  test('editing an existing note opens the file at the section', async function () {
    this.timeout(15000);
    await vscode.commands.executeCommand('pacmon.addOrEditNote', 'express');
    await poll(() => {
      const uri = vscode.window.activeTextEditor?.document.uri;
      return uri && uri.path.endsWith('.pacmon/DEPENDENCIES.md') ? true : undefined;
    });
    const ed = vscode.window.activeTextEditor!;
    const headingLine = ed.document.lineAt(Math.max(ed.selection.active.line - 1, 0)).text;
    assert.strictEqual(headingLine, '## express', `cursor should sit right under the heading, got: "${headingLine}"`);
  });

  test('note markers: the setting wins, and the toggle writes it', async function () {
    this.timeout(20000);
    const cfg = (): vscode.WorkspaceConfiguration => vscode.workspace.getConfiguration('pacmon');
    const read = (): string => cfg().get<string>('decorations', 'preview');
    const saved = cfg().inspect('decorations');
    try {
      // Start from a known state in BOTH targets: a workspace value shadows
      // the global one, and a previous run may have left one behind.
      await cfg().update('decorations', undefined, vscode.ConfigurationTarget.Workspace);
      await cfg().update('decorations', 'preview', vscode.ConfigurationTarget.Global);
      await sleep(400);
      assert.strictEqual(read(), 'preview', `setup failed, effective value is "${read()}"`);

      // The toggle used to write a hidden per-workspace flag that outranked
      // the setting, so choosing "off" in the view could do nothing at all.
      await vscode.commands.executeCommand('pacmon.toggleDecorations');
      await sleep(400);
      assert.strictEqual(read(), 'off', `toggle should have written the setting, got "${read()}"`);

      await vscode.commands.executeCommand('pacmon.toggleDecorations');
      await sleep(400);
      assert.strictEqual(read(), 'preview', `toggling back should restore, got "${read()}"`);

      // An explicit "off" stays off. Clear the workspace value the toggle just
      // wrote first — a workspace setting outranking a global one is ordinary
      // VS Code precedence, not the bug this test guards.
      await cfg().update('decorations', undefined, vscode.ConfigurationTarget.Workspace);
      await cfg().update('decorations', 'off', vscode.ConfigurationTarget.Global);
      await sleep(400);
      assert.strictEqual(read(), 'off', `explicit off must stick, got "${read()}"`);
    } finally {
      await cfg().update('decorations', undefined, vscode.ConfigurationTarget.Workspace);
      await cfg().update('decorations', saved?.globalValue, vscode.ConfigurationTarget.Global);
    }
  });

  test('opening package.json from the command works from anywhere', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(notes));
    await vscode.commands.executeCommand('pacmon.openPackageJson');
    const uri = await poll(() => {
      const u = vscode.window.activeTextEditor?.document.uri;
      return u && u.path.endsWith('package.json') ? u : undefined;
    });
    assert.ok(uri.path.endsWith('package.json'));
  });

  // NOTE: the settings view reveals the manifest from inside its webview, which
  // a test cannot click. What is covered here is the surfacing itself — that
  // package.json can be brought back with nothing open. The webview wiring that
  // calls it needs a human.
  test('the manifest can be surfaced with no editor open', async function () {
    this.timeout(20000);
    const cfg = vscode.workspace.getConfiguration('pacmon');
    const saved = cfg.inspect('noteButtons')?.globalValue;
    try {
      // Close everything, then focus the Pacmon view as a user would.
      await vscode.commands.executeCommand('workbench.action.closeAllEditors');
      await poll(() => (vscode.window.visibleTextEditors.length === 0 ? true : undefined));
      await vscode.commands.executeCommand('pacmon.settings.focus');

      // A setting change of the kind the view writes...
      await vscode.workspace
        .getConfiguration('pacmon')
        .update('noteButtons', ['codelens'], vscode.ConfigurationTarget.Global);
      await vscode.commands.executeCommand('pacmon.openPackageJson');

      const shown = await poll(() =>
        vscode.window.visibleTextEditors.find((e) => e.document.uri.path.endsWith('package.json')),
      );
      assert.ok(shown, 'package.json should be on screen');
    } finally {
      await vscode.workspace
        .getConfiguration('pacmon')
        .update('noteButtons', saved, vscode.ConfigurationTarget.Global);
    }
  });

  test('the Pacmon view is contributed and its rows toggle the setting', async function () {
    this.timeout(20000);
    const ext = vscode.extensions.getExtension('kontra.pacmon')!;
    const contributes = ext.packageJSON.contributes as {
      viewsContainers: { activitybar: { id: string; icon: string }[] };
      views: Record<string, { id: string; type?: string }[]>;
    };
    assert.ok(
      contributes.viewsContainers.activitybar.some((c) => c.id === 'pacmon'),
      'no activity bar container',
    );
    const view = contributes.views['pacmon']?.find((v) => v.id === 'pacmon.settings');
    assert.ok(view, 'no settings view');
    assert.strictEqual(view.type, 'webview', 'the settings view should be a webview');

    // Focusing the view is what a user clicking the activity bar icon does.
    await vscode.commands.executeCommand('pacmon.settings.focus');

    const cfg = (): string[] =>
      vscode.workspace.getConfiguration('pacmon').get<string[]>('noteButtons', []);
    const before = cfg();
    try {
      await vscode.workspace
        .getConfiguration('pacmon')
        .update('noteButtons', ['codelens'], vscode.ConfigurationTarget.Global);
      await poll(() => (cfg().includes('codelens') ? true : undefined));
      // The opt-in arm must actually come back when it is selected.
      const lenses = await poll(async () => {
        const all = await vscode.commands.executeCommand<vscode.CodeLens[]>(
          'vscode.executeCodeLensProvider',
          fixtureUri('package.json'),
        );
        const mine = (all ?? []).filter((l) => l.command?.command === 'pacmon.addOrEditNote');
        return mine.length > 0 ? mine : undefined;
      });
      assert.ok(lenses.length > 0, 'codelens should be available when selected');

      // Links have no change event and VS Code caches them, so the provider is
      // re-registered by hand; without that, toggling link off does nothing.
      const commandLinks = async (): Promise<number> => {
        const all = await vscode.commands.executeCommand<vscode.DocumentLink[]>(
          'vscode.executeLinkProvider',
          fixtureUri('package.json'),
        );
        return (all ?? []).filter((l) => l.target?.scheme === 'command').length;
      };
      assert.strictEqual(await commandLinks(), 0, 'link is off, so no command links');
      await vscode.workspace
        .getConfiguration('pacmon')
        .update('noteButtons', ['codelens', 'link'], vscode.ConfigurationTarget.Global);
      const withLink = await poll(async () => {
        const n = await commandLinks();
        return n > 0 ? n : undefined;
      });
      assert.ok(withLink > 0, 'turning link on must take effect without editing the file');
    } finally {
      await vscode.workspace
        .getConfiguration('pacmon')
        .update('noteButtons', before.length > 0 ? before : undefined, vscode.ConfigurationTarget.Global);
    }
  });

  suite('note affordances (pacmon.noteButtons)', () => {
    const OURS = 'pacmon.addOrEditNote';

    async function withButtons<T>(buttons: string[], fn: () => Promise<T>): Promise<T> {
      const cfg = vscode.workspace.getConfiguration('pacmon');
      await cfg.update('noteButtons', buttons, vscode.ConfigurationTarget.Global);
      try {
        return await fn();
      } finally {
        await cfg.update('noteButtons', undefined, vscode.ConfigurationTarget.Global);
      }
    }

    test('link turns the package name into a Ctrl+click command link', async function () {
      this.timeout(15000);
      await withButtons(['link'], async () => {
        const uri = fixtureUri('package.json');
        const links = await poll(async () => {
          const all = await vscode.commands.executeCommand<vscode.DocumentLink[]>(
            'vscode.executeLinkProvider',
            uri,
          );
          const mine = (all ?? []).filter((l) => l.target?.scheme === 'command');
          return mine.length > 0 ? mine : undefined;
        });
        const target = links.find((l) => decodeURIComponent(l.target!.query).includes('express'));
        assert.ok(target, 'no command link for express');
        assert.strictEqual(target.target!.path, OURS);
      });
    });

    test('iconLeft is a decoration — it emits no inlay hint and no code lens', async function () {
      this.timeout(15000);
      await withButtons(['iconLeft'], async () => {
        const uri = fixtureUri('package.json');
        const doc = await vscode.workspace.openTextDocument(uri);
        const hints = await vscode.commands.executeCommand<vscode.InlayHint[]>(
          'vscode.executeInlayHintProvider',
          uri,
          new vscode.Range(0, 0, doc.lineCount, 0),
        );
        const lenses = await vscode.commands.executeCommand<vscode.CodeLens[]>(
          'vscode.executeCodeLensProvider',
          uri,
        );
        assert.strictEqual((hints ?? []).length, 0, 'the glyph must not be an inlay hint');
        assert.strictEqual(
          (lenses ?? []).filter((l) => l.command?.command === OURS).length,
          0,
          'no code lens should remain',
        );
      });
    });

    test('the icon click path ignores non-mouse caret moves', async function () {
      this.timeout(20000);
      const cfg = vscode.workspace.getConfiguration('pacmon');
      await cfg.update('noteEntry', 'panel', vscode.ConfigurationTarget.Global);
      try {
        await withButtons(['iconLeft'], async () => {
          const uri = fixtureUri('package.json');
          const doc = await vscode.workspace.openTextDocument(uri);
          const ed = await vscode.window.showTextDocument(doc, vscode.ViewColumn.One);

          // Exactly where a click on the glyph lands. Arriving there
          // programmatically (kind Command/undefined) must do nothing —
          // otherwise Home, arrow keys and every jump would open the note.
          const keyPos = doc.positionAt(doc.getText().indexOf('"express"'));
          ed.selection = new vscode.Selection(keyPos, keyPos);
          await sleep(1200);

          const panel = vscode.window.tabGroups.all
            .flatMap((g) => g.tabs)
            .find(
              (t) =>
                t.input instanceof vscode.TabInputWebview &&
                t.input.viewType.includes('pacmon.noteEditor'),
            );
          assert.strictEqual(panel, undefined, 'a non-mouse caret move must not open the note');
        });
      } finally {
        await cfg.update('noteEntry', 'input', vscode.ConfigurationTarget.Global);
      }
    });

    test('an empty setting silences both affordances', async function () {
      this.timeout(15000);
      await withButtons([], async () => {
        const uri = fixtureUri('package.json');
        const links = await vscode.commands.executeCommand<vscode.DocumentLink[]>(
          'vscode.executeLinkProvider',
          uri,
        );
        assert.strictEqual(
          (links ?? []).filter((l) => l.target?.scheme === 'command').length,
          0,
          'no command links',
        );
        // The right-click command must still work with every affordance off.
        const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
        const original = await vscode.workspace.fs.readFile(notes);
        try {
          await vscode.commands.executeCommand('pacmon.addOrEditNote', 'vitest', 'still reachable');
          const text = await poll(async () => {
            const t = new TextDecoder().decode(await vscode.workspace.fs.readFile(notes));
            return t.includes('still reachable') ? t : undefined;
          });
          assert.ok(text.includes('## vitest'));
        } finally {
          await vscode.workspace.fs.writeFile(notes, original);
        }
      });
    });
  });

  test('panel mode opens the note editor beside package.json', async function () {
    this.timeout(15000);
    const cfg = vscode.workspace.getConfiguration('pacmon');
    await cfg.update('noteEntry', 'panel', vscode.ConfigurationTarget.Global);
    try {
      const pkg = fixtureUri('package.json');
      const doc = await vscode.workspace.openTextDocument(pkg);
      await vscode.window.showTextDocument(doc, vscode.ViewColumn.One);

      await vscode.commands.executeCommand('pacmon.addOrEditNote', 'express');

      const tab = await poll(() =>
        vscode.window.tabGroups.all
          .flatMap((g) => g.tabs)
          .find(
            (t) =>
              t.input instanceof vscode.TabInputWebview &&
              t.input.viewType.includes('pacmon.noteEditor'),
          ),
      );
      assert.ok(
        tab.label.includes('express'),
        `panel title should name the dependency, got: "${tab.label}"`,
      );
      assert.ok(tab.group.viewColumn !== vscode.ViewColumn.One, 'panel should open beside package.json');
      await vscode.window.tabGroups.close(tab);
    } finally {
      await cfg.update('noteEntry', 'input', vscode.ConfigurationTarget.Global);
    }
  });

  test('the first note creates .pacmon/, the notes file and AGENTS.md', async function () {
    this.timeout(30000);
    const dir = fixtureUri('.pacmon');
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const agents = fixtureUri('.pacmon', 'AGENTS.md');
    const original = await vscode.workspace.fs.readFile(notes);
    try {
      await vscode.commands.executeCommand('workbench.action.closeAllEditors');
      await vscode.workspace.fs.delete(dir, { recursive: true, useTrash: false });
      // Give the watcher a moment to see the deletion.
      await sleep(800);

      const pkg = fixtureUri('package.json');
      await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(pkg));
      await vscode.commands.executeCommand('pacmon.addOrEditNote', 'lodash', 'fresh start');

      const text = await poll(async () => {
        try {
          const t = await readText(notes);
          return t.includes('fresh start') ? t : undefined;
        } catch {
          return undefined;
        }
      });
      assert.ok(
        text.startsWith('---\nformat: deps-notes/1\nlang: en\nagents: .pacmon/AGENTS.md\n---\n'),
        `template missing, got: ${text.slice(0, 120)}`,
      );
      assert.ok(text.includes('## lodash\n\nfresh start'), 'section missing');
      const rules = await poll(async () => {
        try {
          return await readText(agents);
        } catch {
          return undefined;
        }
      });
      assert.ok(
        rules.includes('### Agent notes') && rules.includes('| `purpose:` |'),
        'AGENTS.md should carry the rules and the field table',
      );
    } finally {
      await vscode.workspace.fs.createDirectory(dir);
      await vscode.workspace.fs.writeFile(notes, original);
      try {
        await vscode.workspace.fs.delete(agents);
      } catch {
        // never created
      }
    }
  });

  test('Set Up AI Instructions regenerates .pacmon/AGENTS.md and leaves a three-line pointer', async function () {
    this.timeout(15000);
    const rootAgents = fixtureUri('AGENTS.md');
    const rules = fixtureUri('.pacmon', 'AGENTS.md');
    try {
      await vscode.commands.executeCommand('pacmon.setupAiInstructions', ['AGENTS.md']);
      const pointer = await poll(async () => {
        try {
          return await readText(rootAgents);
        } catch {
          return undefined;
        }
      });
      const m = /<!-- pacmon:deps-notes:start -->\n([\s\S]*?)<!-- pacmon:deps-notes:end -->/.exec(pointer);
      assert.ok(m, `no marker block in AGENTS.md, got: ${pointer}`);
      assert.strictEqual(m[1]!.trim().split('\n').length, 3, 'the pointer is exactly three lines');
      assert.ok(pointer.includes('.pacmon/AGENTS.md'), 'the pointer names the rules file');
      const generated = await readText(rules);
      assert.ok(generated.includes('| `verified:` |'), '.pacmon/AGENTS.md should be the generated rules');
    } finally {
      for (const u of [rootAgents, rules]) {
        try {
          await vscode.workspace.fs.delete(u);
        } catch {
          // never created
        }
      }
    }
  });

  test('a human edit replaces only the human text — the agent block survives', async function () {
    this.timeout(15000);
    const notes = fixtureUri('.pacmon', 'DEPENDENCIES.md');
    const agents = fixtureUri('.pacmon', 'AGENTS.md');
    const original = await vscode.workspace.fs.readFile(notes);
    try {
      try {
        await vscode.workspace.fs.delete(agents);
      } catch {
        // not there yet — that is the case under test
      }
      const pkg = fixtureUri('package.json');
      await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(pkg));
      await vscode.commands.executeCommand('pacmon.addOrEditNote', 'express', 'new human line');
      const text = await poll(async () => {
        const t = await readText(notes);
        return t.includes('new human line') ? t : undefined;
      });
      assert.ok(!text.includes('HTTP API layer'), 'the old human text should be gone');
      // Search from the section: the file header also mentions "### Agent notes".
      const section = text.indexOf('## express');
      const agentHeading = text.indexOf('\n### Agent notes\n', section);
      assert.ok(agentHeading > section, 'agent heading lost');
      assert.ok(text.includes('- constraint: stay on ^4'), 'agent line lost');
      const human = text.indexOf('new human line');
      assert.ok(
        section < human && human < agentHeading,
        'human text sits between the heading and the agent block',
      );
      // A notes file that exists without the agent rules gets them on the
      // first write — not only when the file itself is created.
      const rules = await poll(async () => {
        try {
          return await readText(agents);
        } catch {
          return undefined;
        }
      });
      assert.ok(rules.includes('| `purpose:` |'), 'AGENTS.md should appear next to an existing notes file');
    } finally {
      await vscode.workspace.fs.writeFile(notes, original);
      try {
        await vscode.workspace.fs.delete(agents);
      } catch {
        // never created
      }
    }
  });
});
