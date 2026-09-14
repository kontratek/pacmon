# The `dependency-notes/1` format

This document describes `.pacmon/DEPENDENCY-NOTES.md`. That file holds the notes a repository keeps about the dependencies of one `package.json`. This is version `1` of the format. See [Versioning](#versioning).

## The notes file

The file is `.pacmon/DEPENDENCY-NOTES.md`. It sits in the same directory as the `package.json` it describes.

A repository can have several `package.json` files, one inside another. A `package.json` without a notes file of its own uses the notes file of the closest ancestor directory that has one, up to the repository root.

The file is UTF-8. Its line ending is CRLF if the file contains one CRLF, otherwise LF. A byte order mark at the start is accepted; a tool that rewrites the file drops it.

The file has these parts, in this order: frontmatter, header comment, title, introduction, sections.

```md
---
format: dependency-notes/1
lang: en
---

<!-- Each "## name" below is a package from package.json. The text right under the
  heading is written by people. "### Agent notes" and everything below it is written
  by AI agents — rules in .pacmon/AGENT-RULES.md. -->

# Dependency Notes

Repository-wide rules, free text.

## <package name>

Human layer.

### Agent notes

- key: value
```

The frontmatter, the header comment and the title have fixed content, given below. A tool that formats the file rewrites them. The introduction is written by people. A section has layers: one is written by people, one by AI agents.

A fenced code block starts and ends with a line of three or more backticks or tildes, indented by at most three spaces. Inside a fenced code block, a line is never a heading and never a field.

## Frontmatter

The frontmatter is present when the first line of the file is `---`. It ends at the next line that is `---`. Each line between them is `key: value`. A key is a letter followed by letters, digits, `_` or `-`. A key with an empty value is not read.

The format defines two keys.

`format` names the version of these rules the file follows. Its value is `dependency-notes/1`. When the key is missing, the file is read as `dependency-notes/1`.

`lang` names the language the values are written in. Keys are always English. When the key is missing, the language is `en`.

Any other key is kept where it is and is not read.

## Header comment

The header comment is the first HTML comment at the top of the file, after the frontmatter if there is one. Its text is fixed: the three lines shown above. It says who writes where, and it points AI agents to `.pacmon/AGENT-RULES.md`. That file holds instructions for agents and is not part of this format. A file's own commentary goes in the introduction, not in the header comment.

## Title

The title is the only level-1 heading in the file. Its text is `# Dependency Notes`. A second level-1 heading is a mistake. A level-1 heading that names a dependency is a section at the wrong level.

## Introduction

The introduction is everything between the title and the first section. It holds the repository's own rules about dependencies. It is free text and is never parsed. It may contain headings of level 3 or deeper.

## Sections

A section starts with a level-2 heading: `## <name>`. `<name>` is the package name exactly as it appears in `package.json`, including `@scope/`. Level-2 headings are reserved for package names everywhere in the file.

A dependency is any key under `dependencies`, `devDependencies`, `peerDependencies` or `optionalDependencies` in `package.json`.

A section name matches a dependency name when the two are equal after both are trimmed, stripped of one pair of surrounding quotes or backticks, and lower-cased.

Each dependency has at most one section. Two sections with the same name are a mistake; the file does not say which one is wrong. Until it is fixed, tools read the first one in the file.

Sections are sorted by name. The sort compares the matched names character by character, so `@scope/x` comes before `a`.

A section whose name matches no dependency is an orphan. An orphan whose agent layer has `status: removed …` is a removed section. A removed section is kept on purpose, for a package that left `package.json`. `status: removed …` in the section of a package that is still in `package.json` is a mistake.

A section with an empty body does not count as a note.

## Layers

The body of a section has up to two layers. The reserved heading `### Agent notes` splits it. The heading is matched after trimming and without regard to case.

- The human layer starts on the line after `## <name>`. It ends before `### Agent notes`, or at the end of the section when there is no such heading.
- The agent layer starts on the line after `### Agent notes` and runs to the end of the section.

The first `### Agent notes` in a section is the one that counts. Inside a section, `### Agent notes` is the only allowed heading. Any other heading of level 3 or deeper is a mistake.

### Human layer

The human layer is free text written by people. Tools do not parse it. They display it, and they show its first non-empty line as the summary of the section. A leading `- ` or `* ` is dropped from the summary. Tools and AI agents do not change this layer.

### Agent layer

The agent layer is written by AI agents. It holds field lines. It may also hold prose.

A field line is a list item of the form `- key: value`. The exact pattern is `^\s*[-*]\s+([a-z][a-z0-9_-]*)\s*:\s*(.*)$`, matched without regard to case. The key is read lower-cased. The value is read trimmed. A list item whose value starts with `//` is a bare URL, such as `- https://…`, not a field. Any line that does not match the pattern is prose and is not read.

A key must be one of the fields listed below. An unknown key is a mistake. A key may appear on several lines. When one value is needed, the first line is used. A value that is empty, or that consists only of dashes, is a mistake.

## Fields

The first six fields are the core fields. They are filled whenever there is something true to say. The other fields are added when they are true and not obvious.

- `purpose` — the package's role in this project: what it is and why this repository uses it. One sentence.
- `usage` — where and how the package is wired in: entry points, wrapper module, config, and the rule for using it.
- `constraint` — something that must not change: a pin, a forbidden upgrade, a coordination requirement. The rule and its reason.
- `verify` — a command or a flow that shows the package still works.
- `log` — one dated event per line: added, upgrade attempted, proposal rejected. With a commit, PR or advisory id.
- `verified` — the installed version the notes were last checked against. The value starts with a version number; a leading `v` is allowed.
- `risk` — a judgment about what the package costs or endangers: security exposure, native binary, licence obligation, maintenance state.
- `runtime` — where the package executes. One of `server`, `client`, `build`, `dev`, `deploy`.
- `exposure` — whether the package handles untrusted input. One of `untrusted-input`, `internal`.
- `bump-with` — packages that must be upgraded together with this one.
- `remove-when` — the condition under which the package should be removed.
- `alternatives` — real decisions about replacements, dated, with a verdict.
- `owner` — who to ask: a team, a person, a channel.
- `status` — whether the package is still here. One of `dead`, `removal-planned`, or `removed` followed by a year and month as `YYYY-MM` and, usually, a reason. A missing `status` means the package is active.
- `links` — changelog, docs, upstream issue, registry page. The specific ones.
- `note` — anything that fits no other field. One thought per line.

`runtime` and `exposure` may hold several values, separated by `,`, `|` or `/`. The values of `runtime`, `exposure` and `status` are matched without regard to case.

## Validity

A file is valid when none of the following is true. Each of them is a mistake with a fix. The human layer is never checked.

- The file has no frontmatter.
- `format` is present and its value is not `dependency-notes/1`.
- The file has no level-1 heading, or its level-1 heading is not `# Dependency Notes`, or it has a second level-1 heading.
- A dependency name is a heading of a level other than 2, or is written `##name`, without the space.
- A section contains a heading of level 3 or deeper other than `### Agent notes`.
- Two sections have the same name.
- A field line has a key that is not a field, or a value that is empty or only dashes.
- `runtime`, `exposure`, `status` or `verified` has a value of the wrong shape.
- `status: removed …` appears in the section of a package that is in `package.json`.

An orphan is not a mistake. It may be a typo, or a package that left `package.json` without its notes.

## Canonical form

A file in canonical form has these parts, in this order:

1. The frontmatter as written, with `format` and `lang` appended when missing. A file without frontmatter gets these two keys.
2. One blank line, then the header comment.
3. One blank line, then `# Dependency Notes`.
4. If the file has an introduction: one blank line, then the introduction with its outer blank lines removed.
5. For each section, in name order: one blank line, then `## <name>` with the name as written, then one blank line, then the body with its outer blank lines removed. The inside of the body is kept as it is.
6. One line ending at the end of the file.

Duplicate sections keep their relative order. A tool that rewrites a file into canonical form changes nothing else.

A tool that adds a section puts it at its sorted position when the file is sorted. Otherwise it appends the section at the end.

## Versioning

`format` is the contract between the file and its reader. The number is a major version alone. It changes when a file that is valid under version *n* would be invalid under version *n + 1*. It changes for no other reason. Additions are recorded under [Changes](#changes) and do not change the number. A reader that meets a version it does not know reports it.

## Changes

None yet.

## Example

A complete repository is in [`example-repo`](example-repo/): a `package.json` and the `.pacmon/DEPENDENCY-NOTES.md` that documents it. One section:

```md
## express

HTTP API layer. Do not upgrade to v5 (SEC-1301).

### Agent notes

- purpose: HTTP framework; serves the public REST API
- constraint: stay on ^4 — v5 changes router path matching
- verified: 4.19.2
```
