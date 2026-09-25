import type { SourceRange } from './model';

export interface XmlAttribute {
  name: string;
  value: string;
  range: SourceRange;
}

export interface XmlNode {
  name: string;
  openStart: number;
  openEnd: number;
  closeStart: number;
  attributes: XmlAttribute[];
  children: XmlNode[];
}

function localName(name: string): string {
  return name.slice(name.lastIndexOf(':') + 1);
}

export function decodeXml(value: string): string {
  return value.replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (entity, body: string) => {
    const lower = body.toLowerCase();
    if (lower === 'amp') return '&';
    if (lower === 'lt') return '<';
    if (lower === 'gt') return '>';
    if (lower === 'quot') return '"';
    if (lower === 'apos') return "'";
    const radix = lower.startsWith('#x') ? 16 : 10;
    const digits = lower.slice(radix === 16 ? 2 : 1);
    const code = Number.parseInt(digits, radix);
    return Number.isInteger(code) && code >= 0 && code <= 0x10FFFF
      ? String.fromCodePoint(code)
      : entity;
  });
}

function attributesIn(text: string, start: number, end: number): XmlAttribute[] {
  const attributes: XmlAttribute[] = [];
  let cursor = start;
  while (cursor < end) {
    while (cursor < end && /\s/.test(text[cursor]!)) cursor++;
    if (cursor >= end || text[cursor] === '/') break;
    const nameStart = cursor;
    while (cursor < end && !/[\s=/]/.test(text[cursor]!)) cursor++;
    const rawName = text.slice(nameStart, cursor);
    while (cursor < end && /\s/.test(text[cursor]!)) cursor++;
    if (!rawName || text[cursor] !== '=') {
      while (cursor < end && !/\s/.test(text[cursor]!) && text[cursor] !== '/') cursor++;
      continue;
    }
    cursor++;
    while (cursor < end && /\s/.test(text[cursor]!)) cursor++;
    const quote = text[cursor];
    if (quote !== '"' && quote !== "'") continue;
    cursor++;
    const valueStart = cursor;
    while (cursor < end && text[cursor] !== quote) cursor++;
    if (cursor >= end) break;
    const rawValue = text.slice(valueStart, cursor);
    const leading = rawValue.length - rawValue.trimStart().length;
    const trimmed = rawValue.trim();
    attributes.push({
      name: localName(rawName),
      value: decodeXml(trimmed),
      range: { offset: valueStart + leading, length: trimmed.length },
    });
    cursor++;
  }
  return attributes;
}

/** Small, tolerant XML reader used by static manifest adapters. */
export function parseXml(text: string): XmlNode[] {
  const roots: XmlNode[] = [];
  const stack: XmlNode[] = [];
  let cursor = 0;
  while (cursor < text.length) {
    const open = text.indexOf('<', cursor);
    if (open < 0) break;
    if (text.startsWith('<!--', open)) {
      const end = text.indexOf('-->', open + 4);
      cursor = end < 0 ? text.length : end + 3;
      continue;
    }
    if (text.startsWith('<![CDATA[', open)) {
      const end = text.indexOf(']]>', open + 9);
      cursor = end < 0 ? text.length : end + 3;
      continue;
    }
    if (text.startsWith('<?', open)) {
      const end = text.indexOf('?>', open + 2);
      cursor = end < 0 ? text.length : end + 2;
      continue;
    }
    if (text.startsWith('<!', open)) {
      const end = text.indexOf('>', open + 2);
      cursor = end < 0 ? text.length : end + 1;
      continue;
    }

    let quote: '"' | "'" | undefined;
    let end = open + 1;
    for (; end < text.length; end++) {
      const char = text[end]!;
      if (quote) {
        if (char === quote) quote = undefined;
      } else if (char === '"' || char === "'") quote = char;
      else if (char === '>') break;
    }
    if (end >= text.length) break;

    let contentStart = open + 1;
    while (contentStart < end && /\s/.test(text[contentStart]!)) contentStart++;
    if (text[contentStart] === '/') {
      contentStart++;
      while (contentStart < end && /\s/.test(text[contentStart]!)) contentStart++;
      let nameEnd = contentStart;
      while (nameEnd < end && !/\s/.test(text[nameEnd]!)) nameEnd++;
      const name = localName(text.slice(contentStart, nameEnd));
      for (let i = stack.length - 1; i >= 0; i--) {
        if (stack[i]!.name !== name) continue;
        stack[i]!.closeStart = open;
        stack.length = i;
        break;
      }
    } else {
      let nameEnd = contentStart;
      while (nameEnd < end && !/[\s/]/.test(text[nameEnd]!)) nameEnd++;
      const rawName = text.slice(contentStart, nameEnd);
      if (rawName !== '') {
        const selfClosing = /\/\s*$/.test(text.slice(nameEnd, end));
        const node: XmlNode = {
          name: localName(rawName),
          openStart: open,
          openEnd: end + 1,
          closeStart: end + 1,
          attributes: attributesIn(text, nameEnd, end),
          children: [],
        };
        const parent = stack[stack.length - 1];
        if (parent) parent.children.push(node);
        else roots.push(node);
        if (!selfClosing) stack.push(node);
      }
    }
    cursor = end + 1;
  }
  for (const node of stack) node.closeStart = text.length;
  return roots;
}

export function xmlChild(node: XmlNode, name: string): XmlNode | undefined {
  return node.children.find((candidate) => candidate.name === name);
}

export function xmlAttribute(node: XmlNode, name: string): XmlAttribute | undefined {
  return node.attributes.find((candidate) => candidate.name === name);
}

export function xmlTextValue(
  text: string,
  node: XmlNode | undefined,
): { value: string; range: SourceRange } | undefined {
  if (!node) return undefined;
  const raw = text.slice(node.openEnd, node.closeStart);
  const plain = raw.replace(/<!--[^]*?-->/g, '').replace(/<!\[CDATA\[([^]*?)]]>/g, '$1');
  const trimmed = plain.trim();
  const value = decodeXml(trimmed);
  if (value === '') return undefined;
  const rawValueIndex = raw.indexOf(trimmed);
  const offset = rawValueIndex < 0 ? node.openEnd : node.openEnd + rawValueIndex;
  return { value, range: { offset, length: trimmed.length } };
}
