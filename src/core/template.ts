export const FORMAT_VERSION = 'deps-notes/1';
export const DEFAULT_LANG = 'en';

/** Everything Pacmon owns lives in this directory, next to the package.json it describes. */
export const NOTES_DIR = '.pacmon';
export const NOTES_FILE_NAME = 'DEPENDENCIES.md';
export const AGENTS_FILE_NAME = 'AGENTS.md';
export const NOTES_REL_PATH = `${NOTES_DIR}/${NOTES_FILE_NAME}`;
export const AGENTS_REL_PATH = `${NOTES_DIR}/${AGENTS_FILE_NAME}`;

/** Reserved sub-headings inside a package section. Matched case-insensitively. */
export const AGENT_NOTES_HEADING = '### Agent notes';
export const GENERATED_HEADING = '### Generated';

/** Frontmatter keys the format defines, in canonical order. The normalizer adds missing ones. */
export const FRONTMATTER_DEFAULTS: ReadonlyArray<readonly [key: string, value: string]> = [
  ['format', FORMAT_VERSION],
  ['lang', DEFAULT_LANG],
  ['agents', AGENTS_REL_PATH],
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
 * file itself. Everything else (fields, rules, examples) lives in AGENTS.md.
 */
export const AI_FORMAT_COMMENT_LINES = [
  '<!-- Each "## name" below is a package from package.json. The text right under the',
  `  heading is written by people. "${AGENT_NOTES_HEADING}" and everything below it is written`,
  `  by AI agents — rules in ${AGENTS_REL_PATH}. -->`,
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

const AI_BLOCK_START = '<!-- pacmon:deps-notes:start -->';
const AI_BLOCK_END = '<!-- pacmon:deps-notes:end -->';

/**
 * Pointer block for the user's own agent files (AGENTS.md, CLAUDE.md, rules).
 * Deliberately three lines: the rules themselves live in the generated
 * `.pacmon/AGENTS.md`, which Pacmon owns; these files it does not.
 */
export function aiInstructionsBlock(): string {
  return [
    AI_BLOCK_START,
    `Dependency notes live in \`${NOTES_REL_PATH}\`: one \`## <package>\` section per dependency — people write right under the heading, AI agents write under \`${AGENT_NOTES_HEADING}\`.`,
    `Rules and the field list are in \`${AGENTS_REL_PATH}\`; read a package's section before adding, bumping or removing it.`,
    `Update the notes in the same commit as \`package.json\`; a removed package keeps its section with \`- status: removed …\`.`,
    AI_BLOCK_END,
  ].join('\n');
}

/** Insert or replace the marked block in an existing instructions file. */
export function upsertAiInstructions(existing: string): string {
  const block = aiInstructionsBlock();
  const start = existing.indexOf(AI_BLOCK_START);
  const end = existing.indexOf(AI_BLOCK_END);
  if (start !== -1 && end !== -1 && end > start) {
    return existing.slice(0, start) + block + existing.slice(end + AI_BLOCK_END.length);
  }
  const sep = existing.length === 0 ? '' : existing.endsWith('\n\n') ? '' : existing.endsWith('\n') ? '\n' : '\n\n';
  return existing + sep + block + '\n';
}
