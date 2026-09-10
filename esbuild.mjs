import * as esbuild from 'esbuild';

const watch = process.argv.includes('--watch');
const production = process.argv.includes('--production');

/** @type {import('esbuild').BuildOptions} */
const common = {
  entryPoints: ['src/ext/extension.ts'],
  bundle: true,
  external: ['vscode'],
  format: 'cjs',
  mainFields: ['module', 'main'],
  sourcemap: !production,
  minify: production,
  target: 'es2022',
  logLevel: 'info',
};

const builds = [
  { ...common, platform: 'node', outfile: 'dist/extension.js' },
  { ...common, platform: 'browser', outfile: 'dist/extension.web.js' },
];

if (watch) {
  const contexts = await Promise.all(builds.map((b) => esbuild.context(b)));
  await Promise.all(contexts.map((c) => c.watch()));
} else {
  await Promise.all(builds.map((b) => esbuild.build(b)));
}
