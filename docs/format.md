# The `pacmon/1` format

Reference for the files Pacmon reads and writes: `.pacmon/DEPENDENCIES.md`, the dependency notes of one `package.json`, and the files around it. This describes version `1`; see [Versioning](#versioning).

## Files

| Path | One per | Written by |
|---|---|---|
| `<dir>/.pacmon/DEPENDENCIES.md` | `package.json`, in the same `<dir>` | people and AI agents, by layer; tools write only the parts marked below |
| `<workspace root>/.pacmon/AGENT-RULES.md` | workspace folder | Pacmon, from its own copy; never edited by hand |
| a marked block in `AGENTS.md`, `CLAUDE.md`, `.cursor/rules/dependency-notes.md`, `.github/copilot-instructions.md` | workspace folder | Pacmon writes the block; the rest of the file is the user's |

**Which notes file applies to a `package.json`:** the nearest `.pacmon/DEPENDENCIES.md` found walking up from the `package.json`'s directory to the workspace folder root. A new notes file is created in the `package.json`'s own directory.

**`AGENT-RULES.md`** is written when the first note in the workspace is written, if missing, and rewritten by *Set Up AI Instructions*. Its content is `assets/AGENT-RULES.md` of the installed extension.

**The marked block** is three lines between `<!-- pacmon:start -->` and `<!-- pacmon:end -->`. *Set Up AI Instructions* replaces an existing block in place, otherwise appends one after a blank line. A block between `<!-- pacmon:deps-notes:start -->` and `<!-- pacmon:deps-notes:end -->` is found and replaced too; those markers are no longer written.

## The notes file

UTF-8. Line ending: CRLF if the file contains one, otherwise LF. A byte order mark is accepted and removed by *Format*.

Parts, in order:

```md
---
format: pacmon/1
lang: en
---

<!-- Each "## name" below is a package from package.json. The text right under the
  heading is written by people. "### Agent notes" and everything below it is written
  by AI agents — rules in .pacmon/AGENT-RULES.md. -->

# Dependencies

Repository-wide rules, free text.

## <package name>

Human layer.

### Agent notes

- key: value
```

| Part | Owner | *Format* |
|---|---|---|
| Frontmatter | the format; other tools' keys are kept | adds missing keys |
| Header comment | the format | rewrites |
| Title | the format | rewrites |
| Introduction | people | keeps |
| Sections | people and agents, by layer | sorts; keeps bodies |

Lines inside a fenced code block (` ``` ` or `~~~`, indented up to three spaces) are never headings or fields.

### Frontmatter

Present when line 1 is `---`; ends at the next line that is `---`. Each line inside is `key: value`, `key` matching `[A-Za-z][\w-]*`. A key with an empty value is not read.

| Key | Value | When missing |
|---|---|---|
| `format` | `pacmon/1` | the file is read as `pacmon/1`; *Format* adds the key |
| `lang` | the language the values are written in; keys are always English | `en`; *Format* adds the key |

Any other key is kept in place and not read.

### Header comment

The first HTML comment at the top of the file, after the frontmatter if there is one. Its text belongs to the format: *Format* replaces it with the three lines shown above. A file's own commentary goes in the introduction.

### Title

The one level-1 heading, `# Dependencies`. Any other level-1 heading is a mistake; a level-1 heading that names a dependency is a section at the wrong level.

### Introduction

Everything between the title and the first section: the repository's own dependency rules, free text, never parsed. Headings of level 3 and deeper are allowed here.

### Sections

`## <name>`, where `<name>` is the package name as it appears in `package.json`, `@scope/` included. `##` is reserved for package names everywhere in the file.

- A dependency is any key under `dependencies`, `devDependencies`, `peerDependencies` or `optionalDependencies`.
- Names are matched after trimming, removing one pair of surrounding quotes or backticks, and lower-casing.
- One section per dependency. When two sections name the same package, the first is read and the second is reported.
- Sections are ordered by name, the matched form compared code unit by code unit, so `@scope/x` sorts first.
- A section whose name matches no dependency is an **orphan**, unless its agent layer says `status: removed …`: then it is a **removed** section, kept on purpose. `status: removed …` on a package that is present is a mistake.
- A section with nothing under its heading documents nothing.

### Layers

A section has up to three layers, split by reserved level-3 headings compared case-insensitively after trimming:

| Layer | From | Written by |
|---|---|---|
| Human | the line after `## <name>` | people |
| Agent | the line after `### Agent notes` | AI agents |
| Generated | `### Generated`, heading included, to the end of the section | tools; reserved, nothing writes it yet |

The first `### Agent notes` opens the agent layer; one placed after `### Generated` does not. Inside a section, `### Agent notes` is the only heading; any other heading of level 3 or deeper, `### Generated` included, is reported.

**Human layer.** Free text. Tools display it and take its first non-empty line as the **summary**, shown in place of the section; a leading `- ` or `* ` is dropped from the summary. Tools and agents do not change this layer.

**Agent layer.** Field lines and prose. A field line is

```md
- key: value
```

matched by `^\s*[-*]\s+([a-z][a-z0-9_-]*)\s*:\s*(.*)$`, case-insensitively; `key` is lower-cased, `value` trimmed. Any other line is prose and is not read. A line whose value starts with `//` (a bare URL, `- https://…`) is not a field.

- Keys come from the table below. An unknown key is reported, with the field it most likely meant when one is close.
- Keys may repeat. Where one value is needed, the first line wins.
- A value that is empty or only dashes is a mistake.

### Fields

The first six are the core fields, expected whenever they can be filled truthfully; the rest apply when true and non-obvious.

| Key | Holds |
|---|---|
| `purpose` | the package's role in this project: what it is and why this repository uses it, one sentence |
| `usage` | entry points, wrapper module, config; the rule for using it |
| `constraint` | a pin, a forbidden upgrade, a coordination requirement: the rule and its reason |
| `verify` | a command or a flow that shows the package still works |
| `log` | one dated event per line: added, upgrade attempted, proposal rejected, with a commit, PR or advisory id |
| `verified` | the installed version the notes were last checked against; starts with a version number, `v` optional |
| `risk` | a judgment: security exposure, native binary, licence obligation, maintenance state |
| `runtime` | where it executes: `server`, `client`, `build`, `dev`, `deploy` |
| `exposure` | `untrusted-input` or `internal` |
| `bump-with` | packages that must be upgraded together |
| `remove-when` | the exit condition |
| `alternatives` | real decisions only, dated, with a verdict |
| `owner` | a team, a person, a channel |
| `status` | `dead`, `removal-planned`, or `removed YYYY-MM — reason`; absent means active |
| `links` | changelog, docs, upstream issue, registry page: the specific ones |
| `note` | anything that fits no other field, one thought per line |

`runtime` and `exposure` accept several values separated by `,`, `|` or `/`. Enumerated values are compared lower-cased.

## Validation

Every finding is a mistake with a fix, reported as a warning. The human layer is never checked.

| Finding | Condition |
|---|---|
| missing frontmatter | no frontmatter block |
| unknown format | `format` is present and is not `pacmon/1` |
| missing title | no level-1 heading |
| wrong title | the level-1 heading is not `# Dependencies` |
| extra title | a second level-1 heading |
| wrong heading level | a dependency name as a heading of a level other than 2 |
| missing space | `##name` where `name` is a dependency |
| stray heading | a heading of level 3 or deeper inside a section, other than `### Agent notes` |
| duplicate section | a second section for the same package |
| unknown key | a field line whose key is not in the table |
| empty value | a field line whose value is empty or only dashes |
| bad value | `runtime`, `exposure`, `status` or `verified` with a value of the wrong shape |
| removed but present | `status: removed …` in the section of a package that is in `package.json` |

Orphans are marked, not reported: a typo, or a package that left `package.json` without its notes.

## Canonical form

*Format* rewrites a file into this form and changes nothing else:

- the frontmatter as written, `format` and `lang` appended when missing;
- one blank line, the header comment, one blank line, `# Dependencies`;
- the introduction, if any, after one blank line, outer blank lines removed;
- the sections in name order, each as one blank line, `## <name>` with the name as written, one blank line, the body with outer blank lines removed and the inside verbatim;
- one line ending at the end of the file.

Duplicate sections keep their relative order. A section written by a tool goes to its sorted position when the file is sorted, otherwise to the end.

## Tools

A tool that writes this format:

- keeps the human layer, the introduction and unknown frontmatter keys as they are;
- when it writes one layer of a section, keeps the other layers byte for byte;
- writes `AGENT-RULES.md` only when it is missing or when asked to;
- reads a file whose `format` it does not know, and reports it.

Pacmon writes the human layer from its note commands, both layers from the note panel, and only the format-owned parts from *Format*.

## Versioning

`format` names the version of these rules the file follows. The number is a major version alone: it changes when a file valid under version *n* would be invalid under *n + 1*, and for no other reason. Additions are recorded under [Changes](#changes) and do not change it.

## Changes

- 2026-09-14 — Named `pacmon/1`. The same layout was released as `deps-notes/1` (0.1.0, 2026-09-11). The `agents` frontmatter key is no longer written; the rules file is `.pacmon/AGENT-RULES.md`.

## Example

A complete repository is in [`example-repo`](example-repo/): a `package.json` and the `.pacmon/DEPENDENCIES.md` that documents it. One section:

```md
## express

HTTP API layer. Do not upgrade to v5 (SEC-1301).

### Agent notes

- purpose: HTTP framework; serves the public REST API
- constraint: stay on ^4 — v5 changes router path matching
- verified: 4.19.2
```
