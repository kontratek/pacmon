export type Eol = '\n' | '\r\n';

export interface LineRange {
  /** inclusive, 0-based */
  startLine: number;
  /** inclusive, 0-based */
  endLine: number;
}

export interface NoteSection {
  /** Trimmed heading text after "## " — the package name as written. */
  name: string;
  headingLine: number;
  /** First body line (0-based, may equal bodyEnd when body is empty). */
  bodyStart: number;
  /** Exclusive end line of the body. */
  bodyEnd: number;
  /**
   * Line of the first `### Agent notes` sub-heading (case-insensitive, outside
   * fences). Everything from bodyStart up to it is the human-written layer;
   * everything after it is the agent-written layer.
   */
  agentHeadingLine?: number;
  /**
   * Line of the first `### Generated` sub-heading. Reserved for a future
   * version: preserved verbatim, never edited, no UI yet.
   */
  generatedHeadingLine?: number;
}

export interface NotesProblem {
  kind: 'duplicateSection';
  name: string;
  line: number;
  firstLine: number;
}

export interface Frontmatter extends LineRange {
  /** `format:` — the deps-notes format version this file was written for. */
  formatVersion?: string;
  /** `lang:` — language the values are written in (keys are always English). */
  lang?: string;
  /** `agents:` — path to the agent instruction file, relative to the repo root. */
  agents?: string;
}

export interface NotesFileModel {
  eol: Eol;
  hadBom: boolean;
  lines: string[];
  frontmatter?: Frontmatter;
  aiComment?: LineRange;
  titleLine?: number;
  /** Free text between the header block and the first section (verbatim, may be empty). */
  intro: LineRange | undefined;
  sections: NoteSection[];
  problems: NotesProblem[];
}

export type DepSection =
  | 'dependencies'
  | 'devDependencies'
  | 'peerDependencies'
  | 'optionalDependencies';

export interface DepEntry {
  name: string;
  section: DepSection;
  /** Offset of the key node in the package.json text (includes the opening quote). */
  keyOffset: number;
  keyLength: number;
}
