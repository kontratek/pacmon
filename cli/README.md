# pacmon

**Pacmon documents *why* each dependency exists — right next to your `package.json`.**

Today Pacmon ships as a **VS Code extension**: hover a dependency to read its note, right-click to write one. Notes live in your repository in `.pacmon/DEPENDENCIES.md`, travel with git, and render on GitHub. Notes are plain Markdown and stay in your repository.

- Install: [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=Kontra.pacmon)
- Source and issues: [github.com/kontratek/pacmon](https://github.com/kontratek/pacmon)
- Site: [pacmon.dev](https://pacmon.dev)

## About this package

This npm package is the home of the forthcoming Pacmon **command line interface** — the same notes file, readable and writable outside the editor (CI checks, coverage reports, batch edits). Until that ships, running `npx pacmon` only prints the links above. No dependencies, no install scripts.

Apache-2.0 © Kontra Siber Savunma Teknoloji Ltd. Şti.
