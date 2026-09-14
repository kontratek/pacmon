import { describe, expect, it } from 'vitest';
import { parseNotes } from '../../core/parseNotes';
import {
  composeSectionBody,
  insertionLine,
  normalizeText,
  replaceHumanBodyInText,
  replaceSectionBodyInText,
  replaceSectionLayersInText,
  sectionSnippet,
} from '../../core/serialize';
import { AI_FORMAT_COMMENT_LINES } from '../../core/template';

describe('normalize/serialize', () => {
  it('sorts sections, canonicalizes headings, adds frontmatter, header and title', () => {
    const input = '##   zulu  \nz-note\n## alpha\na-note\n';
    const out = normalizeText(input);
    expect(out).toBe(
      [
        '---',
        'format: dependency-notes/1',
        'lang: en',
        '---',
        '',
        ...AI_FORMAT_COMMENT_LINES,
        '',
        '# Dependency Notes',
        '',
        '## alpha',
        '',
        'a-note',
        '',
        '## zulu',
        '',
        'z-note',
        '',
      ].join('\n'),
    );
  });

  it('is idempotent', () => {
    const messy = '## b\n\n\nbb\n\n## a\naa\n\nintro-less';
    const once = normalizeText(messy);
    expect(normalizeText(once)).toBe(once);
  });

  it('keeps unknown frontmatter keys, adds the missing format keys, owns the header and the title, keeps the intro', () => {
    const input = [
      '---',
      'format: dependency-notes/1',
      'extra: kept',
      '---',
      '',
      '<!-- custom comment -->',
      '',
      '# My Deps',
      '',
      'Intro paragraph.',
      '',
      '## a',
      'x',
    ].join('\n');
    const out = normalizeText(input);
    const fm = out.slice(0, out.indexOf('\n---\n', 4) + 5);
    expect(fm).toBe('---\nformat: dependency-notes/1\nextra: kept\nlang: en\n---\n');
    // The header comment is the format's, not the file's: a custom one is replaced.
    expect(out).not.toContain('<!-- custom comment -->');
    expect(out).toContain(AI_FORMAT_COMMENT_LINES[0]!);
    // The title is the format's too: one `#` heading, always `# Dependency Notes`.
    expect(out).toContain('\n# Dependency Notes\n\nIntro paragraph.');
    expect(out).not.toContain('# My Deps');
  });

  it('preserves CRLF and scoped ordering (@scope first)', () => {
    const input = '## zebra\r\nz\r\n## @a/pkg\r\ns\r\n';
    const out = normalizeText(input);
    expect(out.indexOf('## @a/pkg')).toBeLessThan(out.indexOf('## zebra'));
    expect(out).toContain('\r\n');
    expect(out.includes('\n## zebra')).toBe(true);
  });

  it('does not touch section bodies, agent blocks included (inner blank lines kept)', () => {
    const input = '## a\n\nline1\n\nline2\n\n### Agent notes\n\n- purpose: p\n';
    const out = normalizeText(input);
    expect(out).toContain('line1\n\nline2\n\n### Agent notes\n\n- purpose: p');
  });

  it('keeps duplicate sections (stable order)', () => {
    const out = normalizeText('## a\nfirst\n## a\nsecond');
    const first = out.indexOf('first');
    const second = out.indexOf('second');
    expect(first).toBeGreaterThan(-1);
    expect(second).toBeGreaterThan(first);
  });
});

describe('insertionLine', () => {
  it('finds the sorted position in a sorted file', () => {
    const m = parseNotes('## alpha\na\n## charlie\nc\n');
    expect(insertionLine(m, 'bravo')).toBe(2);
    expect(insertionLine(m, 'zulu')).toBeNull();
  });

  it('returns null (append) for unsorted files', () => {
    const m = parseNotes('## zulu\nz\n## alpha\na\n');
    expect(insertionLine(m, 'bravo')).toBeNull();
  });

  it('sectionSnippet produces heading plus empty body', () => {
    expect(sectionSnippet(' pkg ', '\n')).toBe('## pkg\n\n');
  });
});

describe('composeSectionBody', () => {
  it('lays out human, agent (under its heading) and generated (with its own heading)', () => {
    expect(composeSectionBody({ human: 'H1\nH2', agent: '- purpose: p', generated: '### Generated\n- installed: 1' })).toBe(
      'H1\nH2\n\n### Agent notes\n\n- purpose: p\n\n### Generated\n- installed: 1',
    );
  });

  it('omits the agent heading when the agent text is empty, and trims', () => {
    expect(composeSectionBody({ human: '  only human  \n', agent: '  ' })).toBe('only human');
    expect(composeSectionBody({ human: '', agent: '- purpose: p' })).toBe('### Agent notes\n\n- purpose: p');
    expect(composeSectionBody({ human: '' })).toBe('');
  });
});

