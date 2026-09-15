# Pacmon for JetBrains IDEs

The JetBrains counterpart of the [VS Code extension](../README.md): the same `.pacmon/DEPENDENCY-NOTES.md` file, read and written the same way, inside IntelliJ IDEA, WebStorm and the rest of the JetBrains family. `docs/format.md` (at the repository root) is the authority on the file format both clients read; this module keeps its own hand-written port of the same rules, not a shared library.

## What it does

- A **Pacmon** tool window (right stripe) with two views: a dashboard (documentation coverage, click-target and note-marker settings) and a per-dependency editor with separate human and agent layers.
- Small inlay icons before each dependency name in `package.json` — filled when it has a note, hollow when it doesn't — plus an end-of-line preview and a quick-doc hover.
- Live `docs/format.md` warnings, underlined with a hover message, wherever `DEPENDENCY-NOTES.md` is open.
- Four Tools-menu commands — **Open DEPENDENCY-NOTES.md**, **Documentation Coverage**, **Set Up AI Instructions**, **Format DEPENDENCY-NOTES.md** — and **Add/Edit Dependency Note** in the editor's context menu.

## Requirements

- JDK 21
- No local Gradle install needed — use the wrapper (`./gradlew` / `gradlew.bat`)
- First build downloads IntelliJ IDEA Community 2025.2.6.2 as a dependency (a few hundred MB, cached under `~/.gradle` afterwards)

## Building

Run these from `jetbrains/`:

| Command | What it does |
|---|---|
| `./gradlew test` | Compiles and runs the unit test suite (JUnit, no IDE needed) |
| `./gradlew buildPlugin` | Produces the installable plugin zip at `build/distributions/pacmon-jetbrains-<version>.zip` |
| `./gradlew runIde` | Launches a sandboxed IntelliJ IDEA with the plugin installed, for manual testing |
| `./gradlew verifyPlugin` | Runs the IntelliJ Plugin Verifier against the IDEs listed in `pluginVerification.ides` (`build.gradle.kts`) |

To install a built zip into your own IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk...** and pick the zip.

If a `test` run reports failures that don't match the source (a signature you just changed, a test that passed a moment ago), rerun with `--rerun-tasks` before trusting the result — Gradle's build cache has been seen serving stale compiled test classes after an ABI change in this project.

## Code structure

```
src/main/kotlin/dev/pacmon/jetbrains/
├── core/      Pure logic — no IntelliJ platform imports except where noted. Hand-ported
│              from src/core/*.ts at the repository root; keep the two in sync by hand.
│   ├── NotesCore.kt      parse/serialize the notes file, compose and splice layers,
│   │                     canonical form (docs/format.md "Canonical form")
│   ├── NotesModel.kt     the data classes NotesCore.parse produces (sections, frontmatter,
│   │                     layer ranges, lint spans)
│   ├── NotesLint.kt      the full format lint — a line-for-line port of src/core/lint.ts
│   ├── LintFinding.kt    one sealed class per lint finding kind, with its message()
│   ├── AgentNotes.kt     agent-layer field lint used live in the note editor's side panel
│   └── AiInstructions.kt the AGENTS.md/CLAUDE.md pointer block ("Set Up AI Instructions")
│
├── editor/    Wiring into the package.json editor and the notes file editor.
│   ├── DependencyPsi.kt            reads dependency names out of package.json's PSI
│   ├── PacmonInlayHintsProvider.kt the inlay icon before each dependency name
│   ├── PacmonLinePainter.kt        the end-of-line note preview
│   ├── PacmonDocumentationProvider.kt  the quick-doc hover
│   └── PacmonNotesAnnotator.kt     live NotesLint warnings in DEPENDENCY-NOTES.md
│
├── service/   Project-level state and file I/O.
│   ├── PacmonProjectService.kt  settings, resolving/reading/writing the notes file,
│   │                            AGENT-RULES.md, the monorepo "nearest ancestor" search
│   └── PacmonNotesListener.kt   message-bus topic other components subscribe to when
│                                 the notes file changes (including from outside the IDE)
│
├── settings/  PacmonConfigurable.kt — the Settings → Tools → Pacmon page
│
├── ui/        The tool window's Swing UI (Kotlin UI DSL where practical, plain Swing
│              where a custom layout is needed).
│   ├── PacmonToolWindowFactory.kt    registers the tool window
│   ├── PacmonToolWindowRootPanel.kt  CardLayout switch between dashboard and editor
│   ├── PacmonToolWindowService.kt    project service that owns the panel and routes
│   │                                 "open this dependency" requests into it
│   ├── PacmonDashboardPanel.kt       the coverage/settings view
│   ├── PacmonToolWindowPanel.kt      the per-dependency editor (header, layers, footer)
│   ├── NoteLayerPanel.kt             one editable layer: read view / textarea, autosize
│   └── PacmonMarkdown.kt             the small Markdown renderer behind the read view
│
└── action/    AnAction entry points wired in plugin.xml (Tools menu, editor popup menu).
```

