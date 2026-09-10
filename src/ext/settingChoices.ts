import { INLINE_SOURCES } from '../core/layers';

/**
 * The values the Pacmon view may write for each radio group. Shared by the
 * markup (which radios to draw) and the host (what to accept off the wire):
 * the webview is not trusted to pick setting values on its own.
 */
export const CHOICE_VALUES = {
  // Only the two first-class editors. Other values still work if set in
  // settings.json; the view says so rather than showing nothing selected.
  noteEntry: ['panel', 'peek'],
  decorations: ['preview', 'badge', 'off'],
  inlineSource: INLINE_SOURCES,
} as const satisfies Record<string, readonly string[]>;

export function choiceValues(key: string): readonly string[] | undefined {
  return (CHOICE_VALUES as Record<string, readonly string[]>)[key];
}
