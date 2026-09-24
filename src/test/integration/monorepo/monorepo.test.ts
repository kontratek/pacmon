import * as assert from 'assert';
import * as vscode from 'vscode';

// Runs in a copy of fixtures/monorepo: npm, Mix, Zig and Python manifests with
// nested notes files, plus downloaded/cache manifests discovery must ignore.

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

async function mixHoverText(manifest: vscode.Uri, dep: string): Promise<string> {
  const doc = await vscode.workspace.openTextDocument(manifest);
  await vscode.window.showTextDocument(doc);
  const offset = doc.getText().indexOf(`:${dep}`);
  assert.ok(offset > 0, `${dep} is not in ${manifest.path}`);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
    'vscode.executeHoverProvider',
    manifest,
    doc.positionAt(offset + 2),
  );
  return (hovers ?? [])
    .flatMap((h) => h.contents)
    .map((c) => (typeof c === 'string' ? c : c.value))
    .join('\n');
}

async function zigHoverText(manifest: vscode.Uri, dep: string): Promise<string> {
  const doc = await vscode.workspace.openTextDocument(manifest);
  await vscode.window.showTextDocument(doc);
  const offset = doc.getText().indexOf(`.${dep}`);
  assert.ok(offset > 0, `${dep} is not in ${manifest.path}`);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
    'vscode.executeHoverProvider',
    manifest,
    doc.positionAt(offset + 2),
  );
  return (hovers ?? [])
    .flatMap((h) => h.contents)
    .map((c) => (typeof c === 'string' ? c : c.value))
    .join('\n');
}

async function pythonHoverText(manifest: vscode.Uri, dep: string): Promise<string> {
  const doc = await vscode.workspace.openTextDocument(manifest);
  await vscode.window.showTextDocument(doc);
  const offset = doc.getText().indexOf(dep);
  assert.ok(offset >= 0, `${dep} is not in ${manifest.path}`);
  const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
    'vscode.executeHoverProvider',
    manifest,
    doc.positionAt(offset + 2),
  );
  return (hovers ?? []).flatMap((hover) => hover.contents)
    .map((content) => typeof content === 'string' ? content : content.value).join('\n');
}

const cfg = (): vscode.WorkspaceConfiguration => vscode.workspace.getConfiguration('pacmon');

