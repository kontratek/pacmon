import { describe, expect, it } from 'vitest';
import { classifyStrayHeading, closestAgentKey, lintNotes, type LintFinding } from '../../core/lint';
import { parseNotes } from '../../core/parseNotes';
import { insertSectionIntoText, replaceSectionBodyInText } from '../../core/serialize';

const deps = ['express', '@scope/util', 'lodash'];
const HEAD = ['---', 'format: dependency-notes/1', '---', '', '# Dependency Notes', ''];

function of(text: string, kinds: readonly LintFinding['kind'][], names: readonly string[] = deps): LintFinding[] {
  return lintNotes(parseNotes(text), names).filter((f) => kinds.includes(f.kind));
}

describe('lintNotes — headings of dependencies', () => {
  it('flags wrong heading level only for known dependency names', () => {
    const f = of([...HEAD, '### express', 'x', '', '#### random-thing', 'y'].join('\n'), ['wrongHeadingLevel']);
    expect(f).toEqual([{ kind: 'wrongHeadingLevel', line: 6, name: 'express', level: 3 }]);
  });

  it('flags a title-level heading that is actually a dependency', () => {
    expect(of('# lodash\nbody', ['wrongHeadingLevel'])).toEqual([{ kind: 'wrongHeadingLevel', line: 0, name: 'lodash', level: 1 }]);
  });

  it('flags ##name (missing space) for known deps', () => {
    expect(of('---\nf: 1\n---\n##express\nx', ['missingSpaceAfterHashes'])).toEqual([
      { kind: 'missingSpaceAfterHashes', line: 3, name: 'express' },
    ]);
  });

  it('ignores fenced code', () => {
    expect(of([...HEAD, '## express', '', '```', '### express', '# not a title', '```'].join('\n'), ['wrongHeadingLevel', 'extraTitle', 'strayHeading'])).toEqual([]);
  });
});

describe('lintNotes — frontmatter and title', () => {
  it('accepts v1 and validates the v2 ecosystem field', () => {
    expect(of('## express\nx', ['missingFrontmatter'])).toHaveLength(1);
    expect(of('---\nformat: dependency-notes/1\n---\n## express\nx', ['missingFrontmatter', 'unknownFormat'])).toEqual([]);
    expect(of('---\nlang: en\nformat: dependency-notes/2\n---\n# Dependency Notes\n', ['missingEcosystem'])).toEqual([
      { kind: 'missingEcosystem', line: 0 },
    ]);
    expect(of('---\nformat: dependency-notes/3\n---\n# Dependency Notes\n', ['unknownFormat'])).toEqual([
      { kind: 'unknownFormat', line: 1, version: 'dependency-notes/3' },
    ]);
    expect(lintNotes(
      parseNotes('---\nformat: dependency-notes/2\necosystem: cargo\n---\n# Dependency Notes\n'),
      [],
      'maven',
    ).filter((finding) => finding.kind === 'wrongEcosystem')).toEqual([
      { kind: 'wrongEcosystem', line: 0, actual: 'cargo', expected: 'maven' },
    ]);
  });

  it('wants exactly one title, worded "# Dependency Notes"', () => {
    expect(of(HEAD.join('\n'), ['missingTitle', 'wrongTitle', 'extraTitle'])).toEqual([]);
    expect(of('---\nformat: dependency-notes/1\n---\n\n## express\nx', ['missingTitle'])).toEqual([{ kind: 'missingTitle', line: 3 }]);
    expect(of('---\nformat: dependency-notes/1\n---\n\n# My Deps\n', ['wrongTitle'])).toEqual([{ kind: 'wrongTitle', line: 4, text: 'My Deps' }]);
    expect(of([...HEAD, '## express', 'x', '', '# Second'].join('\n'), ['extraTitle'])).toEqual([{ kind: 'extraTitle', line: 9, text: 'Second' }]);
  });
});

