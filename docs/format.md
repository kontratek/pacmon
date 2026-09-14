# The `dependency-notes/1` format

Reference for `.pacmon/DEPENDENCY-NOTES.md`: the notes a repository keeps about the dependencies of one `package.json`. This describes version `1`; see [Versioning](#versioning).

## The notes file

`.pacmon/DEPENDENCY-NOTES.md` sits in the directory of the `package.json` it describes. Where packages are nested, the nearest file walking up from a `package.json` to the repository root applies to it.

UTF-8. Line ending: CRLF if the file contains one, otherwise LF. A byte order mark is accepted and dropped when the file is rewritten.

The parts of the file, in order:

```md
---
format: dependency-notes/1
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

Frontmatter, header comment and title belong to the format. The introduction belongs to the people who maintain the repository. A section belongs to people and to AI agents, by layer. Lines inside a fenced code block (` ``` ` or `~~~`, indented up to three spaces) are never headings or fields.

## Frontmatter

Present when line 1 is `---`; it ends at the next line that is `---`. Each line inside is `key: value`, `key` matching `[A-Za-z][\w-]*`. A key with an empty value is not read.

`format` names the version of these rules the file follows: `dependency-notes/1`. When missing, the file is read as `dependency-notes/1`.

`lang` names the language the values are written in; keys are always English. Default `en`.

Any other key is kept in place and not read.

## Header comment

The first HTML comment at the top of the file, after the frontmatter if there is one. Its text belongs to the format and is the three lines shown above; it tells a reader who writes where, and points AI agents to `.pacmon/AGENT-RULES.md`, the instruction file kept next to the notes, which is not part of this format. A file's own commentary goes in the introduction.

## Title

The one level-1 heading, `# Dependencies`. Any other level-1 heading is a mistake; a level-1 heading that names a dependency is a section at the wrong level.

## Introduction

Everything between the title and the first section: the repository's own dependency rules, free text, never parsed. Headings of level 3 and deeper are allowed here.

## Sections

`## <name>`, where `<name>` is the package name as it appears in `package.json`, `@scope/` included. `##` is reserved for package names everywhere in the file.

A dependency is any key under `dependencies`, `devDependencies`, `peerDependencies` or `optionalDependencies`. Names are matched after trimming, removing one pair of surrounding quotes or backticks, and lower-casing.

One section per dependency. When two sections name the same package, the first is read and the second is a mistake. Sections are kept in name order, the matched form compared code unit by code unit, so `@scope/x` sorts first.

A section whose name matches no dependency is an orphan, unless its agent layer says `status: removed …`: then it is a removed section, kept on purpose. `status: removed …` on a package that is present is a mistake. A section with nothing under its heading documents nothing.

## Layers

A section has up to three layers, split by reserved level-3 headings compared case-insensitively after trimming. The human layer runs from the line after `## <name>`. The agent layer runs from the line after `### Agent notes`. The generated layer runs from `### Generated`, heading included, to the end of the section; it is reserved, nothing writes it yet, and whatever is there is kept as is.

The first `### Agent notes` opens the agent layer; one placed after `### Generated` does not. Inside a section, `### Agent notes` is the only heading; any other heading of level 3 or deeper, `### Generated` included, is a mistake.

### Human layer

Free text, written by people. Tools display it and take its first non-empty line as the summary, shown in place of the section; a leading `- ` or `* ` is dropped from the summary. Tools and agents do not change this layer.

### Agent layer

Field lines and prose, written by AI agents. A field line is

```md
- key: value
```

matched by `^\s*[-*]\s+([a-z][a-z0-9_-]*)\s*:\s*(.*)$`, case-insensitively; `key` is lower-cased, `value` trimmed. Any other line is prose and is not read. A line whose value starts with `//`, a bare URL such as `- https://…`, is not a field.

Keys come from the list below; an unknown key is a mistake. Keys may repeat; where one value is needed, the first line wins. A value that is empty or only dashes is a mistake.

## Fields

The first six are the core fields, expected whenever they can be filled truthfully; the rest apply when true and non-obvious.

- `purpose` — the package's role in this project: what it is and why this repository uses it, one sentence.
- `usage` — entry points, wrapper module, config; the rule for using it.
- `constraint` — a pin, a forbidden upgrade, a coordination requirement: the rule and its reason.
- `verify` — a command or a flow that shows the package still works.
- `log` — one dated event per line: added, upgrade attempted, proposal rejected, with a commit, PR or advisory id.
- `verified` — the installed version the notes were last checked against. Starts with a version number, `v` optional.
- `risk` — a judgment: security exposure, native binary, licence obligation, maintenance state.
- `runtime` — where it executes: `server`, `client`, `build`, `dev` or `deploy`.
- `exposure` — `untrusted-input` or `internal`.
- `bump-with` — packages that must be upgraded together.
- `remove-when` — the exit condition.
- `alternatives` — real decisions only, dated, with a verdict.
- `owner` — a team, a person, a channel.
- `status` — `dead`, `removal-planned`, or `removed YYYY-MM — reason`; absent means active.
- `links` — changelog, docs, upstream issue, registry page: the specific ones.
- `note` — anything that fits no other field, one thought per line.

`runtime` and `exposure` accept several values separated by `,`, `|` or `/`. Enumerated values are compared lower-cased.

## Validity

A file is valid when none of the following holds. Each is a mistake with a fix; the human layer is never checked.

- no frontmatter block;
- `format` is present and is not `dependency-notes/1`;
- no level-1 heading, a level-1 heading other than `# Dependencies`, or a second level-1 heading;
- a dependency name as a heading of a level other than 2, or `##name` without the space;
- a heading of level 3 or deeper inside a section, other than `### Agent notes`;
- a second section for the same package;
- a field line whose key is not in the list, or whose value is empty or only dashes;
- `runtime`, `exposure`, `status` or `verified` with a value of the wrong shape;
- `status: removed …` in the section of a package that is in `package.json`.

An orphan is not a mistake: a typo, or a package that left `package.json` without its notes.

## Canonical form

A file in canonical form is, in this order: the frontmatter as written, `format` and `lang` appended when missing; one blank line; the header comment; one blank line; `# Dependencies`; the introduction, if any, after one blank line, outer blank lines removed; then each section in name order as one blank line, `## <name>` with the name as written, one blank line, and the body with outer blank lines removed and the inside verbatim; and one line ending at the end of the file. Duplicate sections keep their relative order.

A tool that rewrites a file into canonical form changes nothing else. A section added by a tool goes to its sorted position when the file is sorted, otherwise to the end.

## Versioning

`format` is the reader's contract. The number is a major version alone: it changes when a file valid under version *n* would be invalid under *n + 1*, and for no other reason. Additions are recorded under [Changes](#changes) and do not change it. A reader that meets a version it does not know reports it.

## Changes

- 2026-09-14 — Named `dependency-notes/1`, in `.pacmon/DEPENDENCY-NOTES.md`. The same layout was released as `deps-notes/1` in `.pacmon/DEPENDENCIES.md` (0.1.0, 2026-09-11). The `agents` frontmatter key is no longer written.

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
