# Security Policy

## Reporting a vulnerability

Do not open a public issue for a security problem.

Report it privately on GitHub: open **Security → Report a vulnerability** on this repository, or go to https://github.com/kontratek/pacmon/security/advisories/new. Only Kontra sees the report. Kontra replies in the same thread and publishes an advisory when a fix is out.

Include the Pacmon version, the VS Code version, the steps, and — if the problem needs one — a minimal `package.json` or `.pacmon/DEPENDENCY-NOTES.md`.

## What is in scope

Pacmon runs inside the editor. It reads and writes files in the open workspace (`package.json`, `.pacmon/DEPENDENCY-NOTES.md`, `.pacmon/AGENT-RULES.md`, and the AI instruction files it is asked to update) and makes no network requests. Its own surface is in scope: a crafted notes file or `package.json` that makes the extension misbehave, notes content that reaches the note panel or a hover unescaped, or a write outside the workspace.

## Supported versions

Fixes go into the latest release on the Marketplace and Open VSX. Older releases are not patched.
