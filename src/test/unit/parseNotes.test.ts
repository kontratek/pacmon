import { describe, expect, it } from 'vitest';
import { findSection, parseNotes, sectionBody } from '../../core/parseNotes';

const sample = [
  '---',
  'format: pacmon/1',
  'lang: en',
  '---',
  '',
  '<!-- header -->',
  '',
  '# Dependencies',
  '',
  'Some intro text.',
  '',
  '## express',
  '',
  'HTTP API layer.',
  'Do not upgrade to v5.',
  '',
  '### Agent notes',
  '',
  '- purpose: HTTP framework',
  '- constraint: stay on ^4',
  '',
  '## @scope/util',
  '',
  'Internal helpers.',
].join('\n');

describe('parseNotes', () => {
  it('parses frontmatter keys, comment, title, intro and sections', () => {
    const m = parseNotes(sample);
    expect(m.frontmatter?.formatVersion).toBe('pacmon/1');
    expect(m.frontmatter?.lang).toBe('en');
    expect(m.aiComment).toBeDefined();
    expect(m.titleLine).toBe(7);
    expect(m.intro).toEqual({ startLine: 8, endLine: 10 });
    expect(m.sections.map((s) => s.name)).toEqual(['express', '@scope/util']);
    expect(m.problems).toEqual([]);
  });

  it('keeps unknown frontmatter keys as lines and ignores empty values', () => {
    const m = parseNotes('---\nformat: pacmon/1\nextra: kept\nlang:\n---\n## a\nx');
    expect(m.frontmatter?.formatVersion).toBe('pacmon/1');
    expect(m.frontmatter?.lang).toBeUndefined();
    expect(m.lines[2]).toBe('extra: kept');
  });

  it('marks the agent-notes boundary and keeps the whole body available', () => {
    const m = parseNotes(sample);
    const express = findSection(m, 'express')!;
    expect(express.agentHeadingLine).toBe(16);
    expect(express.generatedHeadingLine).toBeUndefined();
    expect(sectionBody(m, express)).toBe(
      'HTTP API layer.\nDo not upgrade to v5.\n\n### Agent notes\n\n- purpose: HTTP framework\n- constraint: stay on ^4',
    );
    expect(findSection(m, '@scope/util')!.agentHeadingLine).toBeUndefined();
  });

  it('marks the reserved Generated heading; an agent heading after it is ignored', () => {
    const m = parseNotes('## a\n\nhuman\n\n### Agent notes\n\n- purpose: p\n\n### Generated\n\n- installed: 1\n\n### Agent notes\n\n- late: x');
    const a = m.sections[0]!;
    expect(a.agentHeadingLine).toBe(4);
    expect(a.generatedHeadingLine).toBe(8);
    // The stray second heading is just body text inside the generated block.
    expect(sectionBody(m, a)).toContain('- late: x');
  });

  it('matches the reserved headings case-insensitively and takes the first one', () => {
    const m = parseNotes('## a\n\n### AGENT NOTES\n\n- purpose: first\n\n### Agent Notes\n\n- purpose: second');
    expect(m.sections[0]!.agentHeadingLine).toBe(2);
  });

  it('ignores reserved headings inside fenced code', () => {
    const m = parseNotes('## a\n\n```md\n### Agent notes\n```\n\nprose');
    expect(m.sections[0]!.agentHeadingLine).toBeUndefined();
  });

  it('does not start the agent layer before a section exists', () => {
    const m = parseNotes('# Dependencies\n\n### Agent notes\n\n## a\nx');
    expect(m.sections).toHaveLength(1);
    expect(m.sections[0]!.agentHeadingLine).toBeUndefined();
  });

  it('matches names tolerantly (case, backticks, whitespace)', () => {
    const m = parseNotes('## `Express`  \n\nnote');
    expect(findSection(m, 'express')?.name).toBe('`Express`');
  });

  it('ignores ## lines inside fenced code blocks', () => {
    const text = ['## real', '', '```md', '## fake-in-fence', '```', 'after fence', '~~~', '## also-fake', '~~~'].join('\n');
    const m = parseNotes(text);
    expect(m.sections.map((s) => s.name)).toEqual(['real']);
    const body = sectionBody(m, m.sections[0]!);
    expect(body).toContain('## fake-in-fence');
    expect(body).toContain('## also-fake');
  });

  it('does not treat other ### headings as sections or layers', () => {
    const m = parseNotes('## pkg\n\n### sub-heading inside body\ntext');
    expect(m.sections).toHaveLength(1);
    expect(m.sections[0]!.agentHeadingLine).toBeUndefined();
    expect(sectionBody(m, m.sections[0]!)).toContain('### sub-heading');
  });

  it('detects CRLF and BOM', () => {
    const m = parseNotes('﻿## a\r\n\r\nbody\r\n');
    expect(m.hadBom).toBe(true);
    expect(m.eol).toBe('\r\n');
    expect(m.sections[0]?.name).toBe('a');
  });

  it('flags duplicate sections but keeps both', () => {
    const m = parseNotes('## a\nx\n## b\ny\n## a\nz');
    expect(m.sections).toHaveLength(3);
    expect(m.problems).toEqual([{ kind: 'duplicateSection', name: 'a', line: 4, firstLine: 0 }]);
  });

  it('handles empty file and file without frontmatter/title', () => {
    expect(parseNotes('').sections).toEqual([]);
    const m = parseNotes('## only\nbody');
    expect(m.frontmatter).toBeUndefined();
    expect(m.titleLine).toBeUndefined();
    expect(m.sections).toHaveLength(1);
  });

  it('treats unterminated leading comment as content, not aiComment', () => {
    const m = parseNotes('<!-- oops\n## a\nbody');
    expect(m.aiComment).toBeUndefined();
    expect(m.sections.map((s) => s.name)).toEqual(['a']);
  });
});
