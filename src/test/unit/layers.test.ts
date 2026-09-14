import { describe, expect, it } from 'vitest';
import {
  agentField,
  isRemovedSection,
  layerRanges,
  notePreview,
  orderedLayers,
  parseAgentFields,
  sectionLayers,
} from '../../core/layers';
import { findSection, parseNotes } from '../../core/parseNotes';

const text = [
  '## express',
  '',
  'HTTP API layer.',
  'Do not upgrade to v5.',
  '',
  '### Agent notes',
  '',
  '- purpose: HTTP framework',
  '- constraint: stay on ^4',
  '- log: 2026-03 tried 5.0.1, reverted (PR #402)',
  '- log: 2026-09 agent: proposed hono, declined',
  '',
  '### Generated',
  '<!-- generated:start -->',
  '- installed: 4.18.2',
  '<!-- generated:end -->',
  '',
  '## lodash',
  '',
  'Utility helpers.',
  '',
  '## bare',
  '',
  '### Agent notes',
  '',
  '- purpose: only an agent wrote here',
].join('\n');

const model = parseNotes(text);
const express = findSection(model, 'express')!;
const lodash = findSection(model, 'lodash')!;
const bare = findSection(model, 'bare')!;

describe('layerRanges / sectionLayers', () => {
  it('splits a full section into human, agent and generated', () => {
    expect(layerRanges(express)).toEqual({
      humanStart: 1,
      humanEnd: 5,
      agentStart: 6,
      agentEnd: 12,
      generatedStart: 12,
      generatedEnd: 17,
    });
    const l = sectionLayers(model, express);
    expect(l.human).toBe('HTTP API layer.\nDo not upgrade to v5.');
    expect(l.agent).toBe(
      '- purpose: HTTP framework\n- constraint: stay on ^4\n- log: 2026-03 tried 5.0.1, reverted (PR #402)\n- log: 2026-09 agent: proposed hono, declined',
    );
    // The generated block keeps its heading — it is preserved as one unit.
    expect(l.generated.startsWith('### Generated')).toBe(true);
    expect(l.generated).toContain('- installed: 4.18.2');
  });

  it('gives empty agent/generated when a section has only human text', () => {
    expect(layerRanges(lodash)).toEqual({ humanStart: 18, humanEnd: 21 });
    expect(sectionLayers(model, lodash)).toEqual({ human: 'Utility helpers.', agent: '', generated: '' });
  });

  it('gives empty human when only an agent wrote', () => {
    const l = sectionLayers(model, bare);
    expect(l.human).toBe('');
    expect(l.agent).toBe('- purpose: only an agent wrote here');
  });
});

describe('parseAgentFields / agentField', () => {
  it('keeps order and repeats, lower-cases keys, accepts * bullets, skips prose', () => {
    const fields = parseAgentFields(
      ['- Purpose: HTTP framework', '* constraint: stay on ^4', 'free prose line', '- log: one', '- log: two', '- links: https://x.y/z'].join(
        '\n',
      ),
    );
    expect(fields.map((f) => f.key)).toEqual(['purpose', 'constraint', 'log', 'log', 'links']);
    expect(fields.filter((f) => f.key === 'log').map((f) => f.value)).toEqual(['one', 'two']);
    expect(agentField(fields, 'LINKS')).toBe('https://x.y/z');
    expect(agentField(fields, 'log')).toBe('one');
    expect(agentField(fields, 'owner')).toBeUndefined();
  });
});

describe('isRemovedSection', () => {
  const m = parseNotes(
    [
      '## old',
      '',
      '### Agent notes',
      '',
      '- status: removed 2026-06 — replaced by lodash',
      '',
      '## dead',
      '',
      '### Agent notes',
      '',
      '- status: dead',
      '',
      '## Cased',
      '',
      '### Agent notes',
      '',
      '- Status: Removed 2026-01',
      '',
      '## plain',
      '',
      'no agent block',
    ].join('\n'),
  );
  it('is true only for a status that starts with "removed"', () => {
    expect(isRemovedSection(m, findSection(m, 'old')!)).toBe(true);
    expect(isRemovedSection(m, findSection(m, 'Cased')!)).toBe(true);
    expect(isRemovedSection(m, findSection(m, 'dead')!)).toBe(false);
    expect(isRemovedSection(m, findSection(m, 'plain')!)).toBe(false);
  });
});

describe('orderedLayers', () => {
  const l = { human: 'H', agent: 'A', generated: '' };
  it('shows both layers, ordered by the setting; -only variants do not hide the other layer', () => {
    expect(orderedLayers(l, 'human-first').map((p) => p.kind)).toEqual(['human', 'agent']);
    expect(orderedLayers(l, 'human-only').map((p) => p.kind)).toEqual(['human', 'agent']);
    expect(orderedLayers(l, 'ai-first').map((p) => p.kind)).toEqual(['agent', 'human']);
    expect(orderedLayers(l, 'ai-only').map((p) => p.kind)).toEqual(['agent', 'human']);
  });
  it('drops empty layers', () => {
    expect(orderedLayers({ human: '', agent: 'A', generated: '' }, 'human-first')).toEqual([{ kind: 'agent', text: 'A' }]);
  });
});

describe('notePreview', () => {
  const both = { human: 'Do not upgrade to v5.\nSecond line.', agent: '- purpose: HTTP framework\n- constraint: ^4', generated: '' };
  const humanOnly = { human: '- why: still allowed as prose', agent: '', generated: '' };
  const agentOnly = { human: '', agent: '- constraint: x\n- purpose: from the agent', generated: '' };

  it('human-first: first human line, else the agent purpose', () => {
    expect(notePreview(both, 'human-first')).toBe('Do not upgrade to v5.');
    expect(notePreview(agentOnly, 'human-first')).toBe('from the agent');
  });

  it('ai-first: agent purpose, else the first human line', () => {
    expect(notePreview(both, 'ai-first')).toBe('HTTP framework');
    expect(notePreview(humanOnly, 'ai-first')).toBe('why: still allowed as prose');
  });

  it('-only variants never fall back', () => {
    expect(notePreview(agentOnly, 'human-only')).toBe('note');
    expect(notePreview(humanOnly, 'ai-only')).toBe('note');
  });

  it('strips a leading list marker for display and never parses the human text otherwise', () => {
    expect(notePreview(humanOnly)).toBe('why: still allowed as prose');
    expect(notePreview({ human: '* starred', agent: '', generated: '' })).toBe('starred');
  });

  it('truncates to exactly maxLen with an ellipsis and says "note" when empty', () => {
    const long = notePreview({ human: 'x'.repeat(120), agent: '', generated: '' });
    expect(long).toHaveLength(90);
    expect(long.endsWith('…')).toBe(true);
    expect(notePreview({ human: '', agent: '', generated: '' })).toBe('note');
  });
});
