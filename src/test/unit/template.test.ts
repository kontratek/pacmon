import { describe, expect, it } from 'vitest';
import { normalizeText } from '../../core/serialize';
import {
  AGENTS_REL_PATH,
  AI_FORMAT_COMMENT_LINES,
  DEFAULT_FRONTMATTER_LINES,
  NOTES_REL_PATH,
  aiInstructionsBlock,
  newNotesFileContent,
  upsertAiInstructions,
} from '../../core/template';

describe('template: frontmatter and header', () => {
  it('declares the three format keys, in order, between the dashes', () => {
    expect(DEFAULT_FRONTMATTER_LINES[0]).toBe('---');
    expect(DEFAULT_FRONTMATTER_LINES[DEFAULT_FRONTMATTER_LINES.length - 1]).toBe('---');
    expect(DEFAULT_FRONTMATTER_LINES.slice(1, -1)).toEqual([
      'format: deps-notes/1',
      'lang: en',
      'agents: .pacmon/AGENTS.md',
    ]);
  });

  it('keeps the header to three lines that only say who writes where', () => {
    expect(AI_FORMAT_COMMENT_LINES).toHaveLength(3);
    expect(AI_FORMAT_COMMENT_LINES[0]?.startsWith('<!--')).toBe(true);
    expect(AI_FORMAT_COMMENT_LINES[2]?.endsWith('-->')).toBe(true);
    const text = AI_FORMAT_COMMENT_LINES.join('\n');
    expect(text).toContain('package.json');
    expect(text).toContain('### Agent notes');
    expect(text).toContain(AGENTS_REL_PATH);
    // The old recognized bullets are gone from the format entirely.
    expect(text).not.toMatch(/\bwhy:|\bcaution:/);
  });

  it('produces a fresh file that is already canonical', () => {
    const fresh = newNotesFileContent('\n', 'a', 'x');
    expect(fresh).toContain('## a');
    expect(fresh).toContain('\nx\n');
    expect(normalizeText(fresh)).toBe(fresh);
  });
});

describe('template: pointer block for the user’s agent files', () => {
  const between = (text: string): string[] => {
    const lines = text.split('\n');
    const start = lines.findIndex((l) => l.includes('pacmon:deps-notes:start'));
    const end = lines.findIndex((l) => l.includes('pacmon:deps-notes:end'));
    return lines.slice(start + 1, end);
  };

  it('is exactly three lines and points at both files', () => {
    const inner = between(aiInstructionsBlock());
    expect(inner).toHaveLength(3);
    const text = inner.join('\n');
    expect(text).toContain(NOTES_REL_PATH);
    expect(text).toContain(AGENTS_REL_PATH);
    expect(text).toContain('### Agent notes');
    expect(text).toContain('status: removed');
  });

  it('appends the block once and replaces it on re-run', () => {
    const first = upsertAiInstructions('# AGENTS\n\nexisting\n');
    expect(first).toContain('pacmon:deps-notes:start');
    const second = upsertAiInstructions(first);
    expect(second.match(/pacmon:deps-notes:start/g)).toHaveLength(1);
    expect(second).toContain('existing');
    expect(between(second)).toHaveLength(3);
  });

  it('works on empty files', () => {
    const out = upsertAiInstructions('');
    expect(out.startsWith('<!-- pacmon:deps-notes:start -->')).toBe(true);
    expect(out.endsWith('\n')).toBe(true);
  });
});
