import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { AGENT_NOTES_HEADING, AGENT_RULES_REL_PATH, GENERATED_HEADING, NOTES_REL_PATH } from '../../core/template';
import { AGENT_FIELDS, EXPOSURE_VALUES, RUNTIME_VALUES } from '../../core/vocabulary';

/** The file Pacmon copies into a workspace. Hand-written, so this checks it against the code it must agree with. */
const content = readFileSync(new URL('../../../assets/AGENT-RULES.md', import.meta.url), 'utf8');

const rowKey = (line: string): string | undefined => /^\| `([a-z-]+):` \|/.exec(line)?.[1];
const keysIn = (part: string | undefined): string[] =>
  (part ?? '').split('\n').map(rowKey).filter((k): k is string => k !== undefined);
const [coreTable, optionalTable] = content.split('Add these only when');

describe('assets/AGENT-RULES.md', () => {
  it('lists the core fields, then the optional ones, exactly as the vocabulary has them', () => {
    expect(keysIn(coreTable)).toEqual(AGENT_FIELDS.filter((f) => f.core).map((f) => f.key));
    expect(keysIn(optionalTable)).toEqual(AGENT_FIELDS.filter((f) => !f.core).map((f) => f.key));
  });

  it('names the enumerated values the lint accepts', () => {
    for (const v of [...RUNTIME_VALUES, ...EXPOSURE_VALUES, 'dead', 'removal-planned']) {
      expect(content, v).toContain(`\`${v}\``);
    }
    expect(content).toContain('status: removed');
  });

  it('names the files and headings the code uses', () => {
    expect(content).toContain(NOTES_REL_PATH);
    expect(content).toContain(AGENT_RULES_REL_PATH);
    expect(content).toContain(AGENT_NOTES_HEADING);
    expect(content).toContain(GENERATED_HEADING);
  });

  it('carries the rules that came out of the trial, and none of the dropped ideas', () => {
    expect(content).toContain('never invent a key');
    expect(content).toContain('Never write an empty field');
    expect(content).toContain('judgments, not measurements');
    expect(content).toContain('Never edit or delete it');
    // Dropped: `updated:` (replaced by `verified:`), why/caution bullets, and
    // mirroring the human note with a source marker.
    expect(content).not.toMatch(/`updated:`|\bupdated:/);
    expect(content).not.toMatch(/\bwhy:|\bcaution:/);
    expect(content).not.toMatch(/source:\s*human/i);
  });

  it('is short enough to be read whole, LF-only, one trailing newline', () => {
    expect(content.split('\n').length).toBeLessThanOrEqual(150);
    expect(content).not.toContain('\r');
    expect(content.endsWith('\n') && !content.endsWith('\n\n')).toBe(true);
  });
});
