import { parseTree } from 'jsonc-parser';
import type { DepEntry, DepSection } from './model';

const DEP_SECTIONS: readonly DepSection[] = [
  'dependencies',
  'devDependencies',
  'peerDependencies',
  'optionalDependencies',
];

function isDepSection(v: unknown): v is DepSection {
  return typeof v === 'string' && (DEP_SECTIONS as readonly string[]).includes(v);
}

/** Extract dependency names + key positions from a package.json text (jsonc tolerant). */
export function extractDeps(text: string): DepEntry[] {
  const root = parseTree(text, [], { allowTrailingComma: true });
  if (!root || root.type !== 'object') return [];
  const out: DepEntry[] = [];
  for (const prop of root.children ?? []) {
    if (prop.type !== 'property') continue;
    const keyNode = prop.children?.[0];
    const valNode = prop.children?.[1];
    if (!keyNode || !isDepSection(keyNode.value)) continue;
    if (!valNode || valNode.type !== 'object') continue;
    const section = keyNode.value;
    for (const entry of valNode.children ?? []) {
      const k = entry.children?.[0];
      if (k && typeof k.value === 'string') {
        out.push({ name: k.value, section, keyOffset: k.offset, keyLength: k.length });
      }
    }
  }
  return out;
}

/** The dependency whose key range contains the given offset (quotes included). */
export function depAtOffset(deps: readonly DepEntry[], offset: number): DepEntry | undefined {
  return deps.find((d) => offset >= d.keyOffset && offset <= d.keyOffset + d.keyLength);
}
