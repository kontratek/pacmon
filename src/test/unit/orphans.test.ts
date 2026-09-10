import { describe, expect, it } from 'vitest';
import { orphanMarkers } from '../../core/orphans';
import { extractDeps } from '../../core/packageJson';
import { parseNotes } from '../../core/parseNotes';

describe('orphanMarkers', () => {
  const deps = extractDeps(JSON.stringify({ dependencies: { express: '1', lodash: '1' } }));
  const notes = parseNotes(
    [
      '## express',
      'fine',
      '## ghost-package',
      'no such dependency',
      '## lodas',
      'a typo',
      '## old-package',
      '',
      '### Agent notes',
      '',
      '- status: removed 2026-06 — replaced by lodash',
    ].join('\n'),
  );

  it('marks every section whose package is not in package.json, in file order', () => {
    expect(orphanMarkers(deps, notes)).toEqual([
      { line: 2, name: 'ghost-package', removed: false },
      { line: 4, name: 'lodas', guess: 'lodash', removed: false },
      { line: 6, name: 'old-package', removed: true },
    ]);
  });

  it('marks nothing when every section is a dependency', () => {
    expect(orphanMarkers(deps, parseNotes('## express\nx\n## lodash\ny'))).toEqual([]);
  });
});
