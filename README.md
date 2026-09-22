# Pacmon

**Your dependency manifest says what you depend on. Pacmon adds why.**

Every dependency gets a short note: why it is here, what must not change, what to check before an upgrade. The note shows in `package.json`, `Cargo.toml`, or Maven `pom.xml` on hover, and you write it from there.

Notes are plain Markdown under `.pacmon/`: npm uses `DEPENDENCY-NOTES.md`, Rust uses `cargo/DEPENDENCY-NOTES.md`, and Maven uses `maven/DEPENDENCY-NOTES.md`. No database, service, or account is involved. AI coding agents read the same files before they touch a package.

![Pacmon in VS Code: a preview after each dependency in package.json, the note on hover, and a note written from the side panel](docs/media/demo.gif)

## The notes file

One ecosystem-specific Markdown file, one `## section` per direct dependency. People write under the heading. Agents write under `### Agent notes`.

```md
## express

HTTP API layer (SEC-1234).
Do not upgrade to v5 — the auth middleware is incompatible.

### Agent notes

- purpose: HTTP framework; serves the public REST API and the webhook receiver
- constraint: stay on ^4 — v5 changes router path matching and the session API
- verify: `vitest src/api` and the login e2e (`pnpm e2e:auth`)
- log: 2026-03 agent tried 5.0.1, 14 auth tests failed, reverted (PR #402)
- verified: 4.19.2
```

The file renders on GitHub as it is. A complete example is in [`docs/example-repo`](docs/example-repo/).

The file is the only place a note lives. Open it and write a section by hand,
and the dependency manifest shows it at once: the mark before the package name fills in,
and the first line of the note appears at the end of the line. Nothing is
generated, and nothing is cached anywhere else.

![helmet has no note; a section for it is written by hand in the notes file; the helmet line in package.json then carries the note](docs/media/notes-file.gif)

## Features

- Hover a dependency in `package.json`, `Cargo.toml`, or `pom.xml` to read its note.
- The first line of the note shows at the end of the dependency line.
- A mark before each package name: filled when it has a note, hollow when it does not.
- Write a note from the manifest: in a side panel, a peek editor, or an input box.
- **Documentation Coverage** lists which dependencies have a note and which do not.
- Problems in the notes file show as warnings, most with a one-click fix.

## Getting started

1. Install Pacmon from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=kontra.pacmon). VSCodium, Cursor, Windsurf, code-server and other editors that use [Open VSX](https://open-vsx.org/extension/kontra/pacmon) install it from there.
2. Open a `package.json`, `Cargo.toml`, or Maven `pom.xml`. A hollow mark appears before every supported direct dependency that has no note yet.
3. Right-click a dependency and choose **Pacmon: Add/Edit Dependency Note**, or click the mark. Write one line.
4. The note now shows on hover and at the end of the line. Pacmon created the ecosystem-specific notes file and `.pacmon/AGENT-RULES.md`; commit them with the manifest.

## AI agents

Every section has a second layer, `### Agent notes`, for AI coding agents. They write `- key: value` lines there: `purpose`, `constraint`, `verify`, `log`, `verified` and a few more. Agents never edit the text people wrote.

Run **Pacmon: Set Up AI Instructions** once. It writes the rules and the field list to `.pacmon/AGENT-RULES.md`, and adds a three-line pointer to the instruction files your agents already read: `AGENTS.md`, `CLAUDE.md`, `.cursor/rules/`, `.github/copilot-instructions.md`. From then on, an agent reads a package's section before it adds, upgrades or removes the package, and records what it did. Pacmon checks the agent lines: an unknown field or an empty value shows as a warning, with a fix.

![An agent in the terminal fills the notes for react; the package.json line gains its preview and the panel shows the agent lines](docs/media/demo-ai.gif)

Pacmon does not call any AI service. Agents use their own tools; Pacmon gives them the file and the rules.

## Settings

| Setting | Default | What it does |
|---|---|---|
| `pacmon.decorations` | `preview` | The hint at the end of a line that has a note: `preview` (the first line of the note), `badge` (a small marker), or `off`. |
| `pacmon.inlineSource` | `human-first` | Which layer feeds the preview and leads the hover: `human-first`, `ai-first`, `human-only`, `ai-only`. |
| `pacmon.noteEntry` | `panel` | How you write a note from a manifest: `panel`, `peek`, `input`, `inputBeside`, or `comments` (experimental). |
| `pacmon.noteButtons` | `["iconLeft", "link"]` | Clickable ways into a note: `iconLeft`, `link`, `codelens`, `inlayHint`, `lightbulb`. |
| `pacmon.monorepo` | `nearest` | Which ecosystem-specific notes file a manifest uses: the nearest one walking up, or only the one at the workspace root (`rootOnly`). |

The **Pacmon** view in the activity bar switches these without opening the settings editor.

## Requirements

VS Code 1.100 or newer. Nothing else: Pacmon reads and writes files in your workspace and makes no network requests. It works in VS Code for the Web (vscode.dev, github.dev), Remote-SSH, WSL and dev containers.

## Format reference

Existing npm notes use `dependency-notes/1`; Cargo and Maven notes use `dependency-notes/2`. The reference is [`docs/format.md`](docs/format.md).

## Contributing and license

Pacmon is an early preview and does not accept pull requests yet. Bug reports and feature requests are welcome as [issues](https://github.com/kontratek/pacmon/issues); see [CONTRIBUTING.md](CONTRIBUTING.md). Security problems: see [SECURITY.md](SECURITY.md).

Apache License 2.0 — see [LICENSE](LICENSE). "Pacmon" and the logo are trademarks of Kontra; see [TRADEMARK.md](TRADEMARK.md).
