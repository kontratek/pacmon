import { describe, expect, it } from 'vitest';
import { renderHtml } from '../../ext/notePanelHtml';

/** The <script nonce="…"> body of the panel, as the webview receives it. */
function scriptOf(html: string): string {
  const m = /<script nonce="[A-Za-z0-9]+">([\s\S]*?)<\/script>/.exec(html);
  expect(m, 'panel html should contain exactly one nonced script block').toBeTruthy();
  return m![1]!;
}

/** The declarations of one CSS rule in the panel's <style> block. */
function ruleOf(html: string, selector: string): string {
  const at = html.indexOf(`\n  ${selector} {`);
  expect(at, `panel css should have a "${selector}" rule`).toBeGreaterThan(-1);
  const open = html.indexOf('{', at);
  return html.slice(open + 1, html.indexOf('}', open));
}

describe('note panel html', () => {
  it('is offline by construction: locked-down CSP, no external references', () => {
    const html = renderHtml();
    expect(html).toContain("default-src 'none'");
    // Anything that could reach out must be absent.
    expect(html).not.toMatch(/https?:\/\//);
    expect(html).not.toMatch(/<link\b/);
    expect(html).not.toMatch(/\bsrc=/);
  });

  it('allowlists exactly the nonce it puts on the style and script blocks', () => {
    const html = renderHtml();
    // The CSP writes it as 'nonce-XXX'; the tags as nonce="XXX".
    const allowed = [...html.matchAll(/'nonce-([A-Za-z0-9]+)'/g)].map((m) => m[1]);
    const used = [...html.matchAll(/nonce="([A-Za-z0-9]+)"/g)].map((m) => m[1]);
    expect(allowed).toHaveLength(2); // style-src + script-src
    expect(used).toHaveLength(2); // <style> + <script>
    expect(new Set([...allowed, ...used]).size).toBe(1);
    expect(used[0]!.length).toBe(32);
  });

  it('gives each panel a fresh nonce', () => {
    const a = /nonce="([A-Za-z0-9]+)"/.exec(renderHtml())![1];
    const b = /nonce="([A-Za-z0-9]+)"/.exec(renderHtml())![1];
    expect(a).not.toBe(b);
  });

  it('emits syntactically valid webview JavaScript', () => {
    // Guards the class of bug this file is most exposed to: an escape in the
    // TS template literal (\\n, quotes, ${…}) that produces broken JS at
    // runtime — where the CSP would let it fail silently.
    expect(() => new Function(scriptOf(renderHtml()))).not.toThrow();
  });

  it('shows each layer rendered and edits it in place; the agent box starts folded', () => {
    const html = renderHtml();
    for (const id of ['humanView', 'human', 'agentView', 'agent']) {
      expect(html, `needs #${id}`).toContain(`id="${id}"`);
    }
    expect(html).toMatch(/<details id="agentBox">/);
    // Problems in the agent block are listed under it, with a Fix button.
    expect(html).toContain('id="agentProblems"');
    expect(html).toContain('id="fixAgent"');
    // The editors start hidden: read mode first.
    expect(html).toMatch(/<textarea id="human"[^>]*\bhidden>/);
    expect(html).toMatch(/<textarea id="agent"[^>]*\bhidden>/);
    // No Save button: it saves as you type. And the why/caution helpers went with the flat format.
    expect(html).not.toContain('id="save"');
    expect(html).not.toContain('- why: ');
    expect(html).not.toContain('- caution: ');
  });

  it('saves as you type and on leaving, keyed by the dependency it was loaded for', () => {
    const script = scriptOf(renderHtml());
    expect(script).toContain('setTimeout(save, 600)');
    expect(script).toContain("e.key === 'Escape'");
    expect(script).toContain("e.key === 's'");
    expect(script).toContain("'pagehide'");
    for (const type of ['ready', 'openFile']) {
      expect(script, `webview should post a "${type}" message`).toContain(`type: '${type}'`);
    }
    expect(script).toContain("post('save')");
    expect(script).toContain("post('input')");
    expect(script).toContain("post('fixAgent')");
    expect(script).toMatch(/\{ type, key \}/);
    expect(script).toContain("m.type === 'problems'");
  });

  it('reads as text at rest — the frame and the fill belong to writing', () => {
    const html = renderHtml();
    // What this guards: a note being read looked like an empty input box.
    const view = ruleOf(html, '.view');
    expect(view).not.toContain('background:');
    expect(view).toContain('border: 1px solid transparent');
    // Still plainly live: hover fills it, keyboard focus draws the frame.
    expect(ruleOf(html, '.view:hover')).toContain('background:');
    expect(ruleOf(html, '.view:focus-visible')).toContain('focusBorder');
    // Writing is the framed, filled box — the one place chrome is earned.
    const box = ruleOf(html, 'textarea');
    expect(box).toContain('input-background');
    expect(box).toContain('focusBorder');
    // Read and write are the same box: one floor, so neither mode is taller.
    expect(ruleOf(html, 'body')).toContain('--note-min:');
    expect(view).toContain('min-height: var(--note-min)');
    expect(box).toContain('min-height: var(--note-min)');
    // Each layer keeps a caption, since the frame no longer names it.
    expect(html).toContain('id="humanCaption"');
    expect(html).toContain('id="agentCaption"');
    expect(html).toContain('aria-labelledby="humanCaption"');
    expect(html).toContain('aria-labelledby="agentCaption"');
    // One pen, on the human caption: the one cue that does not wait for the
    // mouse. Drawn, not loaded — default-src 'none' puts codicons out of reach.
    expect([...html.matchAll(/class="pen"/g)]).toHaveLength(1);
    expect(html).toMatch(/<svg class="pen"[^>]*aria-hidden="true"/);
    expect(ruleOf(html, '.pen')).toContain('opacity:');
  });

  it('never puts rendered markup into the page without the sanitizer', () => {
    const script = scriptOf(renderHtml());
    const assignments = [...script.matchAll(/innerHTML\s*=\s*([A-Za-z_]+)\(/g)].map((m) => m[1]);
    expect(assignments.length).toBeGreaterThan(0);
    expect(new Set(assignments)).toEqual(new Set(['sanitize']));
    expect(script).not.toMatch(/innerHTML\s*=(?!\s*sanitize\()/);
  });
});
