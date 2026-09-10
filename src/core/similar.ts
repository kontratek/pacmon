/** Edit distance, for "did you mean" guesses. */
export function levenshtein(a: string, b: string): number {
  const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let diag = prev[0] ?? 0;
    prev[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const tmp = prev[j] ?? 0;
      prev[j] = Math.min(tmp + 1, (prev[j - 1] ?? 0) + 1, diag + (a[i - 1] === b[j - 1] ? 0 : 1));
      diag = tmp;
    }
  }
  return prev[b.length] ?? 0;
}

/**
 * The candidate a misspelling most likely meant, when it is close enough to
 * be sure (at most two edits, and fewer than a third of the name); undefined
 * otherwise, so gibberish gets no guess. Case-insensitive; `_` and `-` count
 * the same.
 */
export function closest(name: string, candidates: readonly string[]): string | undefined {
  const norm = (s: string): string => s.toLowerCase().replace(/_/g, '-');
  const n = norm(name);
  let best: { candidate: string; d: number } | undefined;
  for (const candidate of candidates) {
    const d = levenshtein(n, norm(candidate));
    if (!best || d < best.d) best = { candidate, d };
  }
  if (!best) return undefined;
  const limit = Math.min(2, Math.floor(Math.max(n.length, best.candidate.length) / 3));
  return best.d <= limit ? best.candidate : undefined;
}
