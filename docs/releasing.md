# Releasing

Pacmon is published by the Release workflow, never from a laptop. Merging a pull
request publishes nothing; a release is a button. The run raises the version,
names the CHANGELOG's "Unreleased" section, runs the whole test suite, builds one
VSIX, pushes the version commit and its tag to `main`, and sends that same file
to the Visual Studio Marketplace, to Open VSX, and to a GitHub Release.

## One-time setup

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Where it comes from |
|---|---|
| `VSCE_PAT` | Azure DevOps personal access token for the `kontra` publisher: https://dev.azure.com → User settings → Personal access tokens. Organization "All accessible organizations", scope Marketplace → **Manage**. Azure DevOps caps the lifetime at one year, so note the expiry. |
| `OVSX_PAT` | Open VSX access token: https://open-vsx.org/user-settings/tokens. Publishing also needs the `kontra` namespace, created once with `pnpm exec ovsx create-namespace kontra -p <token>`. It already exists. |

Who may release is an allowlist in the workflow's first step — currently
`yavuzatlas` and `yavuzatlas-kontra`. Anyone else with write access can press the
button, but the run stops there, before anything is built or published. Add an
account by editing that line.

The `kontra` namespace on Open VSX is **unverified**: the account that created it
holds contributor access, not ownership, and the extension page carries no
publisher badge. Ownership is granted on request — open a namespace claim at
https://github.com/EclipseFdn/open-vsx.org/issues. Worth doing for a name the
trademark policy reserves.

## Every release

1. **As work lands:** put anything a user would notice under `## Unreleased` in
   `CHANGELOG.md`, in the same pull request. That text becomes the release notes.
2. **To release:** Actions → Release → Run workflow → pick `patch`, `minor` or
   `major` → Run.

That is the whole ritual. The run refuses to publish if the "Unreleased" section
is empty, and every test runs against the tree that actually ships.

Version numbers follow VS Code's channel convention: releases take even minors
(`0.2.x`, `0.4.x`). Odd minors are reserved for pre-releases.

## When something goes wrong

- **A test fails.** Nothing was published and nothing was committed. Fix it on
  `main` and press the button again.
- **A publish step fails** (an expired token, a registry outage). Replace the
  secret, then **Re-run failed jobs** on that run. The version is not raised a
  second time: the run checks out the same commit, sees its tag already on the
  remote, and skips the commit. A registry that already has the version skips it,
  so only the missing one goes out.
- **The push is rejected** because `main` moved after the button was pressed.
  Nothing was published. Press the button again.
- **A mistake is found after publishing.** A version number is spent the moment a
  registry accepts it. It ships as the next patch version; nothing is re-published.

## Releasing without the button

A tag pushed by hand releases the version already in `package.json`, whose
CHANGELOG section must already exist. This path skips the version bump entirely:

```sh
git tag -a v0.1.1 -m v0.1.1 && git push origin v0.1.1
```

## Pre-releases and manual publishing

The workflow publishes release versions only. For a pre-release, or to publish
from a machine, build with `pnpm run package` and then, with `VSCE_PAT` and
`OVSX_PAT` set in the environment:

```sh
pnpm exec vsce publish --packagePath pacmon-X.Y.Z.vsix [--pre-release]
pnpm exec ovsx publish --packagePath pacmon-X.Y.Z.vsix
```
