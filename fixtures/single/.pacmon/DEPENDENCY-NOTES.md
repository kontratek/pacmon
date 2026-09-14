---
format: dependency-notes/1
lang: en
---

<!-- Each "## name" below is a package from package.json. The text right under the
  heading is written by people. "### Agent notes" and everything below it is written
  by AI agents — rules in .pacmon/AGENT-RULES.md. -->

# Dependencies

## express

HTTP API layer (SEC-1234).
Do not upgrade to v5 — the auth middleware is incompatible.

### Agent notes

- purpose: HTTP framework; serves the public REST API
- constraint: stay on ^4 — v5 breaks the auth middleware
- verify: `vitest src/api`
- log: 2026-03 agent tried 5.0.1, auth tests failed, reverted (PR #402)
- verified: 4.18.0

## ghost-package

This section is an orphan on purpose (used by diagnostics tests).

## lodash

Utility helpers. Only debounce is used.

## old-package

Replaced by lodash.

### Agent notes

- status: removed 2026-06 — replaced by lodash
