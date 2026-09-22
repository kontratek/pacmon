# Dependency notes — rules for AI agents

<!-- .pacmon/AGENT-RULES.md is copied into this repository by Pacmon; do not edit it.
  "Pacmon: Set Up AI Instructions" refreshes it. The format reference is
  https://github.com/kontratek/pacmon/blob/main/docs/format.md -->

## Before you touch a dependency

Its notes are beside the manifest you are changing: npm uses `.pacmon/DEPENDENCY-NOTES.md`, Rust uses `.pacmon/cargo/DEPENDENCY-NOTES.md`, and Maven uses `.pacmon/maven/DEPENDENCY-NOTES.md`. In a monorepo, use the nearest matching ecosystem file walking up. Read the introduction and then the package's `## <name>` section.

- **Adding a package:** open its section in the same commit, with at least `purpose:`. Say what you considered and why this one, in `alternatives:` or `log:`.
- **Upgrading:** read its `constraint:` and `verify:` lines, then run what `verify:` says. Log the attempt with its outcome even if you reverted it — the next agent must not repeat it.
- **Removing:** keep the section and add `- status: removed <YYYY-MM> — <reason>`. Do not delete it.
- **Trivial packages** (`@types/*`, tiny helpers): `purpose:` and, if it applies, `bump-with:` are enough.

## Where you write

```md
## <exact note key shown by Pacmon>

Text written by people. Do not touch it.

### Agent notes

- key: value
- key: value
```

- **The text right under the heading is written by people.** Never edit or delete it. Treat it as one of your sources — alongside the code, the git history, the registry and your own reasoning. If your block disagrees with it, the human text wins: fix your block and add a `log:` line saying so.
- **`### Agent notes` is yours.** Lower-case keys, one fact per line, keys may repeat (several `constraint:` or `log:` lines are normal). Keys outside the vocabulary below are flagged by Pacmon; if something fits none of them, write it as `note:` — never invent a key.
- One `## name` section per direct dependency, in alphabetical order. `##` is reserved for package names; inside a section the only heading is `### Agent notes`.
- Never write an empty field or a dash placeholder. If you have nothing true to say, leave the field out.
- Write judgments, not measurements.
- Revise, do not accumulate: correct a line instead of adding a contradicting one. `log:` is the exception — it is the history.
- Field keys are English. Write the values in the language given by `lang:` in the file's frontmatter.
- Never remove the frontmatter or the header comment at the top of the file. If you are asked to draft the first human line, keep it short: it shows next to the dependency in its manifest.

## Fields

Fill these whenever you can:

| Field | Question it answers | One line of |
|---|---|---|
| `purpose:` | What job does it do here? | the package's role in this project — what it is and why this repo uses it, one sentence |
| `usage:` | Where and how is it wired in? | entry points, wrapper module, config; the rule for using it ("always through lib/http.ts") |
| `constraint:` | What must not change? | a pin, a forbidden upgrade, a coordination requirement — the rule and its reason |
| `verify:` | How do I check I did not break it? | a command or a flow: `vitest src/api`, "run the login e2e" |
| `log:` | What happened, what was decided? | one dated event per line — added (by whom, version, PR), an upgrade attempt, a rejected proposal and why; carry a commit hash, PR or advisory id |
| `verified:` | Which installed version were these notes checked against? | the version from the lockfile when you last confirmed the block is still true |

Add these only when they are true and non-obvious:

| Field | Question it answers | One line of |
|---|---|---|
| `risk:` | What does it cost or endanger? | a judgment — security exposure, native binary, licence obligation, maintenance state — not raw numbers |
| `runtime:` | Where does it execute? | one of: `server`, `client`, `build`, `dev`, `deploy` |
| `exposure:` | Does it handle untrusted input? | one of: `untrusted-input`, `internal` |
| `bump-with:` | What must move with it? | packages that have to be upgraded together (peer pairs, plugin sets) |
| `remove-when:` | When should it go? | the exit condition |
| `alternatives:` | What could replace it? | only real decisions, dated, with a verdict: "fastify (rejected 2023-01: middleware ecosystem)" |
| `owner:` | Who to ask? | a team, a person, a channel |
| `status:` | Is it still here? | `dead`, `removal-planned`, or `removed <YYYY-MM> — <reason>`; absent means active |
| `links:` | Sources? | changelog, docs, upstream issue, registry page — the specific ones, not the obvious |
| `note:` | Anything else the next agent must know? | free text that fits no other field — one thought per line, never a measurement |

A section whose `status:` starts with `removed` is kept on purpose for a package that left its manifest; Pacmon does not flag it as an orphan.

## Example

```md
## express

Do not upgrade to v5 — the auth middleware is incompatible (SEC-1301).
Rate-limit settings live in `src/middleware/limits.ts`.

### Agent notes

- purpose: HTTP framework; serves the public REST API and the webhook receiver
- constraint: stay on ^4 — v5 changes router path matching (path-to-regexp v8) and the session API
- verify: `vitest src/api` and the login e2e (`pnpm e2e:auth`)
- log: 2026-03 agent tried 5.0.1, 14 auth tests failed, reverted (PR #402)
- verified: 4.18.2
```
