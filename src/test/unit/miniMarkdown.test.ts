import { describe, expect, it } from 'vitest';
import { miniMarkdown } from '../../core/miniMarkdown';

describe('miniMarkdown', () => {
  it('renders paragraphs, line breaks, bullets and inline code, escaping the rest', () => {
    expect(miniMarkdown('a <b>\nb\n\n- x `y`\n- z')).toBe(
      '<p>a &lt;b&gt;<br>b</p><ul><li>x <code>y</code></li><li>z</li></ul>',
    );
  });

  it('returns nothing for blank input and tolerates CRLF', () => {
    expect(miniMarkdown('  \n')).toBe('');
    expect(miniMarkdown('one\r\ntwo')).toBe('<p>one<br>two</p>');
  });
});
