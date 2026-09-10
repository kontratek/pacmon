import type { DepEntry, NoteSection, NotesFileModel, NotesProblem } from './model';
import { isEmptySection, isRemovedSection } from './layers';
import { normalizeName } from './match';

export interface Analysis {
  /** dep name (normalized) → first matching section */
  byDep: Map<string, NoteSection>;
  documented: DepEntry[];
  undocumented: DepEntry[];
  /** Sections whose heading matches no dependency (typo or removed dependency). */
  orphans: NoteSection[];
  /** Sections kept on purpose for a package that left package.json (`- status: removed …`). */
  removed: NoteSection[];
  duplicates: NotesProblem[];
}

export function analyze(deps: readonly DepEntry[], notes: NotesFileModel | undefined): Analysis {
  const byDep = new Map<string, NoteSection>();
  const documented: DepEntry[] = [];
  const undocumented: DepEntry[] = [];
  const orphans: NoteSection[] = [];
  const removed: NoteSection[] = [];

  const depKeys = new Set(deps.map((d) => normalizeName(d.name)));
  const sectionByKey = new Map<string, NoteSection>();
  for (const s of notes?.sections ?? []) {
    const key = normalizeName(s.name);
    if (!sectionByKey.has(key)) sectionByKey.set(key, s);
    if (!depKeys.has(key)) {
      if (notes && isRemovedSection(notes, s)) removed.push(s);
      else orphans.push(s);
    }
  }

  for (const d of deps) {
    const s = sectionByKey.get(normalizeName(d.name));
    // A heading with nothing under it is not a note: coverage must not count it.
    if (s && notes && !isEmptySection(notes, s)) {
      byDep.set(normalizeName(d.name), s);
      documented.push(d);
    } else {
      undocumented.push(d);
    }
  }

  return { byDep, documented, undocumented, orphans, removed, duplicates: notes?.problems ?? [] };
}
