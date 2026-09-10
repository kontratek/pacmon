import { describe, expect, it } from 'vitest';
import { fixAgentText, lintAgentText, rewriteUnknownKeyLine } from '../../core/fixes';

describe('rewriteUnknownKeyLine', () => {
  it('renames a misspelling and keeps gibberish as a note, indentation intact', () => {
    expect(rewriteUnknownKeyLine('- contraint: stay on ^4')).toEqual({ text: '- constraint: stay on ^4', renamedTo: 'constraint' });
    expect(rewriteUnknownKeyLine('  - bump_with: zod')).toEqual({ text: '  - bump-with: zod', renamedTo: 'bump-with' });
    expect(rewriteUnknownKeyLine('- dafdsf: fdsaf')).toEqual({ text: '- note: dafdsf: fdsaf' });
    expect(rewriteUnknownKeyLine('plain prose')).toBeUndefined();
  });
});

describe('lintAgentText / fixAgentText', () => {
  const agent = ['- purpose: p', '- dafdsf: fdsaf', '- contraint: x', '- owner:', '- runtime: browser'].join('\n');

  it('reports problems with lines relative to the agent text', () => {
    expect(lintAgentText(agent).map((p) => [p.kind, p.line, p.key])).toEqual([
      ['unknownAgentKey', 1, 'dafdsf'],
      ['unknownAgentKey', 2, 'contraint'],
      ['emptyAgentValue', 3, 'owner'],
      ['badAgentValue', 4, 'runtime'],
    ]);
    expect(lintAgentText('')).toEqual([]);
    expect(lintAgentText('- purpose: fine')).toEqual([]);
  });

  it('fixes only the unknown keys and leaves everything else alone', () => {
    expect(fixAgentText(agent)).toBe(
      ['- purpose: p', '- note: dafdsf: fdsaf', '- constraint: x', '- owner:', '- runtime: browser'].join('\n'),
    );
    expect(fixAgentText('')).toBe('');
  });
});
