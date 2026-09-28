import { dependencyEntry } from './dependency';
import type { DependencyEntry } from './model';
import { parseXml, xmlAttribute, type XmlNode } from './xml';

const PROJECT_EXTENSIONS = ['.csproj', '.fsproj', '.vbproj'] as const;
const NUGET_PACKAGE_ID = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

function basename(path: string): string {
  return path.replace(/\\/g, '/').split('/').at(-1) ?? '';
}

export function isNugetManifestPath(path: string): boolean {
  const name = basename(path);
  const lower = name.toLowerCase();
  return name === 'Directory.Packages.props'
    || PROJECT_EXTENSIONS.some((extension) => lower.endsWith(extension));
}

function descendants(nodes: readonly XmlNode[]): XmlNode[] {
  const result: XmlNode[] = [];
  const visit = (node: XmlNode): void => {
    result.push(node);
    node.children.forEach(visit);
  };
  nodes.forEach(visit);
  return result;
}

function entry(node: XmlNode, attributeName: 'Include' | 'Update', scope: string): DependencyEntry | undefined {
  if (xmlAttribute(node, 'Remove')) return undefined;
  const attribute = xmlAttribute(node, attributeName);
  if (!attribute || !NUGET_PACKAGE_ID.test(attribute.value)) return undefined;
  const iconRange = { offset: node.openStart, length: 1 };
  return dependencyEntry(
    attribute.value,
    scope,
    attribute.range,
    [attribute.range],
    attribute.value,
    iconRange,
  );
}

/** Reads literal NuGet declarations without evaluating MSBuild or following imports. */
export function extractNugetDependencies(text: string, path = 'project.csproj'): DependencyEntry[] {
  const nodes = descendants(parseXml(text));
  if (basename(path) === 'Directory.Packages.props') {
    return nodes.flatMap((node) => {
      if (node.name === 'PackageVersion') {
        const attributeName = xmlAttribute(node, 'Include') ? 'Include' : 'Update';
        const dependency = entry(node, attributeName, 'centralVersion');
        return dependency ? [dependency] : [];
      }
      if (node.name === 'GlobalPackageReference') {
        const dependency = entry(node, 'Include', 'globalPackageReference');
        return dependency ? [dependency] : [];
      }
      return [];
    });
  }

  return nodes.flatMap((node) => {
    if (node.name !== 'PackageReference' || !xmlAttribute(node, 'Include')) return [];
    const dependency = entry(node, 'Include', 'packageReference');
    return dependency ? [dependency] : [];
  });
}

/** Coverage uses one row per case-insensitive NuGet package ID; callers pass
 * central manifests first so their declaration represents duplicates. */
export function uniqueNugetDependencies(dependencies: readonly DependencyEntry[]): DependencyEntry[] {
  const unique = new Map<string, DependencyEntry>();
  for (const dependency of dependencies) {
    const key = dependency.noteKey.trim().toLowerCase();
    if (!unique.has(key)) unique.set(key, dependency);
  }
  return [...unique.values()];
}
