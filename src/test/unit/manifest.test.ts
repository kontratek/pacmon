import { describe, expect, it } from 'vitest';
import { extractCargoDependencies } from '../../core/cargoManifest';
import { dependencyAtOffset, manifestAdapterForFileName } from '../../core/manifest';
import { extractMavenDependencies } from '../../core/mavenManifest';

describe('manifest adapters', () => {
  it('matches only the three supported manifest names', () => {
    expect(manifestAdapterForFileName('package.json')?.kind).toBe('npm');
    expect(manifestAdapterForFileName('Cargo.toml')?.kind).toBe('cargo');
    expect(manifestAdapterForFileName('pom.xml')?.kind).toBe('maven');
    expect(manifestAdapterForFileName('build.gradle')).toBeUndefined();
  });
});

describe('Cargo.toml dependencies', () => {
  const cargo = String.raw`[dependencies]
serde = "1"
"quoted.dep" = "1"
local = { package = "upstream", version = "2" } # alias
multi = {
  package = "multiline-upstream",
  version = "3"
}
workspace-crate.workspace = true

[dev-dependencies]
pretty_assertions = "1"

[build-dependencies.cc]
version = "1"

[target.'cfg(unix)'.dependencies]
nix = "0.29"

[target."cfg(windows)".build-dependencies.winres]
version = "0.1"

[workspace.dependencies]
not-direct = "1"
`;

  it('extracts direct, table, target and workspace-reference declarations', () => {
    const deps = extractCargoDependencies(cargo);
    expect(deps.map((dep) => `${dep.scope}:${dep.noteKey}`)).toEqual([
      'dependencies:serde',
      'dependencies:quoted.dep',
      'dependencies:local',
      'dependencies:multi',
      'dependencies:workspace-crate',
      'dev-dependencies:pretty_assertions',
      'build-dependencies:cc',
      'target:cfg(unix)/dependencies:nix',
      'target:cfg(windows)/build-dependencies:winres',
    ]);
  });

  it('keeps aliases as note keys and source ranges point to their manifest keys', () => {
    const deps = extractCargoDependencies(cargo);
    const alias = deps.find((dep) => dep.noteKey === 'local')!;
    expect(cargo.slice(alias.primaryRange.offset, alias.primaryRange.offset + alias.primaryRange.length)).toBe('local');
    expect(dependencyAtOffset(deps, alias.primaryRange.offset + 1)?.noteKey).toBe('local');
  });

  it('fails safely on malformed input', () => {
    expect(extractCargoDependencies('[dependencies\nserde = "1"')).toEqual([]);
  });

});

describe('pom.xml dependencies', () => {
  const pom = `<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
  </dependencies>
  <dependencyManagement><dependencies><dependency>
    <groupId>managed</groupId><artifactId>not-direct</artifactId>
  </dependency></dependencies></dependencyManagement>
  <build><plugins><plugin><dependencies><dependency>
    <groupId>plugin</groupId><artifactId>not-direct</artifactId>
  </dependency></dependencies></plugin></plugins></build>
  <profiles><profile><id>native</id><dependencies><dependency>
    <groupId>\${native.group}</groupId><artifactId>bridge</artifactId><scope>runtime</scope>
  </dependency></dependencies></profile></profiles>
</project>`;

  it('extracts project and profile dependencies but excludes management and plugins', () => {
    expect(extractMavenDependencies(pom).map((dep) => `${dep.scope}:${dep.noteKey}`)).toEqual([
      'compile:org.slf4j:slf4j-api',
      'profile:native/runtime:${native.group}:bridge',
    ]);
  });

  it('makes groupId and artifactId hoverable and decorates artifactId', () => {
    const deps = extractMavenDependencies(pom);
    const slf4j = deps[0]!;
    expect(slf4j.sourceRanges).toHaveLength(2);
    expect(pom.slice(slf4j.primaryRange.offset, slf4j.primaryRange.offset + slf4j.primaryRange.length)).toBe('slf4j-api');
    const groupOffset = pom.indexOf('org.slf4j');
    expect(dependencyAtOffset(deps, groupOffset + 1)?.noteKey).toBe('org.slf4j:slf4j-api');
  });

  it('decodes XML text while retaining source ranges', () => {
    const xml = `<project><dependencies><dependency>
      <groupId><![CDATA[org.example]]></groupId>
      <artifactId>client&amp;api</artifactId>
    </dependency></dependencies></project>`;
    const dependency = extractMavenDependencies(xml)[0]!;
    expect(dependency.noteKey).toBe('org.example:client&api');
    expect(xml.slice(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length))
      .toBe('client&amp;api');
  });

  it('fails safely on malformed input', () => {
    expect(extractMavenDependencies('<not-project><dependency>')).toEqual([]);
  });
});
