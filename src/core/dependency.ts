import type { DependencyEntry, SourceRange } from './model';

export function dependencyEntry(
  noteKey: string,
  scope: string,
  primaryRange: SourceRange,
  sourceRanges: readonly SourceRange[] = [primaryRange],
  displayName = noteKey,
): DependencyEntry {
  return {
    noteKey,
    displayName,
    scope,
    primaryRange,
    sourceRanges,
    name: noteKey,
    section: scope,
    keyOffset: primaryRange.offset,
    keyLength: primaryRange.length,
  };
}

export function dependencyAtOffset(
  dependencies: readonly DependencyEntry[],
  offset: number,
): DependencyEntry | undefined {
  return dependencies.find((dependency) =>
    dependency.sourceRanges.some((range) => offset >= range.offset && offset <= range.offset + range.length),
  );
}
