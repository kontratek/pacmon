import type { DependencyEntry, ManifestKind } from './model';
import { extractCargoDependencies } from './cargoManifest';
import { extractMavenDependencies } from './mavenManifest';
import { extractNpmDependencies } from './packageJson';
import { extractGradleDependencies } from './gradleManifest';
import { extractMixDependencies } from './mixManifest';
import { extractZigDependencies } from './zig-manifest';
export { dependencyAtOffset } from './dependency';

export interface ManifestAdapter {
  kind: ManifestKind;
  /** Supported names in default-selection order. */
  fileNames: readonly string[];
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
    fileNames: ['package.json'],
    notesRelativePath: '.pacmon/DEPENDENCY-NOTES.md',
    extractDependencies: extractNpmDependencies,
    normalizeNoteKey: (raw) => tolerantKey(raw).toLowerCase(),
  },
  {
    kind: 'cargo',
    fileNames: ['Cargo.toml'],
    notesRelativePath: '.pacmon/cargo/DEPENDENCY-NOTES.md',
    extractDependencies: extractCargoDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'maven',
    fileNames: ['pom.xml'],
    notesRelativePath: '.pacmon/maven/DEPENDENCY-NOTES.md',
    extractDependencies: extractMavenDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'gradle',
    fileNames: ['build.gradle.kts', 'build.gradle'],
    notesRelativePath: '.pacmon/gradle/DEPENDENCY-NOTES.md',
    extractDependencies: extractGradleDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'mix',
    fileNames: ['mix.exs'],
    notesRelativePath: '.pacmon/mix/DEPENDENCY-NOTES.md',
    extractDependencies: extractMixDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'zig',
    fileNames: ['build.zig.zon'],
    notesRelativePath: '.pacmon/zig/DEPENDENCY-NOTES.md',
    extractDependencies: extractZigDependencies,
    normalizeNoteKey: tolerantKey,
  },
];

export function manifestAdapterForFileName(fileName: string): ManifestAdapter | undefined {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.fileNames.includes(fileName));
}

export function manifestAdapterForKind(kind: ManifestKind): ManifestAdapter {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.kind === kind)!;
}
