import { describe, expect, it } from 'vitest';
import { dependencyAtOffset, manifestAdapterForPath } from '../../core/manifest';
import { extractGleamDependencies } from '../../core/gleam-manifest';

describe('Gleam dependencies', () => {
  const manifest = String.raw`name = "example"
version = "1.0.0"

[dependencies]
gleam_stdlib = ">= 0.44.0 and < 2.0.0"
gleam_http = { hex = "gleam_http", version = "~> 4.0" }
local_package = { path = "../local_package" }
git_package = {
  git = "https://example.test/git_package.git",
  ref = "0123456789abcdef"
}
"quoted.package" = "~> 1.0"

[dev_dependencies]
gleeunit = ">= 1.0.0 and < 2.0.0"

[javascript]
typescript_declarations = true`;

  it('matches gleam.toml but not the lock file', () => {
    expect(manifestAdapterForPath('/repo/gleam.toml')?.kind).toBe('gleam');
    expect(manifestAdapterForPath('/repo/manifest.toml')).toBeUndefined();
  });

  it('extracts version, Hex, path, Git and development dependencies', () => {
    expect(extractGleamDependencies(manifest).map((dependency) => `${dependency.scope}:${dependency.noteKey}`))
      .toEqual([
        'dependencies:gleam_stdlib',
        'dependencies:gleam_http',
        'dependencies:local_package',
        'dependencies:git_package',
        'dependencies:quoted.package',
        'dev_dependencies:gleeunit',
      ]);
  });

  it('keeps dependency keys clickable and uses them as marker anchors', () => {
    const dependencies = extractGleamDependencies(manifest);
    const stdlib = dependencies[0]!;
    expect(manifest.slice(stdlib.primaryRange.offset, stdlib.primaryRange.offset + stdlib.primaryRange.length))
      .toBe('gleam_stdlib');
    expect(stdlib.iconRange).toEqual(stdlib.primaryRange);
    expect(dependencyAtOffset(dependencies, stdlib.primaryRange.offset + 2)?.noteKey).toBe('gleam_stdlib');

    const quoted = dependencies.find((dependency) => dependency.noteKey === 'quoted.package')!;
    expect(manifest.slice(quoted.primaryRange.offset, quoted.primaryRange.offset + quoted.primaryRange.length))
      .toBe('"quoted.package"');
  });

  it('ignores comments, unrelated tables and assignment-like text in values', () => {
    const text = String.raw`# [dependencies]
# commented = "1"
description = "fake = dependency"

[repository]
type = "github"
repo = "not_a_dependency"

[dependencies]
real_package = { git = "https://example.test/fake = value.git" }

[erlang]
application_start_module = "ignored"`;
    expect(extractGleamDependencies(text).map((dependency) => dependency.noteKey)).toEqual(['real_package']);
  });

  it('deduplicates within a scope and keeps safely parsed entries before a malformed tail', () => {
    const text = `[dependencies]\nfirst = "1"\nfirst = "2"\nbroken = {\n`;
    expect(extractGleamDependencies(text).map((dependency) => dependency.noteKey)).toEqual(['first', 'broken']);
  });
});
