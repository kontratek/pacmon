import { analyze } from './analyze';
import type { DepEntry, NotesFileModel } from './model';
import { closest } from './similar';

/** A section whose package is not in package.json: it gets a marker, not a diagnostic. */
export interface OrphanMarker {
  /** Heading line. */
  line: number;
  name: string;
  /** A real dependency this name is a near-miss of — probably a typo. */
  guess?: string;
  /** Carries `- status: removed …`: kept on purpose. */
  removed: boolean;
}

export function orphanMarkers(deps: readonly DepEntry[], model: NotesFileModel): OrphanMarker[] {
  const { orphans, removed } = analyze(deps, model);
  const names = deps.map((d) => d.noteKey);
  const out: OrphanMarker[] = [];
  for (const s of orphans) {
    const guess = closest(s.name, names);
    out.push(
      guess === undefined
        ? { line: s.headingLine, name: s.name, removed: false }
        : { line: s.headingLine, name: s.name, guess, removed: false },
    );
  }
  for (const s of removed) out.push({ line: s.headingLine, name: s.name, removed: true });
  return out.sort((a, b) => a.line - b.line);
}
