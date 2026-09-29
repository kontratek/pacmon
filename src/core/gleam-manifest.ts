import { dependencyEntry } from './dependency';
import type { DependencyEntry, SourceRange } from './model';

interface ParsedKey {
  value: string;
  range: SourceRange;
}

const DEPENDENCY_SCOPES = new Set(['dependencies', 'dev_dependencies']);

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
  for (let index = 0; index < line.length; index++) {
    const char = line[index]!;
    if (quote === '"' && escaped) {
      escaped = false;
    } else if (quote === '"' && char === '\\') {
      escaped = true;
    } else if (quote) {
      if (char === quote) quote = undefined;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === '#') {
      return line.slice(0, index);
    }
  }
  return line;
}

function unquotedIndex(text: string, wanted: string): number {
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let index = 0; index < text.length; index++) {
    const char = text[index]!;
    if (quote === '"' && escaped) {
      escaped = false;
    } else if (quote === '"' && char === '\\') {
      escaped = true;
    } else if (quote) {
      if (char === quote) quote = undefined;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === wanted) {
      return index;
    }
  }
  return -1;
}

function nestingDelta(text: string): number {
  let delta = 0;
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (const char of text) {
    if (quote === '"' && escaped) {
      escaped = false;
    } else if (quote === '"' && char === '\\') {
      escaped = true;
    } else if (quote) {
      if (char === quote) quote = undefined;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === '{' || char === '[') {
      delta++;
    } else if (char === '}' || char === ']') {
      delta--;
    }
  }
  return delta;
}

function decodeKey(raw: string): string | undefined {
  if (raw.startsWith('"')) {
    if (!raw.endsWith('"')) return undefined;
    try {
      return JSON.parse(raw) as string;
    } catch {
      return raw.slice(1, -1);
    }
  }
  if (raw.startsWith("'")) return raw.endsWith("'") ? raw.slice(1, -1) : undefined;
  if (!/^[A-Za-z0-9_-]+$/.test(raw)) return undefined;
  return raw;
}

function dependencyKey(raw: string, absoluteOffset: number): ParsedKey | undefined {
  const leading = raw.length - raw.trimStart().length;
  const token = raw.trim();
  if (!token) return undefined;
  const value = decodeKey(token);
  if (value === undefined) return undefined;
  return { value, range: { offset: absoluteOffset + leading, length: token.length } };
}

function tableName(content: string): string | undefined {
  const match = /^\s*\[\s*([^\]]+)\s*\]\s*$/.exec(content);
  return match?.[1]?.trim();
}

/** Extracts direct Gleam dependencies without invoking Gleam or resolving packages. */
export function extractGleamDependencies(text: string): DependencyEntry[] {
  const out: DependencyEntry[] = [];
  const seen = new Set<string>();
  let activeScope: string | undefined;
  let continuationDepth = 0;

  for (const line of splitLines(text)) {
    const content = contentBeforeComment(line.text);
    if (continuationDepth > 0) {
      continuationDepth = Math.max(0, continuationDepth + nestingDelta(content));
      continue;
    }

    if (content.trimStart().startsWith('[')) {
      const name = tableName(content);
      activeScope = name && DEPENDENCY_SCOPES.has(name) ? name : undefined;
      continue;
    }
    if (!activeScope) continue;

    const equals = unquotedIndex(content, '=');
    if (equals < 0) continue;
    const key = dependencyKey(content.slice(0, equals), line.offset);
    if (!key) continue;

    const id = `${activeScope}\0${key.value}`;
    if (!seen.has(id)) {
      seen.add(id);
      out.push(dependencyEntry(key.value, activeScope, key.range));
    }
    continuationDepth = Math.max(0, nestingDelta(content.slice(equals + 1)));
  }

  return out;
}
