export const FORMAT_VERSION = 'pacmon/1';
export const DEFAULT_LANG = 'en';

/** Everything Pacmon owns lives in this directory, next to the package.json it describes. */
export const NOTES_DIR = '.pacmon';
export const NOTES_FILE_NAME = 'DEPENDENCIES.md';
export const AGENT_RULES_FILE_NAME = 'AGENT-RULES.md';
export const NOTES_REL_PATH = `${NOTES_DIR}/${NOTES_FILE_NAME}`;
export const AGENT_RULES_REL_PATH = `${NOTES_DIR}/${AGENT_RULES_FILE_NAME}`;

/** Reserved sub-headings inside a package section. Matched case-insensitively. */
export const AGENT_NOTES_HEADING = '### Agent notes';
export const GENERATED_HEADING = '### Generated';

/** Frontmatter keys the format defines, in canonical order. The normalizer adds missing ones. */
export const FRONTMATTER_DEFAULTS: ReadonlyArray<readonly [key: string, value: string]> = [
  ['format', FORMAT_VERSION],
  ['lang', DEFAULT_LANG],
];

export const DEFAULT_FRONTMATTER_LINES = [
  '---',
  ...FRONTMATTER_DEFAULTS.map(([key, value]) => `${key}: ${value}`),
  '---',
];

export const DEFAULT_TITLE = '# Dependencies';

/**
 * The header comment is owned by the format: the normalizer rewrites it from
 * here, so it stays three lines that say only what cannot be inferred from the
 * file itself. The rules and the field list live in AGENT-RULES.md.
 */
export const AI_FORMAT_COMMENT_LINES = [
  '<!-- Each "## name" below is a package from package.json. The text right under the',
  `  heading is written by people. "${AGENT_NOTES_HEADING}" and everything below it is written`,
  `  by AI agents — rules in ${AGENT_RULES_REL_PATH}. -->`,
];

/** Full content of a freshly created notes file. */
export function newNotesFileContent(eol: string, firstSectionName?: string, body?: string): string {
  const lines = [
    ...DEFAULT_FRONTMATTER_LINES,
    '',
    ...AI_FORMAT_COMMENT_LINES,
    '',
    DEFAULT_TITLE,
  ];
  if (firstSectionName) {
    lines.push('', `## ${firstSectionName.trim()}`, '');
    if (body && body.trim() !== '') {
      lines.push(...body.trim().split(/\r?\n/));
    }
  }
  return lines.join(eol) + eol;
}

const AI_BLOCK_START = '<!-- pacmon:start -->';
const AI_BLOCK_END = '<!-- pacmon:end -->';
/** Written before the format was named `pacmon/1`. Still found, never written. */
const LEGACY_AI_BLOCK = ['<!-- pacmon:deps-notes:start -->', '<!-- pacmon:deps-notes:end -->'] as const;

/**
 * Pointer block for the user's own agent files (AGENTS.md, CLAUDE.md, rules).
 * Deliberately three lines: the rules themselves live in `.pacmon/AGENT-RULES.md`,
 * which Pacmon owns; these files it does not.
 */
export function aiInstructionsBlock(): string {
  return [
    AI_BLOCK_START,
    `Dependency notes live in \`${NOTES_REL_PATH}\`: one \`## <package>\` section per dependency — people write right under the heading, AI agents write under \`${AGENT_NOTES_HEADING}\`.`,
    `Rules and the field list are in \`${AGENT_RULES_REL_PATH}\`; read a package's section before adding, bumping or removing it.`,
    `Update the notes in the same commit as \`package.json\`; a removed package keeps its section with \`- status: removed …\`.`,
    AI_BLOCK_END,
  ].join('\n');
}

/** Insert or replace the marked block in an existing instructions file. */
export function upsertAiInstructions(existing: string): string {
  const block = aiInstructionsBlock();
  for (const [startMark, endMark] of [[AI_BLOCK_START, AI_BLOCK_END], LEGACY_AI_BLOCK] as const) {
    const start = existing.indexOf(startMark);
    const end = existing.indexOf(endMark);
    if (start !== -1 && end !== -1 && end > start) {
      return existing.slice(0, start) + block + existing.slice(end + endMark.length);
    }
  }
  const sep = existing.length === 0 ? '' : existing.endsWith('\n\n') ? '' : existing.endsWith('\n') ? '\n' : '\n\n';
  return existing + sep + block + '\n';
}