describe('replaceSectionBodyInText', () => {
  const text = '## alpha\n\nold body\n\n## beta\n\nb-note\n';

  it('replaces a middle section body, keeping separation', () => {
    expect(replaceSectionBodyInText(text, 'alpha', 'new body')).toBe('## alpha\n\nnew body\n\n## beta\n\nb-note\n');
  });

  it('replaces the last section body', () => {
    expect(replaceSectionBodyInText(text, 'beta', 'changed')).toBe('## alpha\n\nold body\n\n## beta\n\nchanged\n');
  });

  it('matches tolerantly and returns input unchanged for unknown sections', () => {
    expect(replaceSectionBodyInText(text, 'ALPHA', 'x')).toContain('## alpha\n\nx\n');
    expect(replaceSectionBodyInText(text, 'ghost', 'x')).toBe(text);
  });
});

describe('replaceHumanBodyInText', () => {
  const layered = [
    '## alpha',
    '',
    'old human',
    '',
    '### Agent notes',
    '',
    '- purpose: p',
    '- constraint: c',
    '',
    '## beta',
    '',
    'b-note',
    '',
    '### Agent notes',
    '',
    '- purpose: q',
    '',
    '### Generated',
    '<!-- generated:start -->',
    '- installed: 1',
    '<!-- generated:end -->',
    '',
  ].join('\n');

  it('replaces only the human text of a middle section; the agent block survives verbatim', () => {
    const out = replaceHumanBodyInText(layered, 'alpha', 'new human\nsecond line');
    expect(out).toContain('## alpha\n\nnew human\nsecond line\n\n### Agent notes\n\n- purpose: p\n- constraint: c\n\n## beta');
    expect(out).not.toContain('old human');
  });

  it('keeps the agent block and the generated tail of the last section', () => {
    const out = replaceHumanBodyInText(layered, 'beta', 'changed');
    expect(out).toContain('## beta\n\nchanged\n\n### Agent notes\n\n- purpose: q\n\n### Generated\n<!-- generated:start -->\n- installed: 1\n<!-- generated:end -->\n');
    expect(out).not.toContain('b-note');
  });

  it('can empty the human text without touching the agent block', () => {
    const out = replaceHumanBodyInText(layered, 'alpha', '');
    expect(out).toContain('## alpha\n\n### Agent notes\n\n- purpose: p');
  });

  it('behaves like replaceSectionBodyInText when there is no agent block', () => {
    const plain = '## alpha\n\nold body\n\n## beta\n\nb-note\n';
    expect(replaceHumanBodyInText(plain, 'alpha', 'new body')).toBe(replaceSectionBodyInText(plain, 'alpha', 'new body'));
    expect(replaceHumanBodyInText(plain, 'beta', 'changed')).toBe(replaceSectionBodyInText(plain, 'beta', 'changed'));
  });

  it('returns input unchanged for unknown sections and preserves CRLF', () => {
    expect(replaceHumanBodyInText(layered, 'ghost', 'x')).toBe(layered);
    const crlf = '## a\r\n\r\nold\r\n\r\n### Agent notes\r\n\r\n- purpose: p\r\n';
    expect(replaceHumanBodyInText(crlf, 'a', 'new')).toBe('## a\r\n\r\nnew\r\n\r\n### Agent notes\r\n\r\n- purpose: p\r\n');
  });
});

describe('replaceSectionLayersInText', () => {
  const text = '## a\n\nh\n\n### Agent notes\n\n- purpose: p\n\n### Generated\n- installed: 1\n\n## b\n\nx\n';

  it('rewrites human and agent together and carries the generated block over', () => {
    const out = replaceSectionLayersInText(text, 'a', { human: 'H', agent: '- purpose: P\n- verify: v' });
    expect(out).toBe('## a\n\nH\n\n### Agent notes\n\n- purpose: P\n- verify: v\n\n### Generated\n- installed: 1\n\n## b\n\nx\n');
  });

  it('drops the agent heading when the agent text is emptied', () => {
    const out = replaceSectionLayersInText(text, 'a', { human: 'H', agent: '' });
    expect(out).toBe('## a\n\nH\n\n### Generated\n- installed: 1\n\n## b\n\nx\n');
  });
});
