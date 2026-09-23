import type { Frontmatter, ManifestKind, NoteSection, NotesFileModel, NotesProblem } from './model';
import { normalizeNameForEcosystem } from './match';
import { AGENT_NOTES_HEADING, GENERATED_HEADING } from './template';

const HEADING_RE = /^##(?!#)\s+(.+?)\s*$/;
const SUBHEADING_RE = /^###(?!#)\s+(.+?)\s*$/;
const FENCE_RE = /^\s{0,3}(`{3,}|~{3,})/;
const TITLE_RE = /^#(?!#)\s+/;
const FRONTMATTER_KV_RE = /^([A-Za-z][\w-]*):\s*(.*?)\s*$/;

/** Reserved sub-heading names, compared after trimming and lower-casing. */
const subHeadingKey = (heading: string): string => heading.replace(/^###\s+/, '').trim().toLowerCase();
const AGENT_KEY = subHeadingKey(AGENT_NOTES_HEADING);
const GENERATED_KEY = subHeadingKey(GENERATED_HEADING);

/** Parse a DEPENDENCY-NOTES.md text into a line-oriented model. Tolerant by design. */
export function parseNotes(text: string): NotesFileModel {
  let hadBom = false;
  if (text.charCodeAt(0) === 0xfeff) {
    hadBom = true;
    text = text.slice(1);
  }
  const eol: '\n' | '\r\n' = text.includes('\r\n') ? '\r\n' : '\n';
  const lines = text.split(/\r\n|\n/);

  const model: NotesFileModel = {
    eol,
    hadBom,
    lines,
    intro: undefined,
    sections: [],
    problems: [],
  };

  let i = 0;

  // --- Frontmatter (only if the file starts with ---) ---
  if (lines[0] === '---') {
    for (let j = 1; j < lines.length; j++) {
      if (lines[j] === '---') {
        const fm: Frontmatter = { startLine: 0, endLine: j };
        for (let k = 1; k < j; k++) {
          const kv = FRONTMATTER_KV_RE.exec(lines[k] ?? '');
          if (!kv || kv[1] === undefined || kv[2] === undefined || kv[2] === '') continue;
          // Unknown keys are kept as lines; only the format's own are read.
          if (kv[1] === 'format') fm.formatVersion = kv[2];
          else if (kv[1] === 'lang') fm.lang = kv[2];
          else if (kv[1] === 'ecosystem' && ['cargo', 'maven', 'gradle', 'mix'].includes(kv[2])) {
            fm.ecosystem = kv[2] as ManifestKind;
          }
        }
        model.frontmatter = fm;
        i = j + 1;
        break;
      }
    }
  }

  const skipBlanks = () => {
    while (i < lines.length && (lines[i] ?? '').trim() === '') i++;
  };

  // --- Leading AI/format comment (first HTML comment before any content) ---
  skipBlanks();
  if (i < lines.length && (lines[i] ?? '').trimStart().startsWith('<!--')) {
    const start = i;
    while (i < lines.length && !(lines[i] ?? '').includes('-->')) i++;
    if (i < lines.length) {
      model.aiComment = { startLine: start, endLine: i };
      i++;
    } else {
      // Unterminated comment: treat as plain content.
      i = start;
    }
  }

  // --- Title (# Heading) ---
  skipBlanks();
  if (i < lines.length && TITLE_RE.test(lines[i] ?? '')) {
    model.titleLine = i;
    i++;
  }

  // --- Intro + sections (with fenced-code awareness) ---
  const introStart = i;
  let firstHeading = -1;
  let inFence = false;
  let current: NoteSection | undefined;

  for (; i < lines.length; i++) {
    const line = lines[i] ?? '';
    if (FENCE_RE.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    // Reserved sub-headings split a section into layers. Any other `###` is
    // ordinary body text (people may structure their own notes freely).
    if (current) {
      const sub = SUBHEADING_RE.exec(line);
      if (sub && sub[1] !== undefined) {
        const key = sub[1].trim().toLowerCase();
        if (key === AGENT_KEY) {
          // First one wins, and it cannot come after the generated block.
          if (current.agentHeadingLine === undefined && current.generatedHeadingLine === undefined) {
            current.agentHeadingLine = i;
          }
        } else if (key === GENERATED_KEY) {
          if (current.generatedHeadingLine === undefined) current.generatedHeadingLine = i;
        }
        continue;
      }
    }
    const h = HEADING_RE.exec(line);
    if (!h || h[1] === undefined) continue;
    if (firstHeading === -1) firstHeading = i;
    if (current) current.bodyEnd = i;
    current = { name: h[1], headingLine: i, bodyStart: i + 1, bodyEnd: lines.length };
    model.sections.push(current);
  }

  if (firstHeading > introStart) {
    model.intro = { startLine: introStart, endLine: firstHeading - 1 };
  } else if (firstHeading === -1 && introStart < lines.length) {
    model.intro = { startLine: introStart, endLine: lines.length - 1 };
  }

  // --- Duplicates ---
  const seen = new Map<string, NoteSection>();
  for (const s of model.sections) {
    const key = normalizeNameForEcosystem(s.name, model.frontmatter?.ecosystem);
    const first = seen.get(key);
    if (first) {
      const p: NotesProblem = {
        kind: 'duplicateSection',
        name: s.name,
        line: s.headingLine,
        firstLine: first.headingLine,
      };
      model.problems.push(p);
    } else {
      seen.set(key, s);
    }
  }

  return model;
}

/** First section matching the given package name (tolerant). */
export function findSection(model: NotesFileModel, name: string): NoteSection | undefined {
  const key = normalizeNameForEcosystem(name, model.frontmatter?.ecosystem);
  return model.sections.find((s) => normalizeNameForEcosystem(s.name, model.frontmatter?.ecosystem) === key);
}

/** Raw body text of a section, with surrounding blank lines trimmed. */
export function sectionBody(model: NotesFileModel, section: NoteSection): string {
  let start = section.bodyStart;
  let end = section.bodyEnd;
  while (start < end && (model.lines[start] ?? '').trim() === '') start++;
  while (end > start && (model.lines[end - 1] ?? '').trim() === '') end--;
  return model.lines.slice(start, end).join('\n');
}
