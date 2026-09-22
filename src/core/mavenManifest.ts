import { dependencyEntry } from './dependency';
import type { DependencyEntry, SourceRange } from './model';

interface XmlNode {
  name: string;
  openEnd: number;
  closeStart: number;
  children: XmlNode[];
}

function localName(name: string): string {
  return name.slice(name.lastIndexOf(':') + 1);
}

function parseXml(text: string): XmlNode[] {
  const roots: XmlNode[] = [];
  const stack: XmlNode[] = [];
  let cursor = 0;
  while (cursor < text.length) {
    const open = text.indexOf('<', cursor);
    if (open < 0) break;
    if (text.startsWith('<!--', open)) {
      const end = text.indexOf('-->', open + 4);
      cursor = end < 0 ? text.length : end + 3;
      continue;
    }
    if (text.startsWith('<![CDATA[', open)) {
      const end = text.indexOf(']]>', open + 9);
      cursor = end < 0 ? text.length : end + 3;
      continue;
    }
    if (text.startsWith('<?', open)) {
      const end = text.indexOf('?>', open + 2);
      cursor = end < 0 ? text.length : end + 2;
      continue;
    }
    if (text.startsWith('<!', open)) {
      const end = text.indexOf('>', open + 2);
      cursor = end < 0 ? text.length : end + 1;
      continue;
    }

    let quote: '"' | "'" | undefined;
    let end = open + 1;
    for (; end < text.length; end++) {
      const char = text[end]!;
      if (quote) {
        if (char === quote) quote = undefined;
      } else if (char === '"' || char === "'") quote = char;
      else if (char === '>') break;
    }
    if (end >= text.length) break;
    const inside = text.slice(open + 1, end).trim();
    if (inside.startsWith('/')) {
      const name = localName(inside.slice(1).trim().split(/\s/, 1)[0] ?? '');
      for (let i = stack.length - 1; i >= 0; i--) {
        if (stack[i]!.name !== name) continue;
        const node = stack[i]!;
        node.closeStart = open;
        stack.length = i;
        break;
      }
    } else {
      const selfClosing = /\/\s*$/.test(inside);
      const rawName = inside.replace(/\/\s*$/, '').split(/\s/, 1)[0] ?? '';
      if (rawName !== '') {
        const node: XmlNode = { name: localName(rawName), openEnd: end + 1, closeStart: end + 1, children: [] };
        const parent = stack[stack.length - 1];
        if (parent) parent.children.push(node);
        else roots.push(node);
        if (!selfClosing) stack.push(node);
      }
    }
    cursor = end + 1;
  }
  for (const node of stack) node.closeStart = text.length;
  return roots;
}

function child(node: XmlNode, name: string): XmlNode | undefined {
  return node.children.find((candidate) => candidate.name === name);
}

function decodeXml(value: string): string {
  return value.replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (entity, body: string) => {
    const lower = body.toLowerCase();
    if (lower === 'amp') return '&';
    if (lower === 'lt') return '<';
    if (lower === 'gt') return '>';
    if (lower === 'quot') return '"';
    if (lower === 'apos') return "'";
    const radix = lower.startsWith('#x') ? 16 : 10;
    const digits = lower.slice(radix === 16 ? 2 : 1);
    const code = Number.parseInt(digits, radix);
    return Number.isFinite(code) ? String.fromCodePoint(code) : entity;
  });
}

function valueAndRange(text: string, node: XmlNode | undefined): { value: string; range: SourceRange } | undefined {
  if (!node) return undefined;
  const raw = text.slice(node.openEnd, node.closeStart);
  const plain = raw.replace(/<!--[^]*?-->/g, '').replace(/<!\[CDATA\[([^]*?)]]>/g, '$1');
  const value = decodeXml(plain.trim());
  if (value === '') return undefined;
  const rawValueIndex = raw.indexOf(plain.trim());
  const offset = rawValueIndex < 0 ? node.openEnd : node.openEnd + rawValueIndex;
  return { value, range: { offset, length: plain.trim().length } };
}

function dependenciesFrom(
  text: string,
  dependencies: XmlNode | undefined,
  scopePrefix?: string,
): DependencyEntry[] {
  if (!dependencies) return [];
  const out: DependencyEntry[] = [];
  for (const dependency of dependencies.children.filter((node) => node.name === 'dependency')) {
    const group = valueAndRange(text, child(dependency, 'groupId'));
    const artifact = valueAndRange(text, child(dependency, 'artifactId'));
    if (!group || !artifact) continue;
    const declaredScope = valueAndRange(text, child(dependency, 'scope'))?.value ?? 'compile';
    const scope = scopePrefix ? `${scopePrefix}/${declaredScope}` : declaredScope;
    const noteKey = `${group.value}:${artifact.value}`;
    out.push(dependencyEntry(noteKey, scope, artifact.range, [group.range, artifact.range]));
  }
  return out;
}

/** Extracts direct Maven dependencies without resolving parents or invoking Maven. */
export function extractMavenDependencies(text: string): DependencyEntry[] {
  const project = parseXml(text).find((node) => node.name === 'project');
  if (!project) return [];
  const out = dependenciesFrom(text, child(project, 'dependencies'));
  const profiles = child(project, 'profiles');
  for (const profile of profiles?.children.filter((node) => node.name === 'profile') ?? []) {
    const id = valueAndRange(text, child(profile, 'id'))?.value ?? 'unnamed';
    out.push(...dependenciesFrom(text, child(profile, 'dependencies'), `profile:${id}`));
  }
  return out;
}
