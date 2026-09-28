import { describe, expect, it } from 'vitest';
import { analyze } from '../../core/analyze';
import { dependencyEntry } from '../../core/dependency';
import { manifestAdapterForPath } from '../../core/manifest';
import {
  extractNugetDependencies,
  isNugetManifestPath,
  uniqueNugetDependencies,
} from '../../core/nuget-manifest';
import { parseNotes } from '../../core/parseNotes';

describe('NuGet manifest matching', () => {
  it('supports C#, F#, VB and central package manifests only', () => {
    expect(manifestAdapterForPath('/repo/App.csproj')?.kind).toBe('nuget');
    expect(manifestAdapterForPath('/repo/App.fsproj')?.kind).toBe('nuget');
    expect(manifestAdapterForPath('/repo/App.vbproj')?.kind).toBe('nuget');
    expect(manifestAdapterForPath('/repo/Directory.Packages.props')?.kind).toBe('nuget');
    expect(isNugetManifestPath('/repo/App.CSPROJ')).toBe(true);
    expect(isNugetManifestPath('/repo/Directory.Build.props')).toBe(false);
    expect(isNugetManifestPath('/repo/packages.config')).toBe(false);
  });
});

describe('NuGet project dependencies', () => {
  const project = `<Project Sdk="Microsoft.NET.Sdk" xmlns:x="urn:test">
  <ItemGroup Condition="'$(TargetFramework)' == 'net9.0'">
    <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
    <x:PackageReference
      Include="Serilog.AspNetCore">
      <Version>9.0.0</Version>
    </x:PackageReference>
    <!-- <PackageReference Include="Commented.Package" /> -->
    <PackageReference Update="Imported.Package" Version="2" />
    <PackageReference Include="Removed.Package" Remove="Removed.Package" />
    <PackageReference Include="$(DynamicPackage)" />
    <ProjectReference Include="../Shared/Shared.csproj" />
    <FrameworkReference Include="Microsoft.AspNetCore.App" />
    <PackageDownload Include="Tool.Package" Version="[1.0.0]" />
  </ItemGroup>
</Project>`;

  it('extracts literal Include declarations with precise ranges', () => {
    const dependencies = extractNugetDependencies(project, '/repo/App.csproj');
    expect(dependencies.map((dependency) => `${dependency.scope}:${dependency.noteKey}`)).toEqual([
      'packageReference:Newtonsoft.Json',
      'packageReference:Serilog.AspNetCore',
    ]);
    for (const dependency of dependencies) {
      expect(project.slice(
        dependency.primaryRange.offset,
        dependency.primaryRange.offset + dependency.primaryRange.length,
      )).toBe(dependency.displayName);
      expect(project[dependency.iconRange.offset]).toBe('<');
    }
  });

  it('keeps valid declarations before malformed XML without throwing', () => {
    const malformed = '<Project><ItemGroup><PackageReference Include="Good.Package" /><PackageReference Include=';
    expect(extractNugetDependencies(malformed, 'App.csproj').map((dependency) => dependency.noteKey))
      .toEqual(['Good.Package']);
    expect(extractNugetDependencies(
      '<Project><PackageReference Include="Bad&#999999999;Package" /></Project>',
      'App.csproj',
    )).toEqual([]);
  });

  it('decodes XML entities in literal package IDs while retaining the source range', () => {
    const text = '<Project><ItemGroup><PackageReference Include="Entity&#46;Package" /></ItemGroup></Project>';
    const dependency = extractNugetDependencies(text, 'App.csproj')[0]!;
    expect(dependency.noteKey).toBe('Entity.Package');
    expect(text.slice(
      dependency.primaryRange.offset,
      dependency.primaryRange.offset + dependency.primaryRange.length,
    )).toBe('Entity&#46;Package');
  });
});

describe('central NuGet dependencies', () => {
  const central = `<Project xmlns:n="urn:test">
  <ItemGroup>
    <PackageVersion Include="Newtonsoft.Json" Version="13.0.3" />
    <n:PackageVersion Update="Serilog" Version="4.2.0" Condition="'$(TargetFramework)' == 'net9.0'" />
    <GlobalPackageReference Include="Nerdbank.GitVersioning" Version="3.7.115" />
    <PackageVersion Include="Removed.Package" Remove="Removed.Package" />
    <PackageVersion Include="$(DynamicPackage)" Version="1" />
    <PackageDownload Include="Download.Only" Version="[1.0.0]" />
  </ItemGroup>
</Project>`;

  it('extracts all literal central and global declarations', () => {
    expect(extractNugetDependencies(central, 'Directory.Packages.props')
      .map((dependency) => `${dependency.scope}:${dependency.noteKey}`)).toEqual([
      'centralVersion:Newtonsoft.Json',
      'centralVersion:Serilog',
      'globalPackageReference:Nerdbank.GitVersioning',
    ]);
  });

  it('deduplicates coverage case-insensitively with the first, central entry winning', () => {
    const range = { offset: 0, length: 1 };
    const dependencies = [
      dependencyEntry('Newtonsoft.Json', 'centralVersion', range),
      dependencyEntry('newtonsoft.json', 'packageReference', range),
      dependencyEntry('Serilog', 'packageReference', range),
    ];
    expect(uniqueNugetDependencies(dependencies).map((dependency) => dependency.scope))
      .toEqual(['centralVersion', 'packageReference']);
  });

  it('matches note headings case-insensitively while preserving their spelling', () => {
    const notes = parseNotes([
      '---',
      'format: dependency-notes/2',
      'ecosystem: nuget',
      'lang: en',
      '---',
      '# Dependency Notes',
      '',
      '## newtonsoft.json',
      '',
      'JSON serialization.',
    ].join('\n'));
    const dependency = dependencyEntry('Newtonsoft.Json', 'packageReference', { offset: 0, length: 1 });
    expect(analyze([dependency], notes).documented).toEqual([dependency]);
    expect(notes.sections[0]?.name).toBe('newtonsoft.json');
  });
});
