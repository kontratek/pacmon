import { describe, expect, it } from 'vitest';
import { analyze } from '../../core/analyze';
import { extractDeps } from '../../core/packageJson';
import { parseNotes } from '../../core/parseNotes';

describe('analyze', () => {
  const deps = extractDeps(
    JSON.stringify({ dependencies: { a: '1', b: '1' }, devDependencies: { c: '1' } }),
  );

  it('splits documented/undocumented and finds orphans', () => {
    const notes = parseNotes('## a\nnote-a\n## ghost\nboo');
    const res = analyze(deps, notes);
    expect(res.documented.map((d) => d.name)).toEqual(['a']);
    expect(res.undocumented.map((d) => d.name).sort()).toEqual(['b', 'c']);
    expect(res.orphans.map((o) => o.name)).toEqual(['ghost']);
  });

  it('handles missing notes file', () => {
    const res = analyze(deps, undefined);
    expect(res.documented).toEqual([]);
    expect(res.undocumented).toHaveLength(3);
    expect(res.orphans).toEqual([]);
    expect(res.removed).toEqual([]);
  });

  it('does not count a heading with nothing under it as documentation', () => {
    const notes = parseNotes(['## a', '', '## b', '', '### Agent notes', '', '## c', 'real note'].join('\n'));
    const res = analyze(deps, notes);
    expect(res.documented.map((d) => d.name)).toEqual(['c']);
    expect(res.undocumented.map((d) => d.name).sort()).toEqual(['a', 'b']);
    expect(res.byDep.has('a')).toBe(false);
  });

  it('keeps a removed-status section out of the orphans', () => {
    const notes = parseNotes(
      ['## a', 'note-a', '## ghost', 'boo', '## old', '', '### Agent notes', '', '- status: removed 2026-06 — replaced by a'].join('\n'),
    );
    const res = analyze(deps, notes);
    expect(res.orphans.map((o) => o.name)).toEqual(['ghost']);
    expect(res.removed.map((o) => o.name)).toEqual(['old']);
    expect(res.documented.map((d) => d.name)).toEqual(['a']);
  });
});
