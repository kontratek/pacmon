import { describe, expect, it } from 'vitest';
import { closest, levenshtein } from '../../core/similar';

describe('similar', () => {
  it('measures edit distance', () => {
    expect(levenshtein('', '')).toBe(0);
    expect(levenshtein('kitten', 'sitting')).toBe(3);
    expect(levenshtein('express', 'expres')).toBe(1);
  });

  it('guesses only when the name is close enough to be sure', () => {
    const deps = ['@scope/util', 'express', 'lodash', 'vitest'];
    expect(closest('expres', deps)).toBe('express');
    expect(closest('lodas', deps)).toBe('lodash');
    expect(closest('@scope/utl', deps)).toBe('@scope/util');
    expect(closest('Express', deps)).toBe('express');
    expect(closest('ghost-package', deps)).toBeUndefined();
    expect(closest('moment', deps)).toBeUndefined();
    // Short names get almost no slack: 'ab' must not become 'abc' by accident.
    expect(closest('vi', deps)).toBeUndefined();
    expect(closest('x', [])).toBeUndefined();
  });
});
