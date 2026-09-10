/** Tolerant name normalization: read leniently, write canonically. */
export function normalizeName(raw: string): string {
  let s = raw.trim();
  // Strip a single pair of surrounding backticks or quotes ("## `express`" tolerance).
  const m = /^(`|"|')(.*)\1$/.exec(s);
  if (m && m[2] !== undefined) s = m[2].trim();
  return s.toLowerCase();
}

export function namesEqual(a: string, b: string): boolean {
  return normalizeName(a) === normalizeName(b);
}
