import { parseTree, type Node as JsonNode } from 'jsonc-parser';
import { dependencyEntry } from './dependency';
import type { DependencyEntry, SourceRange } from './model';

const PACKAGE_NAME = /^[A-Za-z0-9][A-Za-z0-9-]*$/;

function property(node: JsonNode, name: string): JsonNode | undefined {
  if (node.type !== 'object') return undefined;
  for (const child of node.children ?? []) {
    if (child.type !== 'property') continue;
    const key = child.children?.[0];
    if (key?.value === name) return child.children?.[1];
  }
  return undefined;
}

function stringContentRange(node: JsonNode): SourceRange {
  return node.type === 'string' && node.length >= 2
    ? { offset: node.offset + 1, length: node.length - 2 }
    : { offset: node.offset, length: node.length };
}

function dependencyFrom(node: JsonNode, scope: string): DependencyEntry | undefined {
  let nameNode: JsonNode | undefined;
  let host = false;
  if (node.type === 'string') nameNode = node;
  else if (node.type === 'object') {
    nameNode = property(node, 'name');
    host = property(node, 'host')?.value === true;
  }
  if (nameNode?.type !== 'string' || typeof nameNode.value !== 'string' || !PACKAGE_NAME.test(nameNode.value)) {
    return undefined;
  }
  const range = stringContentRange(nameNode);
  const iconRange = { offset: node.offset, length: 1 };
  const displayName = nameNode.value;
  return dependencyEntry(
    displayName.toLowerCase(),
    host ? `${scope}:host` : scope,
    range,
    [range],
    displayName,
    iconRange,
  );
}

function dependenciesFrom(node: JsonNode | undefined, scope: string): DependencyEntry[] {
  if (node?.type !== 'array') return [];
  return (node.children ?? []).flatMap((child) => {
    const dependency = dependencyFrom(child, scope);
    return dependency ? [dependency] : [];
  });
}

/** Reads direct dependency declarations from vcpkg.json without invoking vcpkg. */
export function extractVcpkgDependencies(text: string): DependencyEntry[] {
  const root = parseTree(text, [], { allowTrailingComma: true });
  if (!root || root.type !== 'object') return [];
  const out = dependenciesFrom(property(root, 'dependencies'), 'dependencies');
  const features = property(root, 'features');
  if (features?.type !== 'object') return out;
  for (const featureProperty of features.children ?? []) {
    if (featureProperty.type !== 'property') continue;
    const key = featureProperty.children?.[0];
    const feature = featureProperty.children?.[1];
    if (key?.type !== 'string' || typeof key.value !== 'string' || feature?.type !== 'object') continue;
    out.push(...dependenciesFrom(property(feature, 'dependencies'), `feature:${key.value}`));
  }
  return out;
}
