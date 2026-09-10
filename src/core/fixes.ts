import { AGENT_FIELD_RE } from './layers';
import { closestAgentKey, lintNotes, type LintFinding } from './lint';
import { parseNotes } from './parseNotes';
import { AGENT_NOTES_HEADING } from './template';

/** The findings that live in the agent layer — the ones with a fix. */
export type AgentProblem = Extract<LintFinding, { kind: 'unknownAgentKey' | 'emptyAgentValue' | 'badAgentValue' }>;

export function isAgentProblem(f: LintFinding): f is AgentProblem {
  return f.kind === 'unknownAgentKey' || f.kind === 'emptyAgentValue' || f.kind === 'badAgentValue';
}

export interface Rewrite {
  text: string;
  /** Set when the key was a misspelling of this vocabulary key. */
  renamedTo?: string;
}

/**
 * How one unknown-key line is fixed: renamed to the field it most likely
 * meant, otherwise kept whole under `note:` (`- foo: bar` → `- note: foo: bar`).
 * Content is never lost.
 */
export function rewriteUnknownKeyLine(lineText: string): Rewrite | undefined {
  const m = AGENT_FIELD_RE.exec(lineText);
  if (!m || m[1] === undefined || m[2] === undefined) return undefined;
  const indent = /^\s*/.exec(lineText)?.[0] ?? '';
  const suggestion = closestAgentKey(m[1]);
  if (suggestion !== undefined) {
    return { text: `${indent}- ${suggestion}: ${m[2]}`.trimEnd(), renamedTo: suggestion };
  }
  const asNote = keepAsNoteLine(lineText);
  return asNote === undefined ? undefined : { text: asNote };
}

/** `- foo: bar` → `- note: foo: bar`, whatever the key. */
export function keepAsNoteLine(lineText: string): string | undefined {
  const m = AGENT_FIELD_RE.exec(lineText);
  if (!m || m[1] === undefined || m[2] === undefined) return undefined;
  const indent = /^\s*/.exec(lineText)?.[0] ?? '';
  return `${indent}- note: ${m[1]}: ${m[2]}`.trimEnd();
}

/** A bare agent layer (as the note panel holds it) wrapped so the lint sees it as one. */
const WRAP = ['## x', '', AGENT_NOTES_HEADING, ''];

/** Problems of a bare agent-layer text; `line` is 0-based within that text. */
export function lintAgentText(agent: string): AgentProblem[] {
  const model = parseNotes([...WRAP, ...agent.split(/\r?\n/)].join('\n'));
  return lintNotes(model, [])
    .filter(isAgentProblem)
    .map((f) => ({ ...f, line: f.line - WRAP.length }));
}

/** The agent text with every unknown key rewritten (renamed, or kept as `note:`). */
export function fixAgentText(agent: string): string {
  const lines = agent.split(/\r?\n/);
  for (const p of lintAgentText(agent)) {
    if (p.kind !== 'unknownAgentKey') continue;
    const r = rewriteUnknownKeyLine(lines[p.line] ?? '');
    if (r) lines[p.line] = r.text;
  }
  return lines.join('\n');
}
