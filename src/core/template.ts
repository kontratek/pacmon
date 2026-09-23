import type { ManifestKind } from './model';

export const FORMAT_VERSION = 'dependency-notes/1';
export const FORMAT_VERSION_V2 = 'dependency-notes/2';
export const SUPPORTED_FORMAT_VERSIONS = [FORMAT_VERSION, FORMAT_VERSION_V2] as const;
export const DEFAULT_LANG = 'en';

/** Everything Pacmon owns lives beside the dependency manifest under this directory. */
export const NOTES_DIR = '.pacmon';
export const NOTES_FILE_NAME = 'DEPENDENCY-NOTES.md';
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

export function frontmatterLinesFor(ecosystem?: ManifestKind): string[] {
  if (!ecosystem || ecosystem === 'npm') return [...DEFAULT_FRONTMATTER_LINES];
  return ['---', `format: ${FORMAT_VERSION_V2}`, `ecosystem: ${ecosystem}`, `lang: ${DEFAULT_LANG}`, '---'];
}

export const DEFAULT_TITLE = '# Dependency Notes';

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

export function formatCommentLines(ecosystem?: ManifestKind): string[] {
  if (!ecosystem || ecosystem === 'npm') return [...AI_FORMAT_COMMENT_LINES];
  return [
    `<!-- Each "## name" below is a package from the ${ecosystem} dependency manifest.`,
    `  The text under it is written by people. "${AGENT_NOTES_HEADING}" and everything below`,
    `  it is written by AI agents — rules in ${AGENT_RULES_REL_PATH}. -->`,
  ];
}

/** Full content of a freshly created notes file. */
export function newNotesFileContent(
  eol: string,
  firstSectionName?: string,
  body?: string,
  ecosystem?: ManifestKind,
): string {
  const lines = [
    ...frontmatterLinesFor(ecosystem),
    '',
    ...formatCommentLines(ecosystem),
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
/** Written before the format was named `dependency-notes/1`. Still found, never written. */
const LEGACY_AI_BLOCK = ['<!-- pacmon:deps-notes:start -->', '<!-- pacmon:deps-notes:end -->'] as const;

/**
 * Pointer block for the user's own agent files (AGENTS.md, CLAUDE.md, rules).
 * Deliberately three lines: the rules themselves live in `.pacmon/AGENT-RULES.md`,
 * which Pacmon owns; these files it does not.
 */
export function aiInstructionsBlock(): string {
  return [
    AI_BLOCK_START,
    `Dependency notes live in \`${NOTES_REL_PATH}\` for npm, \`${NOTES_DIR}/cargo/${NOTES_FILE_NAME}\` for Rust, \`${NOTES_DIR}/maven/${NOTES_FILE_NAME}\` for Maven, \`${NOTES_DIR}/gradle/${NOTES_FILE_NAME}\` for Gradle, and \`${NOTES_DIR}/mix/${NOTES_FILE_NAME}\` for Mix; people write below \`## <package>\`, agents under \`${AGENT_NOTES_HEADING}\`.`,
    `Rules and the field list are in \`${AGENT_RULES_REL_PATH}\`; read a package's section before adding, bumping or removing it.`,
    `Update the notes in the same commit as the dependency manifest; a removed package keeps its section with \`- status: removed …\`.`,
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
