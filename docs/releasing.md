# Releasing

Pacmon is published by the Release workflow, never from a laptop. Merging a pull
request publishes nothing; a release is a button. The run raises the version,
names the CHANGELOG's "Unreleased" section, runs the whole test suite, builds one
VSIX and one JetBrains plugin zip, pushes the version commit and its tag to
`main`, sends the VSIX to the Visual Studio Marketplace and to Open VSX, submits
the zip to the JetBrains Marketplace, and attaches both to a GitHub Release.

The JetBrains plugin takes its version from `package.json` and its change notes
from the CHANGELOG section of that version (`jetbrains/build.gradle.kts`), so one
version number and one set of notes serve all three registries. The JetBrains
Marketplace lists a version only after its own review, normally within two
business days; the run does not wait for that.

## One-time setup

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Where it comes from |
|---|---|
| `VSCE_PAT` | Azure DevOps personal access token for the `kontra` publisher: https://dev.azure.com → User settings → Personal access tokens. Organization "All accessible organizations", scope Marketplace → **Manage**. Azure DevOps caps the lifetime at one year, so note the expiry. |
| `OVSX_PAT` | Open VSX access token: https://open-vsx.org/user-settings/tokens. Publishing also needs the `kontra` namespace, created once with `pnpm exec ovsx create-namespace kontra -p <token>`. It already exists. |
| `JETBRAINS_TOKEN` | JetBrains Marketplace permanent token: https://plugins.jetbrains.com → your profile → **My Tokens** → Generate Token. The plugin is `dev.pacmon.jetbrains`, Marketplace id 34295, under the `Kontra` vendor, and its first version was uploaded by hand, as the Marketplace requires; the workflow only ever uploads the next ones. |

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
- **JetBrains has not listed the version yet.** That is the review, not a
  failure: the run ends when the upload is accepted, and the Marketplace lists
  the version once a person at JetBrains has approved it, normally within two
  business days. The vendor's e-mail gets the verdict; a rejection names what
  to change, and the fix ships as the next patch version.

## Releasing without the button

A tag pushed by hand releases the version already in `package.json`, whose
CHANGELOG section must already exist. This path skips the version bump entirely:

```sh
git tag -a v0.1.1 -m v0.1.1 && git push origin v0.1.1
```

## Publishing by hand

Every published version is a release; there is no pre-release channel. To
publish from a machine, build with `pnpm run package` and then, with
`VSCE_PAT` and `OVSX_PAT` set in the environment:

```sh
pnpm exec vsce publish --packagePath pacmon-X.Y.Z.vsix
pnpm exec ovsx publish --packagePath pacmon-X.Y.Z.vsix
```

The JetBrains plugin, from `jetbrains/`, with `JETBRAINS_TOKEN` set: the version
and the change notes come from `package.json` and `CHANGELOG.md`, so they must
already say what is being published.

```sh
./gradlew publishPlugin
```
