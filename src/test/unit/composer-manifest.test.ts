import { describe, expect, it } from 'vitest';
import { analyze } from '../../core/analyze';
import { extractComposerDependencies } from '../../core/composer-manifest';
import { dependencyAtOffset, manifestAdapterForPath } from '../../core/manifest';
import { parseNotes } from '../../core/parseNotes';

describe('Composer manifest matching', () => {
  it('matches composer.json only', () => {
    expect(manifestAdapterForPath('/repo/composer.json')?.kind).toBe('composer');
    expect(manifestAdapterForPath('/repo/composer.lock')).toBeUndefined();
  });
});

describe('Composer dependencies', () => {
  const manifest = `{
  // JSONC is accepted for editor tolerance.
  "require": {
    "php": "^8.3",
    "ext-mbstring": "*",
    "lib-curl": ">=7.0",
    "composer-runtime-api": "^2.2",
    "Monolog/Monolog": "^3.0",
  },
  "require-dev": {
    "phpunit/phpunit": "^11.0"
  },
  "provide": { "psr/log-implementation": "3.0" },
  "replace": { "legacy/package": "self.version" },
  "conflict": { "bad/package": "<2" },
  "suggest": { "optional/package": "Optional integration" }
}`;

  it('extracts runtime, development and platform requirements', () => {
    expect(extractComposerDependencies(manifest).map((dependency) =>
      `${dependency.scope}:${dependency.noteKey}:${dependency.displayName}`)).toEqual([
      'require:php:php',
      'require:ext-mbstring:ext-mbstring',
      'require:lib-curl:lib-curl',
      'require:composer-runtime-api:composer-runtime-api',
      'require:monolog/monolog:Monolog/Monolog',
      'require-dev:phpunit/phpunit:phpunit/phpunit',
    ]);
  });

  it('ignores package links that do not install direct dependencies', () => {
    const names = extractComposerDependencies(manifest).map((dependency) => dependency.noteKey);
    expect(names).not.toContain('psr/log-implementation');
    expect(names).not.toContain('legacy/package');
    expect(names).not.toContain('bad/package');
    expect(names).not.toContain('optional/package');
  });

  it('supports every Composer platform package family from the plan', () => {
    const text = JSON.stringify({
      require: {
        php: '^8.3',
        'php-64bit': '*',
        'php-ipv6': '*',
        'php-zts': '*',
        'php-debug': '*',
        'php-future-capability': '*',
        hhvm: '*',
        'ext-json': '*',
        'lib-iconv': '*',
        composer: '^2.7',
        'composer-plugin-api': '^2.6',
        'composer-runtime-api': '^2.2',
      },
    });
    expect(extractComposerDependencies(text).map((dependency) => dependency.noteKey)).toEqual([
      'php',
      'php-64bit',
      'php-ipv6',
      'php-zts',
      'php-debug',
      'php-future-capability',
      'hhvm',
      'ext-json',
      'lib-iconv',
      'composer',
      'composer-plugin-api',
      'composer-runtime-api',
    ]);
  });

  it('returns exact clickable package and icon ranges', () => {
    const dependencies = extractComposerDependencies(manifest);
    for (const dependency of dependencies) {
      expect(manifest.slice(
        dependency.primaryRange.offset,
        dependency.primaryRange.offset + dependency.primaryRange.length,
      )).toBe(dependency.displayName);
      expect(manifest[dependency.iconRange.offset]).toBe('"');
      expect(dependencyAtOffset(dependencies, dependency.primaryRange.offset + 1))
        .toBe(dependency);
    }
  });

  it('keeps valid declarations from malformed JSON and ignores invalid entries', () => {
    const malformed = '{ "require": { "vendor/one": "^1", "bad name": "*", "vendor/two": "^2" ';
    expect(extractComposerDependencies(malformed).map((dependency) => dependency.noteKey))
      .toEqual(['vendor/one', 'vendor/two']);
    expect(extractComposerDependencies('{ "require": [], "require-dev": { "vendor/object": {} } }'))
      .toEqual([]);
  });

  it('matches Composer note headings case-insensitively', () => {
    const notes = parseNotes('---\nformat: dependency-notes/2\necosystem: composer\n---\n# Dependency Notes\n\n## MONOLOG/MONOLOG\n\nLogging.\n');
    const dependency = extractComposerDependencies('{"require":{"monolog/monolog":"^3"}}')[0]!;
    expect(analyze([dependency], notes).documented).toEqual([dependency]);
  });
});
