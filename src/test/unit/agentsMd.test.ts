import { describe, expect, it } from 'vitest';
import { AGENT_FIELDS, agentsMdContent } from '../../core/agentsMd';

describe('agentsMd', () => {
  const content = agentsMdContent();
  const tableRows = content.split('\n').filter((l) => l.startsWith('| ') && !l.startsWith('| Question') && !l.startsWith('|---'));

  it('defines the approved vocabulary: 16 unique keys, 6 of them core, note last', () => {
    expect(AGENT_FIELDS).toHaveLength(16);
    expect(new Set(AGENT_FIELDS.map((f) => f.key)).size).toBe(16);
    expect(AGENT_FIELDS[AGENT_FIELDS.length - 1]?.key).toBe('note');
    expect(content).toContain('never invent a key');
    expect(AGENT_FIELDS.filter((f) => f.core).map((f) => f.key)).toEqual([
      'purpose',
      'usage',
      'constraint',
      'verify',
      'log',
      'verified',
    ]);
  });

  it('lists every field exactly once in the tables', () => {
    for (const f of AGENT_FIELDS) {
      const rows = tableRows.filter((r) => r.includes(`\`${f.key}:\``));
      expect(rows, `rows for ${f.key}`).toHaveLength(1);
    }
    expect(tableRows).toHaveLength(AGENT_FIELDS.length);
  });

  it('carries the rules that came out of the trial, and none of the dropped ideas', () => {
    expect(content).toContain('### Agent notes');
    expect(content).toContain('.pacmon/DEPENDENCIES.md');
    expect(content).toContain('status: removed');
    expect(content).toContain('`dead`');
    expect(content).toContain('`removal-planned`');
    expect(content).toContain('Never write an empty field');
    expect(content).toContain('judgments, not measurements');
    expect(content).toContain('Never edit or delete it');
    // Dropped: `updated:` (replaced by `verified:`), why/caution bullets, and
    // mirroring the human note with a source marker.
    expect(content).not.toMatch(/`updated:`|\bupdated:/);
    expect(content).not.toMatch(/\bwhy:|\bcaution:/);
    expect(content).not.toMatch(/source:\s*human/i);
  });

  it('is deterministic and honours the requested line ending', () => {
    expect(agentsMdContent()).toBe(content);
    const crlf = agentsMdContent('\r\n');
    expect(crlf).toContain('\r\n');
    expect(crlf.replace(/\r\n/g, '')).not.toContain('\n');
    expect(crlf.endsWith('\r\n')).toBe(true);
  });
});
