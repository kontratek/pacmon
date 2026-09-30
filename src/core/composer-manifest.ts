import { parseTree, type Node as JsonNode } from 'jsonc-parser';
import { dependencyEntry } from './dependency';
import type { DependencyEntry, SourceRange } from './model';

const DEPENDENCY_SECTIONS = ['require', 'require-dev'] as const;
const PACKAGE_NAME = /^[A-Za-z0-9][A-Za-z0-9_.-]*\/[A-Za-z0-9][A-Za-z0-9_.-]*$/;
const PLATFORM_PACKAGE = /^(?:php(?:-[A-Za-z0-9_.-]+)?|hhvm|ext-[A-Za-z0-9_.-]+|lib-[A-Za-z0-9_.-]+|composer(?:-(?:plugin|runtime)-api)?)$/i;

function property(node: JsonNode, name: string): JsonNode | undefined {
  if (node.type !== 'object') return undefined;
  for (const child of node.children ?? []) {
    if (child.type !== 'property') continue;
    const key = child.children?.[0];
    if (key?.value === name) return child.children?.[1];
  }
  return undefined;
}

function stringContentRange(node: JsonNode): SourceRange {
  return node.type === 'string' && node.length >= 2
    ? { offset: node.offset + 1, length: node.length - 2 }
    : { offset: node.offset, length: node.length };
}

function isDependencyName(value: string): boolean {
  return PACKAGE_NAME.test(value) || PLATFORM_PACKAGE.test(value);
}

function dependenciesFrom(node: JsonNode | undefined, scope: string): DependencyEntry[] {
  if (node?.type !== 'object') return [];
  const out: DependencyEntry[] = [];
  for (const child of node.children ?? []) {
    if (child.type !== 'property') continue;
    const key = child.children?.[0];
    const value = child.children?.[1];
    if (key?.type !== 'string' || typeof key.value !== 'string' || value?.type !== 'string') continue;
    if (!isDependencyName(key.value)) continue;
    const range = stringContentRange(key);
    out.push(dependencyEntry(
      key.value.toLowerCase(),
      scope,
      range,
      [range],
      key.value,
      { offset: key.offset, length: 1 },
    ));
  }
  return out;
}

/** Reads direct Composer requirements without invoking PHP or Composer. */
export function extractComposerDependencies(text: string): DependencyEntry[] {
  const root = parseTree(text, [], { allowTrailingComma: true });
  if (!root || root.type !== 'object') return [];
  return DEPENDENCY_SECTIONS.flatMap((section) => dependenciesFrom(property(root, section), section));
}
