import { dependencyEntry } from './dependency';
import type { DependencyEntry } from './model';
import { parseXml, xmlChild, xmlTextValue, type XmlNode } from './xml';

function dependenciesFrom(
  text: string,
  dependencies: XmlNode | undefined,
  scopePrefix?: string,
): DependencyEntry[] {
  if (!dependencies) return [];
  const out: DependencyEntry[] = [];
  for (const dependency of dependencies.children.filter((node) => node.name === 'dependency')) {
    const group = xmlTextValue(text, xmlChild(dependency, 'groupId'));
    const artifact = xmlTextValue(text, xmlChild(dependency, 'artifactId'));
    if (!group || !artifact) continue;
    const declaredScope = xmlTextValue(text, xmlChild(dependency, 'scope'))?.value ?? 'compile';
    const scope = scopePrefix ? `${scopePrefix}/${declaredScope}` : declaredScope;
    const noteKey = `${group.value}:${artifact.value}`;
    const iconRange = { offset: dependency.openStart, length: 1 };
    out.push(dependencyEntry(noteKey, scope, artifact.range, [group.range, artifact.range], noteKey, iconRange));
  }
  return out;
}

/** Extracts direct Maven dependencies without resolving parents or invoking Maven. */
export function extractMavenDependencies(text: string): DependencyEntry[] {
  const project = parseXml(text).find((node) => node.name === 'project');
  if (!project) return [];
  const out = dependenciesFrom(text, xmlChild(project, 'dependencies'));
  const profiles = xmlChild(project, 'profiles');
  for (const profile of profiles?.children.filter((node) => node.name === 'profile') ?? []) {
    const id = xmlTextValue(text, xmlChild(profile, 'id'))?.value ?? 'unnamed';
    out.push(...dependenciesFrom(text, xmlChild(profile, 'dependencies'), `profile:${id}`));
  }
  return out;
}