suite('pacmon monorepo', () => {
  suiteSetup(async function () {
    this.timeout(30000);
    const ext = vscode.extensions.getExtension('kontra.pacmon');
    assert.ok(ext, 'extension not found');
    await ext.activate();
    await cfg().update('noteEntry', 'input', vscode.ConfigurationTarget.Global);
    await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
  });

  suiteTeardown(async () => {
    await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    try {
      await vscode.workspace.fs.delete(at('.pacmon', 'AGENT-RULES.md'));
    } catch {
      // not there
    }
  });

  test('the nearest .pacmon/DEPENDENCY-NOTES.md wins for a nested package.json', async function () {
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

  test('Mix umbrella children resolve their nearest shared or child notes', async function () {
    this.timeout(20000);
    const accounts = await poll(async () => {
      const text = await mixHoverText(at('apps', 'accounts', 'mix.exs'), 'ecto_sql');
      return text.includes('Root umbrella database note') ? text : undefined;
    });
    assert.ok(accounts.includes('.pacmon/mix/DEPENDENCY-NOTES.md'), accounts);

    const web = await poll(async () => {
      const text = await mixHoverText(at('apps', 'web', 'mix.exs'), 'phoenix');
      return text.includes('Web child note') ? text : undefined;
    });
    assert.ok(web.includes('Web child note: nearest Mix file wins.'), web);
    assert.ok(!web.includes('Root umbrella web note'), 'the root Mix note must not leak into the web child');
  });

  test('a nested Zig manifest uses its nearest Zig notes', async function () {
    this.timeout(20000);
    const text = await poll(async () => {
      const value = await zigHoverText(at('apps', 'zig-app', 'build.zig.zon'), 'shared_zig');
      return value.includes('Nested Zig note') ? value : undefined;
    });
    assert.ok(text.includes('.pacmon/zig/DEPENDENCY-NOTES.md'), text);
    assert.ok(!text.includes('Root Zig note'), 'the root Zig note must not leak into the nested project');
  });

  test('Python pyproject and split requirements use their nearest shared notes', async function () {
    this.timeout(20000);
    const pyproject = await pythonHoverText(at('apps', 'python-app', 'pyproject.toml'), 'shared_python');
    assert.ok(pyproject.includes('Nested Python note'), pyproject);
    const requirements = await pythonHoverText(at('apps', 'python-app', 'requirements', 'dev.txt'), 'ruff');
    assert.ok(requirements.includes('Python linting and formatting'), requirements);
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

  test('pacmon.monorepo = rootOnly makes an umbrella child use root Mix notes', async function () {
    this.timeout(20000);
    await cfg().update('monorepo', 'rootOnly', vscode.ConfigurationTarget.Global);
    try {
      const text = await poll(async () => {
        const value = await mixHoverText(at('apps', 'web', 'mix.exs'), 'phoenix');
        return value.includes('Root umbrella web note') ? value : undefined;
      });
      assert.ok(!text.includes('Web child note'), 'rootOnly must ignore the child Mix notes file');
    } finally {
      await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    }
  });

  test('pacmon.monorepo = rootOnly makes a nested Zig manifest use root Zig notes', async function () {
    this.timeout(20000);
    await cfg().update('monorepo', 'rootOnly', vscode.ConfigurationTarget.Global);
    try {
      const text = await poll(async () => {
        const value = await zigHoverText(at('apps', 'zig-app', 'build.zig.zon'), 'shared_zig');
        return value.includes('Root Zig note') ? value : undefined;
      });
      assert.ok(!text.includes('Nested Zig note'), 'rootOnly must ignore the child Zig notes file');
    } finally {
      await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    }
  });

  test('pacmon.monorepo = rootOnly makes a nested Python manifest use root Python notes', async function () {
    this.timeout(20000);
    await cfg().update('monorepo', 'rootOnly', vscode.ConfigurationTarget.Global);
    try {
      const text = await poll(async () => {
        const value = await pythonHoverText(at('apps', 'python-app', 'pyproject.toml'), 'shared_python');
        return value.includes('Root Python note') ? value : undefined;
      });
      assert.ok(!text.includes('Nested Python note'), 'rootOnly must ignore the child Python notes file');
    } finally {
      await cfg().update('monorepo', undefined, vscode.ConfigurationTarget.Global);
    }
  });

  test('Zig package-cache manifests are excluded from note dependency discovery', async function () {
    this.timeout(15000);
    const notes = at('.pacmon', 'zig', 'DEPENDENCY-NOTES.md');
    const doc = await vscode.workspace.openTextDocument(notes);
    const editor = await vscode.window.showTextDocument(doc);
    const ecosystemLine = doc.getText().split(/\r?\n/).findIndex((line) => line === 'ecosystem: zig');
    assert.ok(ecosystemLine > 0, 'fixture changed: no Zig ecosystem line');
    await editor.edit((edit) => edit.replace(doc.lineAt(ecosystemLine).range, 'ecosystem: cargo'));
    try {
      const mine = await poll(() => {
        const diagnostics = vscode.languages.getDiagnostics(notes).filter((item) => item.source === 'pacmon');
        return diagnostics.some((item) => item.code === 'wrong-ecosystem') ? diagnostics : undefined;
      });
      assert.ok(
        !mine.some((item) => item.code === 'removed-but-present'),
        'a dependency found only under zig-pkg must not count as a project dependency',
      );
    } finally {
      await vscode.commands.executeCommand('workbench.action.files.revert');
    }
  });

  test('a new note for a nested package lands in its own .pacmon/; the agent rules land once, at the root', async function () {
    this.timeout(20000);
    const appNotes = at('packages', 'app', '.pacmon', 'DEPENDENCY-NOTES.md');
    const rootNotes = at('.pacmon', 'DEPENDENCY-NOTES.md');
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

      const rules = await poll(async () => (await exists(at('.pacmon', 'AGENT-RULES.md')) ? await readText(at('.pacmon', 'AGENT-RULES.md')) : undefined));
      assert.ok(rules.includes('### Agent notes'), 'AGENT-RULES.md should be the rules file');
      assert.strictEqual(await exists(at('packages', 'app', '.pacmon', 'AGENT-RULES.md')), false, 'AGENT-RULES.md exists once, at the root');
    } finally {
      await vscode.workspace.fs.writeFile(appNotes, originalApp);
      await vscode.workspace.fs.writeFile(rootNotes, originalRoot);
    }
  });
});
