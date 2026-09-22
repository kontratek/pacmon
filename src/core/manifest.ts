import type { DependencyEntry, ManifestKind } from './model';
import { extractCargoDependencies } from './cargoManifest';
import { extractMavenDependencies } from './mavenManifest';
import { extractNpmDependencies } from './packageJson';
export { dependencyAtOffset } from './dependency';

export interface ManifestAdapter {
  kind: ManifestKind;
  fileName: string;
  notesRelativePath: string;
  extractDependencies(text: string): DependencyEntry[];
  normalizeNoteKey(raw: string): string;
}

function tolerantKey(raw: string): string {
  let value = raw.trim();
  const quote = /^(`|"|')(.*)\1$/.exec(value);
  if (quote?.[2] !== undefined) value = quote[2].trim();
  return value;
}

export const MANIFEST_ADAPTERS: readonly ManifestAdapter[] = [
  {
    kind: 'npm',
    fileName: 'package.json',
    notesRelativePath: '.pacmon/DEPENDENCY-NOTES.md',
    extractDependencies: extractNpmDependencies,
    normalizeNoteKey: (raw) => tolerantKey(raw).toLowerCase(),
  },
  {
    kind: 'cargo',
    fileName: 'Cargo.toml',
    notesRelativePath: '.pacmon/cargo/DEPENDENCY-NOTES.md',
    extractDependencies: extractCargoDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'maven',
    fileName: 'pom.xml',
    notesRelativePath: '.pacmon/maven/DEPENDENCY-NOTES.md',
    extractDependencies: extractMavenDependencies,
    normalizeNoteKey: tolerantKey,
  },
];

export function manifestAdapterForFileName(fileName: string): ManifestAdapter | undefined {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.fileName === fileName);
}

export function manifestAdapterForKind(kind: ManifestKind): ManifestAdapter {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.kind === kind)!;
}
