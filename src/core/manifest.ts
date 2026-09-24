import type { DependencyEntry, ManifestKind } from './model';
import { extractCargoDependencies } from './cargoManifest';
import { extractMavenDependencies } from './mavenManifest';
import { extractNpmDependencies } from './packageJson';
import { extractGradleDependencies } from './gradleManifest';
import { extractMixDependencies } from './mixManifest';
import { extractZigDependencies } from './zig-manifest';
import {
  extractPyprojectDependencies,
  extractRequirementsDependencies,
  normalizePythonPackageName,
  pythonRequirementsScope,
} from './python-manifest';
export { dependencyAtOffset } from './dependency';

export interface ManifestAdapter {
  kind: ManifestKind;
  /** Supported names in default-selection order. */
  fileNames: readonly string[];
  /** Workspace globs used for discovery, selection, and file watching. */
  discoveryGlobs: readonly string[];
  notesRelativePath: string;
  matchesPath(path: string): boolean;
  extractDependencies(text: string, path?: string): DependencyEntry[];
  normalizeNoteKey(raw: string): string;
}

function tolerantKey(raw: string): string {
  let value = raw.trim();
  const quote = /^(`|"|')(.*)\1$/.exec(value);
  if (quote?.[2] !== undefined) value = quote[2].trim();
  return value;
}

function normalizedPath(path: string): string {
  return path.replace(/\\/g, '/');
}

function pathBasename(path: string): string {
  return normalizedPath(path).split('/').at(-1) ?? '';
}

function exactPathMatcher(fileNames: readonly string[]): (path: string) => boolean {
  return (path) => fileNames.includes(pathBasename(path));
}

function exactDiscoveryGlobs(fileNames: readonly string[]): string[] {
  return fileNames.map((fileName) => `**/${fileName}`);
}

export const MANIFEST_ADAPTERS: readonly ManifestAdapter[] = [
  {
    kind: 'npm',
    fileNames: ['package.json'],
    discoveryGlobs: exactDiscoveryGlobs(['package.json']),
    notesRelativePath: '.pacmon/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['package.json']),
    extractDependencies: extractNpmDependencies,
    normalizeNoteKey: (raw) => tolerantKey(raw).toLowerCase(),
  },
  {
    kind: 'cargo',
    fileNames: ['Cargo.toml'],
    discoveryGlobs: exactDiscoveryGlobs(['Cargo.toml']),
    notesRelativePath: '.pacmon/cargo/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['Cargo.toml']),
    extractDependencies: extractCargoDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'maven',
    fileNames: ['pom.xml'],
    discoveryGlobs: exactDiscoveryGlobs(['pom.xml']),
    notesRelativePath: '.pacmon/maven/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['pom.xml']),
    extractDependencies: extractMavenDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'gradle',
    fileNames: ['build.gradle.kts', 'build.gradle'],
    discoveryGlobs: exactDiscoveryGlobs(['build.gradle.kts', 'build.gradle']),
    notesRelativePath: '.pacmon/gradle/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['build.gradle.kts', 'build.gradle']),
    extractDependencies: extractGradleDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'mix',
    fileNames: ['mix.exs'],
    discoveryGlobs: exactDiscoveryGlobs(['mix.exs']),
    notesRelativePath: '.pacmon/mix/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['mix.exs']),
    extractDependencies: extractMixDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'zig',
    fileNames: ['build.zig.zon'],
    discoveryGlobs: exactDiscoveryGlobs(['build.zig.zon']),
    notesRelativePath: '.pacmon/zig/DEPENDENCY-NOTES.md',
    matchesPath: exactPathMatcher(['build.zig.zon']),
    extractDependencies: extractZigDependencies,
    normalizeNoteKey: tolerantKey,
  },
  {
    kind: 'python',
    fileNames: ['pyproject.toml', 'requirements.txt'],
    discoveryGlobs: ['**/pyproject.toml', '**/requirements*.txt', '**/requirements/**/*.txt'],
    notesRelativePath: '.pacmon/python/DEPENDENCY-NOTES.md',
    matchesPath: (path) => {
      const normalized = normalizedPath(path);
      const basename = pathBasename(normalized);
      return basename === 'pyproject.toml'
        || /^requirements.*\.txt$/.test(basename)
        || (basename.endsWith('.txt') && normalized.split('/').includes('requirements'));
    },
    extractDependencies: (text, path = 'pyproject.toml') => pathBasename(path) === 'pyproject.toml'
      ? extractPyprojectDependencies(text)
      : extractRequirementsDependencies(text, pythonRequirementsScope(path)),
    normalizeNoteKey: (raw) => normalizePythonPackageName(tolerantKey(raw)),
  },
];

export function manifestAdapterForFileName(fileName: string): ManifestAdapter | undefined {
  return manifestAdapterForPath(fileName);
}

export function manifestAdapterForPath(path: string): ManifestAdapter | undefined {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.matchesPath(path));
}

export function manifestAdapterForKind(kind: ManifestKind): ManifestAdapter {
  return MANIFEST_ADAPTERS.find((adapter) => adapter.kind === kind)!;
}