describe('lintNotes — headings inside a section', () => {
  const sectionWith = (...body: string[]): string => [...HEAD, 'Intro text.', '', '### Intro headings are free', '', '## express', '', ...body].join('\n');

  it('allows only ### Agent notes; everything else is a stray heading, classified by what it meant', () => {
    const f = of(
      sectionWith('text', '### Known quirks', '#### Deeper', '### Agent Note', '### AI notes', '### Agent notes', '- purpose: p', '### Generated', '- x: 1'),
      ['strayHeading'],
    );
    expect(f).toEqual([
      { kind: 'strayHeading', line: 13, text: 'Known quirks', level: 3 },
      { kind: 'strayHeading', line: 14, text: 'Deeper', level: 4 },
      { kind: 'strayHeading', line: 15, text: 'Agent Note', level: 3, meant: 'agent-notes' },
      { kind: 'strayHeading', line: 16, text: 'AI notes', level: 3, meant: 'agent-notes' },
      { kind: 'strayHeading', line: 19, text: 'Generated', level: 3, meant: 'generated' },
    ]);
  });

  it('does not double-flag a dependency name under the wrong level — not even as the title', () => {
    expect(of(sectionWith('### lodash'), ['strayHeading'])).toEqual([]);
    expect(of(sectionWith('### lodash'), ['wrongHeadingLevel'])).toHaveLength(1);
    expect(of('# lodash\nbody', ['wrongTitle'])).toEqual([]);
  });

  it('classifies stray headings', () => {
    expect(classifyStrayHeading('Agent Note')).toBe('agent-notes');
    expect(classifyStrayHeading('agents notes')).toBe('agent-notes');
    expect(classifyStrayHeading('AI notes')).toBe('agent-notes');
    expect(classifyStrayHeading('Generated')).toBe('generated');
    expect(classifyStrayHeading('Known quirks')).toBeUndefined();
    expect(classifyStrayHeading('Notes')).toBeUndefined();
  });
});

describe('agent block lint', () => {
  // Line 8 is human text that LOOKS like a field; the agent block starts at line 12.
  const file = (agent: string): ReturnType<typeof parseNotes> =>
    parseNotes([...HEAD, '## express', '', '- foo: bar (human text, never checked)', '', '### Agent notes', '', ...agent.split('\n'), ''].join('\n'));
  const agentFindings = (agent: string, names: readonly string[] = deps): LintFinding[] =>
    lintNotes(file(agent), names).filter(
      (f) =>
        f.kind === 'unknownAgentKey' || f.kind === 'emptyAgentValue' || f.kind === 'badAgentValue' || f.kind === 'removedButPresent',
    );

  it('accepts every vocabulary key in any case, prose lines, bare URLs and the note catch-all', () => {
    expect(
      agentFindings('- purpose: p\n- Note: free text\n- BUMP-WITH: zod\nplain prose here\n- 2026-03 not a key\n- https://example.com/changelog'),
    ).toEqual([]);
  });

  it('flags an unknown key with its line, key and value — and no guess for gibberish', () => {
    expect(agentFindings('- purpose: p\n- dafdsf: fdsaf')).toEqual([
      { kind: 'unknownAgentKey', line: 13, key: 'dafdsf', value: 'fdsaf', span: { start: 2, end: 8 } },
    ]);
  });

  it('suggests the field a misspelling meant', () => {
    expect(agentFindings('- contraint: x\n- bump_with: zod\n- notes: y\n- Verfied: 1.0')).toEqual([
      { kind: 'unknownAgentKey', line: 12, key: 'contraint', value: 'x', span: { start: 2, end: 11 }, suggestion: 'constraint' },
      { kind: 'unknownAgentKey', line: 13, key: 'bump_with', value: 'zod', span: { start: 2, end: 11 }, suggestion: 'bump-with' },
      { kind: 'unknownAgentKey', line: 14, key: 'notes', value: 'y', span: { start: 2, end: 7 }, suggestion: 'note' },
      { kind: 'unknownAgentKey', line: 15, key: 'verfied', value: '1.0', span: { start: 2, end: 9 }, suggestion: 'verified' },
    ]);
    expect(closestAgentKey('why')).toBeUndefined();
    expect(closestAgentKey('caution')).toBeUndefined();
    expect(closestAgentKey('owner')).toBe('owner');
  });

  it('flags empty and dash-only values', () => {
    expect(agentFindings('- remove-when: —\n- owner:').map((f) => f.kind)).toEqual(['emptyAgentValue', 'emptyAgentValue']);
  });

  it('checks the enumerated values and the version shape', () => {
    expect(
      agentFindings('- runtime: server, client\n- exposure: internal\n- status: removal-planned\n- status: dead\n- verified: v4.18.2'),
    ).toEqual([]);
    const bad = agentFindings('- runtime: browser\n- exposure: public\n- status: gone\n- verified: latest');
    expect(bad.map((f) => f.kind)).toEqual(['badAgentValue', 'badAgentValue', 'badAgentValue', 'badAgentValue']);
    // The value is what is underlined, not the key.
    expect(bad[0]).toMatchObject({
      key: 'runtime',
      value: 'browser',
      expected: 'one of server | client | build | dev | deploy',
      span: { start: 11, end: 18 },
    });
  });

  it('flags status: removed on a package that is still in package.json — and only then', () => {
    expect(agentFindings('- status: removed 2026-06 — replaced')).toEqual([
      { kind: 'removedButPresent', line: 12, name: 'express', span: { start: 10, end: 36 } },
    ]);
    // express is not a dependency here: a legitimately removed package.
    expect(agentFindings('- status: removed 2026-06 — replaced', ['lodash'])).toEqual([]);
    // Unknown dependencies (no package.json): no verdict.
    expect(agentFindings('- status: removed 2026-06 — replaced', [])).toEqual([]);
  });

  it('never looks at the human text, nor at fenced code inside the block', () => {
    expect(agentFindings('```\n- weird: thing\n```')).toEqual([]);
    const plain = parseNotes('## express\n\n- weird: thing\n');
    expect(lintNotes(plain, deps).filter((f) => f.kind === 'unknownAgentKey')).toEqual([]);
  });
});

