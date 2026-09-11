# Releasing

Pacmon is published by the Release workflow (`.github/workflows/release.yml`),
never from a laptop. Pushing a tag `vX.Y.Z` runs the full test suite, builds one
VSIX, publishes that file to the Visual Studio Marketplace and to Open VSX, and
attaches it to a GitHub Release for people who install offline. Tokens live in
repository secrets.

## One-time setup

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Where it comes from |
|---|---|
| `VSCE_PAT` | Azure DevOps personal access token for the `kontra` publisher: https://dev.azure.com → User settings → Personal access tokens. Organization "All accessible organizations", scope Marketplace → **Manage**. Note the expiry; Azure DevOps caps it at one year. |
| `OVSX_PAT` | Open VSX access token: https://open-vsx.org/user-settings/tokens. The `kontra` namespace has to exist there once: `pnpm exec ovsx create-namespace kontra -p <token>`. |

An expired token fails only its own publish step; replace the secret and re-run
the job (see below).

## Every release

1. Add a `## X.Y.Z — YYYY-MM-DD` section to `CHANGELOG.md` and commit it. The
   workflow refuses to publish without it; the section becomes the release notes.
2. `pnpm version patch` (or `minor` / `major`). This bumps `package.json`,
   commits, and creates the tag `vX.Y.Z`. Run it on a clean tree.
3. `git push --follow-tags`
4. Watch Actions → Release. Publishing happens only after lint, typecheck, unit
   and integration tests pass on the tagged commit.

## When something goes wrong

- The tag, `package.json` and `CHANGELOG.md` must name the same version. A
  mismatch fails the run before anything is published; fix it and push a new tag.
- Both publish steps use `--skip-duplicate`, so a run that stopped half-way
  (Marketplace done, Open VSX failed) is re-run from the Actions page after the
  fix. The version a registry already has is skipped; the missing one goes out.
- A version number is consumed the moment a registry accepts it. A mistake found
  afterwards ships as the next patch version, never as a re-publish.

## Pre-releases

The workflow publishes release versions only. VS Code keeps the two channels
apart by minor number: even minors (`0.2.x`) for releases, odd minors (`0.3.x`)
for pre-releases. If one is ever needed, publish it by hand with `VSCE_PAT` set:
`pnpm exec vsce publish --no-dependencies --pre-release`.

## By hand

`pnpm run package` builds `pacmon-X.Y.Z.vsix`. With `VSCE_PAT` and `OVSX_PAT` set:

```sh
pnpm exec vsce publish --packagePath pacmon-X.Y.Z.vsix
pnpm exec ovsx publish --packagePath pacmon-X.Y.Z.vsix
```
