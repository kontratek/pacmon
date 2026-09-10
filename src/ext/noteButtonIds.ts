/** The click-target ids, kept free of any vscode import so both the settings
 *  webview's HTML builder and its unit test can use them. */
export type NoteButton = 'iconLeft' | 'link' | 'codelens' | 'inlayHint' | 'lightbulb';

export const ALL_NOTE_BUTTONS: readonly NoteButton[] = [
  'iconLeft',
  'link',
  'codelens',
  'inlayHint',
  'lightbulb',
];

/** Shipped default: the pair that won the 2026-09-04 comparison. */
export const DEFAULT_NOTE_BUTTONS: readonly NoteButton[] = ['iconLeft', 'link'];
