import tseslint from 'typescript-eslint';

const nodeBuiltins = [
  'fs',
  'node:fs',
  'fs/promises',
  'node:fs/promises',
  'path',
  'node:path',
  'os',
  'node:os',
  'child_process',
  'node:child_process',
  'process',
  'node:process',
  'http',
  'node:http',
  'https',
  'node:https',
  'net',
  'node:net',
];

export default tseslint.config(
  { ignores: ['dist/**', 'dist-test/**', 'node_modules/**', '.vscode-test/**', 'fixtures/**'] },
  ...tseslint.configs.recommended,
  {
    // Whole extension must stay web-compatible and offline:
    // no Node builtins, no network modules anywhere in src (tests excluded below).
    files: ['src/core/**/*.ts', 'src/ext/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', { paths: nodeBuiltins.map((name) => ({ name, message: 'Web extension: Node builtins are forbidden. Use vscode.workspace.fs / Uri.joinPath.' })) }],
    },
  },
  {
    // Core must additionally stay vscode-free (future CLI twin).
    files: ['src/core/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            { name: 'vscode', message: 'src/core must not depend on the vscode API.' },
            ...nodeBuiltins.map((name) => ({ name, message: 'src/core must be platform-free.' })),
          ],
        },
      ],
    },
  },
  {
    files: ['src/test/**/*.ts', '*.mjs'],
    rules: {
      'no-restricted-imports': 'off',
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
);
