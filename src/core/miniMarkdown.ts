/**
 * Fallback rendering for the note panel, used only when VS Code's built-in
 * Markdown extension (the normal renderer) is unavailable: paragraphs, line
 * breaks, bullet lists and inline code. Everything is escaped; nothing else
 * is interpreted.
 */
export function miniMarkdown(md: string): string {
  const esc = (s: string): string =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const inline = (s: string): string => esc(s).replace(/`([^`]+)`/g, '<code>$1</code>');
  const text = md.replace(/\r\n/g, '\n').trim();
  if (text === '') return '';
  return text
    .split(/\n{2,}/)
    .map((block) => {
      const lines = block.split('\n');
      if (lines.every((l) => /^\s*[-*]\s+/.test(l))) {
        return `<ul>${lines.map((l) => `<li>${inline(l.replace(/^\s*[-*]\s+/, ''))}</li>`).join('')}</ul>`;
      }
      return `<p>${lines.map(inline).join('<br>')}</p>`;
    })
    .join('');
}
