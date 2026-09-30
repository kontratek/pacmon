import { describe, expect, it } from 'vitest';
import { analyze } from '../../core/analyze';
import { manifestAdapterForPath } from '../../core/manifest';
import { parseNotes } from '../../core/parseNotes';
import { extractVcpkgDependencies } from '../../core/vcpkg-manifest';

describe('vcpkg manifest matching', () => {
  it('matches vcpkg.json only', () => {
    expect(manifestAdapterForPath('/repo/vcpkg.json')?.kind).toBe('vcpkg');
    expect(manifestAdapterForPath('/repo/vcpkg-configuration.json')).toBeUndefined();
  });
});

describe('vcpkg dependencies', () => {
  const manifest = `{
  // JSONC is accepted for editor tolerance.
  "dependencies": [
    "fmt",
    { "name": "OpenSSL", "host": true, "features": ["tools"] },
    { "name": "zlib", "default-features": false },
  ],
  "features": {
    "tests": {
      "description": "Test support",
      "dependencies": ["catch2", { "name": "pkgconf", "host": true }]
    }
  },
  "overrides": [{ "name": "ignored", "version": "1.0" }],
  "builtin-baseline": "deadbeef"
}`;

  it('extracts root, feature and host declarations', () => {
    expect(extractVcpkgDependencies(manifest).map((dependency) =>
      `${dependency.scope}:${dependency.noteKey}:${dependency.displayName}`)).toEqual([
      'dependencies:fmt:fmt',
      'dependencies:host:openssl:OpenSSL',
      'dependencies:zlib:zlib',
      'feature:tests:catch2:catch2',
      'feature:tests:host:pkgconf:pkgconf',
    ]);
  });

  it('returns exact package and icon source ranges', () => {
    for (const dependency of extractVcpkgDependencies(manifest)) {
      expect(manifest.slice(
        dependency.primaryRange.offset,
        dependency.primaryRange.offset + dependency.primaryRange.length,
      )).toBe(dependency.displayName);
      expect(['"', '{']).toContain(manifest[dependency.iconRange.offset]);
    }
  });

  it('keeps valid declarations from malformed JSON and ignores invalid entries', () => {
    const malformed = '{ "dependencies": ["fmt", 42, { "name": "bad name" }, { "name": "zlib" }';
    expect(extractVcpkgDependencies(malformed).map((dependency) => dependency.noteKey))
      .toEqual(['fmt', 'zlib']);
  });

  it('matches note headings case-insensitively', () => {
    const notes = parseNotes('---\nformat: dependency-notes/2\necosystem: vcpkg\n---\n# Dependency Notes\n\n## FMT\n\nFormatting.\n');
    const dependency = extractVcpkgDependencies('{"dependencies":["fmt"]}')[0]!;
    expect(analyze([dependency], notes).documented).toEqual([dependency]);
  });
});