`src/test/kotlin/...` mirrors this layout. `core/` and `editor/DependencyPsi` are covered by plain JUnit tests; the tool-window UI is covered by `BasePlatformTestCase`-based tests under `ui/`.

### Keeping this in sync with the TypeScript core

Per the repository's `CLAUDE.md`: when `docs/format.md` changes, update `src/core/*.ts` first, then this module's `core/` package by hand, in the same commit. `NotesLintTest.kt` and `NotesCoreTest.kt` mirror scenarios from `src/test/unit/lint.test.ts` and `parseNotes.test.ts` — when you add a case to one side, add its counterpart to the other.

## Publishing to the JetBrains Marketplace

The plugin is published under the **Kontra** vendor profile, [plugins.jetbrains.com/vendor/kontra](https://plugins.jetbrains.com/vendor/kontra); sign in with a JetBrains Account that belongs to it. The `<vendor>` name in `plugin.xml` is that profile's name and must stay in step with it.

**What the listing is made of.** The name, the description, the change notes and the logo come out of the zip: `<name>`, `<description>` and `<change-notes>` in `src/main/resources/META-INF/plugin.xml`, and `META-INF/pluginIcon.svg` next to it (40×40 SVG, the mark on the dark disc as in `media/icon.png`). None of them can be edited on the site — a change ships as a new version. The license (Apache License 2.0, the repository's `LICENSE`), the source code link (required for an open-source plugin: https://github.com/kontratek/pacmon), the tags and any screenshots are entered on the site and can be changed there at any time.

Every release, in this order:

1. **Raise the version.** `version` in `build.gradle.kts` follows the VS Code extension's version in `package.json`. The Release workflow at the repository root does not touch it, so raise it by hand; the Marketplace refuses a version number it already has. Write the release's `<change-notes>` in the same edit — the listing's "What's New" is that element.
2. **Verify:** `./gradlew verifyPlugin`. It runs the IntelliJ Plugin Verifier against the build target and the newest release of every later IDE branch (`pluginVerification.ides` in `build.gradle.kts`; since `until-build` is left open, that is every IDE the listing claims). The approval guidelines require this before each upload, and the Marketplace runs it again after, across every product. Each extra IDE is 1.2 GB to download and 3.5 GB unpacked under `~/.gradle`, once; on a laptop short of disk, `./gradlew verifyPlugin -Ppacmon.verify=current` checks the build target alone and downloads nothing. The report lands in `build/reports/pluginVerifier/`; a *compatibility problem* has to be fixed before uploading, a deprecated-API warning is advice. CI runs the full task in the `jetbrains-verify` job.
3. **Build:** `./gradlew buildPlugin` → `build/distributions/pacmon-jetbrains-<version>.zip`. CI's `pacmon-jetbrains` artifact is the same zip.
4. **Upload.** First release: on the Marketplace site, **Upload plugin**, pick the zip, choose the vendor, choose the license and tags, add the source code link. Later releases: the plugin's Marketplace page → **Upload new version** → the new zip. JetBrains reviews every new plugin and every update by hand, normally within two business days, and mails the vendor when the status changes.
5. **Later, from Gradle** (optional): generate a Permanent Token in your Marketplace account settings, export it as `PUBLISH_TOKEN`, and `./gradlew publishPlugin` uploads the zip; a `publishing { }` block under `intellijPlatform { }` in `build.gradle.kts` sets the channel. Never commit the token; keep it in CI secrets or a local, gitignored properties file.

`sinceBuild` under `pluginConfiguration.ideaVersion` controls the oldest IDE that can install a release — raise it when you start depending on a newer platform API. `until-build` is deliberately not set: one plugin.xml serves every JetBrains product that bundles `com.intellij.modules.json`, on every version from `sinceBuild` on, which is why step 2 matters.
