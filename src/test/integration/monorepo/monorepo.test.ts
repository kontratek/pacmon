import * as assert from 'assert';
import * as vscode from 'vscode';

// Runs in a copy of fixtures/monorepo: a root package.json (turbo-fake) with
// its own .pacmon/, and packages/app (express, react-fake) with its own.

function at(...parts: string[]): vscode.Uri {
  const folder = vscode.workspace.workspaceFolders![0]!;
  return vscode.Uri.joinPath(folder.uri, ...parts);
}

async function sleep(ms: number): Promise<void> {
  await new Promise((r) => setTimeout(r, ms));
}

async function poll<T>(fn: () => T | undefined | Promise<T | undefined>, timeoutMs = 8000): Promise<T> {
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

async function exists(uri: vscode.Uri): Promise<boolean> {
  try {
    await vscode.workspace.fs.stat(uri);
    return true;
  } catch {
    return false;
  }
}

/** Pacmon's hover text for one dependency of one package.json ('' when silent). */
async function hoverText(pkg: vscode.Uri, dep: string): Promise<string> {
  const doc = await vscode.workspace.openTextDocument(pkg);
  await vscode.window.showTextDocument(doc);
  const offset = doc.getText().indexOf(`"${dep}"`);
  assert.ok(offset > 0, `${dep} is not in ${pkg.path}`);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
    'vscode.executeHoverProvider',
    pkg,
    doc.positionAt(offset + 2),
  );
  return (hovers ?? [])
    .flatMap((h) => h.contents)
    .map((c) => (typeof c === 'string' ? c : c.value))
    .join('\n');
}

const cfg = (): vscode.WorkspaceConfiguration => vscode.workspace.getConfiguration('pacmon');

suite('pacmon monorepo', () => {
  suiteSetup(async function () {
    this.timeout(30000);
    const ext = vscode.extensions.getExtension('pacmon.pacmon');
    assert.ok(ext, 'extension not found');
    await ext.activate();
    await cfg().update('noteEntry', 'input', vscode.ConfigurationTarget.Global);
    await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
  });

  suiteTeardown(async () => {
    await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    try {
      await vscode.workspace.fs.delete(at('.pacmon', 'AGENTS.md'));
    } catch {
      // not there
    }
  });

  test('the nearest .pacmon/DEPENDENCIES.md wins for a nested package.json', async function () {
    this.timeout(15000);
    const text = await poll(async () => {
      const t = await hoverText(at('packages', 'app', 'package.json'), 'express');
      return t.includes('App-level note') ? t : undefined;
    });
    assert.ok(text.includes('App-level note: nearest file wins.'), text);
    assert.ok(!text.includes('Root-level'), 'the root file must not leak into the nested package');
  });

  test('the root package.json reads the root notes', async function () {
    this.timeout(15000);
    const text = await poll(async () => {
      const t = await hoverText(at('package.json'), 'turbo-fake');
      return t.includes('Root-level') ? t : undefined;
    });
    assert.ok(text.includes('Root-level tooling note.'), text);
  });

  test('pacmon.monorepo = rootOnly ignores the nested notes file', async function () {
    this.timeout(20000);
    await cfg().update('monorepo', 'rootOnly', vscode.ConfigurationTarget.Global);
    try {
      // The root file has no express section, so the hover falls silent.
      const text = await poll(async () => {
        const t = await hoverText(at('packages', 'app', 'package.json'), 'express');
        return t.includes('App-level') ? undefined : t;
      });
      assert.ok(!text.includes('command:pacmon.') && !text.includes('Edit note'), `expected silence, got: ${text}`);
    } finally {
      await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    }
  });

  test('a new note for a nested package lands in its own .pacmon/; the agent rules land once, at the root', async function () {
    this.timeout(20000);
    const appNotes = at('packages', 'app', '.pacmon', 'DEPENDENCIES.md');
    const rootNotes = at('.pacmon', 'DEPENDENCIES.md');
    const originalApp = await vscode.workspace.fs.readFile(appNotes);
    const originalRoot = await vscode.workspace.fs.readFile(rootNotes);
    try {
      const pkg = at('packages', 'app', 'package.json');
      await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(pkg));
      await vscode.commands.executeCommand('pacmon.addOrEditNote', 'react-fake', 'UI runtime (fake)');

      const text = await poll(async () => {
        const t = await readText(appNotes);
        return t.includes('## react-fake') ? t : undefined;
      });
      assert.ok(text.includes('UI runtime (fake)'), 'body missing in the nested notes file');
      assert.ok(text.indexOf('## express') < text.indexOf('## react-fake'), 'sections stay sorted');
      assert.ok(!(await readText(rootNotes)).includes('react-fake'), 'the root notes must stay untouched');

      const rules = await poll(async () => (await exists(at('.pacmon', 'AGENTS.md')) ? await readText(at('.pacmon', 'AGENTS.md')) : undefined));
      assert.ok(rules.includes('### Agent notes'), 'AGENTS.md should be the generated rules');
      assert.strictEqual(await exists(at('packages', 'app', '.pacmon', 'AGENTS.md')), false, 'AGENTS.md exists once, at the root');
    } finally {
      await vscode.workspace.fs.writeFile(appNotes, originalApp);
      await vscode.workspace.fs.writeFile(rootNotes, originalRoot);
    }
  });
});
