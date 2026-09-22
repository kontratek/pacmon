import type { ManifestKind } from './model';

/** Tolerant wrapper removal shared by every notes format. */
export function stripName(raw: string): string {
  let s = raw.trim();
  // Strip a single pair of surrounding backticks or quotes ("## `express`" tolerance).
  const m = /^(`|"|')(.*)\1$/.exec(s);
  if (m && m[2] !== undefined) s = m[2].trim();
  return s;
}

/** Legacy npm matching is case-insensitive. */
export function normalizeName(raw: string): string {
  return stripName(raw).toLowerCase();
}

export function normalizeNameForEcosystem(raw: string, ecosystem?: ManifestKind): string {
  return ecosystem && ecosystem !== 'npm' ? stripName(raw) : normalizeName(raw);
}

export function namesEqual(a: string, b: string): boolean {
  return normalizeName(a) === normalizeName(b);
}
