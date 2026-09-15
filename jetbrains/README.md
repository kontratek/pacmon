# Pacmon for JetBrains IDEs

The JetBrains counterpart of the [VS Code extension](../README.md): the same `.pacmon/DEPENDENCY-NOTES.md` file, read and written the same way, inside IntelliJ IDEA, WebStorm and the rest of the JetBrains family. `docs/format.md` (at the repository root) is the authority on the file format both clients read; this module keeps its own hand-written port of the same rules, not a shared library.

## What it does

- A **Pacmon** tool window (right stripe) with two views: a dashboard (documentation coverage, click-target and note-marker settings) and a per-dependency editor with separate human and agent layers.
- Small inlay icons before each dependency name in `package.json` — filled when it has a note, hollow when it doesn't — plus an end-of-line preview and a quick-doc hover.
- Live `docs/format.md` warnings, underlined with a hover message, wherever `DEPENDENCY-NOTES.md` is open.
- Three Tools-menu commands: **Add/Edit Dependency Note**, **Documentation Coverage**, **Set Up AI Instructions**, **Format DEPENDENCY-NOTES.md**, **Open DEPENDENCY-NOTES.md**.

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

1. **Create (or sign in to) a vendor account.** Go to [plugins.jetbrains.com](https://plugins.jetbrains.com), sign in with a JetBrains Account, then **My Products → Vendors** (or `plugins.jetbrains.com/vendor/new`) to register a vendor profile if you haven't already — this is the account/organization page a published plugin belongs to.
2. **Build the zip:** `./gradlew buildPlugin` → `build/distributions/pacmon-jetbrains-<version>.zip`.
3. **First release — upload by hand:** on the Marketplace site, **Upload plugin**, pick the zip, choose the vendor, fill in the listing (description, license — this repo's `CLA-v1.md`/`LICENSE`, tags). JetBrains reviews new plugins before they go public; expect it to sit "pending approval" for a bit.
4. **Later releases:**
   - By hand: the plugin's Marketplace page → **Upload new version** → the new zip. Simplest, no extra setup.
   - From the CLI (optional, once you want CI to publish): generate a **Permanent Token** under your JetBrains Marketplace account settings, then either export it as `./gradlew publishPlugin` picks up via the `PUBLISH_TOKEN` environment variable, or add a `publishing { token.set(...) }` block to `intellijPlatform { }` in `build.gradle.kts` (not present yet — this repo currently only builds the zip, it doesn't publish from Gradle). Never commit the token; keep it in CI secrets or a local, gitignored properties file.
5. **Versioning:** bump `version` in `build.gradle.kts` before each release; the Marketplace rejects a re-upload of a version number it already has. `sinceBuild`/`untilBuild` under `pluginConfiguration.ideaVersion` control which IDE versions can install a given release — update `sinceBuild` when you start depending on a newer platform API.
6. Once published, `pluginVerification { ides { recommended() } }` (already in `build.gradle.kts`) is worth running before each release — `./gradlew verifyPlugin` — since one plugin.xml serves every JetBrains product that bundles `com.intellij.modules.json`, not IntelliJ IDEA alone.
