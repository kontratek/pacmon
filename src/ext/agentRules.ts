import * as vscode from 'vscode';

/**
 * `assets/AGENT-RULES.md` of the installed extension: the file Pacmon copies
 * into a workspace as `.pacmon/AGENT-RULES.md`. Hand-written and shipped as
 * is; read once, on first use.
 */
const ASSET_PATH = ['assets', 'AGENT-RULES.md'];

let extensionRoot: vscode.Uri | undefined;
let text: Promise<string> | undefined;

/** Called from activate(). */
export function setExtensionRoot(uri: vscode.Uri): void {
  extensionRoot = uri;
  text = undefined;
}

async function readAsset(): Promise<string> {
  if (!extensionRoot) throw new Error('Pacmon: extension root not set');
  const bytes = await vscode.workspace.fs.readFile(vscode.Uri.joinPath(extensionRoot, ...ASSET_PATH));
  return new TextDecoder().decode(bytes).replace(/\r\n/g, '\n');
}

export function agentRulesText(): Promise<string> {
  text ??= readAsset();
  return text;
}
