import { dependencyEntry } from './dependency';
import type { DependencyEntry, SourceRange } from './model';

interface KeyPart {
  value: string;
  range: SourceRange;
}

const DEPENDENCY_TABLES = new Set(['dependencies', 'dev-dependencies', 'build-dependencies']);

function splitLines(text: string): Array<{ text: string; offset: number }> {
  const lines: Array<{ text: string; offset: number }> = [];
  let offset = 0;
  for (const line of text.split(/\r?\n/)) {
    lines.push({ text: line, offset });
    offset += line.length + (text.slice(offset + line.length, offset + line.length + 2) === '\r\n' ? 2 : 1);
  }
  return lines;
}

function contentBeforeComment(line: string): string {
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i]!;
    if (quote === '"' && escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"' && char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) quote = undefined;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === '#') return line.slice(0, i);
  }
  return line;
}

function decodeKey(raw: string): string {
  if (raw.startsWith('"') && raw.endsWith('"')) {
    try {
      return JSON.parse(raw) as string;
    } catch {
      return raw.slice(1, -1);
    }
  }
  if (raw.startsWith("'") && raw.endsWith("'")) return raw.slice(1, -1);
  return raw;
}

function keyParts(raw: string, absoluteOffset: number): KeyPart[] | undefined {
  const out: KeyPart[] = [];
  let start = 0;
  let quote: '"' | "'" | undefined;
  let escaped = false;
  const push = (end: number): boolean => {
    const segment = raw.slice(start, end);
    const leading = segment.length - segment.trimStart().length;
    const token = segment.trim();
    if (token === '') return false;
    if ((token.startsWith('"') && !token.endsWith('"')) || (token.startsWith("'") && !token.endsWith("'"))) {
      return false;
    }
    out.push({
      value: decodeKey(token),
      range: { offset: absoluteOffset + start + leading, length: token.length },
    });
    return true;
  };

  for (let i = 0; i < raw.length; i++) {
    const char = raw[i]!;
    if (quote === '"' && escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"' && char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) quote = undefined;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === '.') {
      if (!push(i)) return undefined;
      start = i + 1;
    }
  }
  if (quote || !push(raw.length)) return undefined;
  return out;
}

function unquotedIndex(raw: string, wanted: string): number {
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let i = 0; i < raw.length; i++) {
    const char = raw[i]!;
    if (quote === '"' && escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"' && char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) quote = undefined;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === wanted) return i;
  }
  return -1;
}

function nestingDelta(raw: string): number {
  let delta = 0;
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let i = 0; i < raw.length; i++) {
    const char = raw[i]!;
    if (quote === '"' && escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"' && char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) quote = undefined;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === '{' || char === '[') delta++;
    else if (char === '}' || char === ']') delta--;
  }
  return delta;
}

function tableHeader(line: string): { body: string; start: number } | undefined {
  const leading = line.length - line.trimStart().length;
  if (line[leading] !== '[' || line[leading + 1] === '[') return undefined;
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let i = leading + 1; i < line.length; i++) {
    const char = line[i]!;
    if (quote === '"' && escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"' && char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) quote = undefined;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === ']') return { body: line.slice(leading + 1, i), start: leading + 1 };
  }
  return undefined;
}

function tableScope(parts: readonly KeyPart[]): { scope: string; dependencyIndex?: number } | undefined {
  if (parts.length === 0 || parts[0]!.value === 'workspace') return undefined;
  let sectionIndex = -1;
  if (DEPENDENCY_TABLES.has(parts[0]!.value)) sectionIndex = 0;
  else if (parts[0]!.value === 'target') {
    sectionIndex = parts.findIndex((part, index) => index >= 2 && DEPENDENCY_TABLES.has(part.value));
  }
  if (sectionIndex < 0) return undefined;
  const section = parts[sectionIndex]!.value;
  const scope = sectionIndex === 0
    ? section
    : `target:${parts.slice(1, sectionIndex).map((part) => part.value).join('.')}/${section}`;
  return { scope, dependencyIndex: parts.length > sectionIndex + 1 ? sectionIndex + 1 : undefined };
}

/** Extracts direct Cargo dependencies without evaluating a workspace or running Cargo. */
export function extractCargoDependencies(text: string): DependencyEntry[] {
  const out: DependencyEntry[] = [];
  const seen = new Set<string>();
  let activeScope: string | undefined;
  let continuationDepth = 0;

  const add = (part: KeyPart, scope: string): void => {
    const id = `${scope}\0${part.value}`;
    if (seen.has(id)) return;
    seen.add(id);
    out.push(dependencyEntry(part.value, scope, part.range));
  };

  for (const line of splitLines(text)) {
    const content = contentBeforeComment(line.text);
    if (continuationDepth > 0) {
      continuationDepth = Math.max(0, continuationDepth + nestingDelta(content));
      continue;
    }
    const header = tableHeader(content);
    if (!header && content.trimStart().startsWith('[')) {
      activeScope = undefined;
      continue;
    }
    if (header) {
      const parts = keyParts(header.body, line.offset + header.start);
      const context = parts ? tableScope(parts) : undefined;
      activeScope = context?.dependencyIndex === undefined ? context?.scope : undefined;
      if (parts && context?.dependencyIndex !== undefined) {
        const dependency = parts[context.dependencyIndex];
        if (dependency) add(dependency, context.scope);
      }
      continue;
    }
    if (!activeScope) continue;
    const equals = unquotedIndex(content, '=');
    if (equals < 0) continue;
    const rawKey = content.slice(0, equals);
    const leading = rawKey.length - rawKey.trimStart().length;
    const parts = keyParts(rawKey.trim(), line.offset + leading);
    if (parts?.[0]) add(parts[0], activeScope);
    continuationDepth = Math.max(0, nestingDelta(content.slice(equals + 1)));
  }
  return out;
}