describe('insertSectionIntoText', () => {
  it('inserts at sorted position with body and single blank separation', () => {
    const text = '## alpha\n\na\n\n## charlie\n\nc\n';
    const out = insertSectionIntoText(text, 'bravo', 'why: because');
    expect(out.indexOf('## bravo')).toBeGreaterThan(out.indexOf('## alpha'));
    expect(out.indexOf('## bravo')).toBeLessThan(out.indexOf('## charlie'));
    expect(out).toContain('## bravo\n\nwhy: because\n');
    expect(out).not.toContain('\n\n\n');
  });

  it('appends at end when name sorts last or file is unsorted', () => {
    const sorted = insertSectionIntoText('## alpha\n\na\n', 'zulu', 'z');
    expect(sorted.trimEnd().endsWith('z')).toBe(true);
    expect(sorted).toContain('a\n\n## zulu\n\nz\n');
    const unsorted = insertSectionIntoText('## zulu\nz\n## alpha\na\n', 'bravo', 'b');
    expect(unsorted.indexOf('## bravo')).toBeGreaterThan(unsorted.indexOf('## alpha'));
  });

  it('handles files without trailing newline and preserves CRLF', () => {
    const out = insertSectionIntoText('## alpha\r\n\r\na', 'zulu', 'z');
    expect(out).toContain('a\r\n\r\n## zulu\r\n\r\nz\r\n');
  });
});

describe('replaceSectionBodyInText', () => {
  const text = '## alpha\n\nold body\n\n## beta\n\nb-note\n';

  it('replaces a middle section body, keeping separation', () => {
    const out = replaceSectionBodyInText(text, 'alpha', 'new body');
    expect(out).toBe('## alpha\n\nnew body\n\n## beta\n\nb-note\n');
  });

  it('replaces the last section body', () => {
    const out = replaceSectionBodyInText(text, 'beta', 'changed');
    expect(out).toBe('## alpha\n\nold body\n\n## beta\n\nchanged\n');
  });

  it('matches tolerantly and returns input unchanged for unknown sections', () => {
    expect(replaceSectionBodyInText(text, 'ALPHA', 'x')).toContain('## alpha\n\nx\n');
    expect(replaceSectionBodyInText(text, 'ghost', 'x')).toBe(text);
  });

  it('preserves CRLF', () => {
    const crlf = '## a\r\n\r\nold\r\n';
    expect(replaceSectionBodyInText(crlf, 'a', 'new')).toBe('## a\r\n\r\nnew\r\n');
  });
});
