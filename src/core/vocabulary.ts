/**
 * The agent layer's vocabulary: the field keys and the enumerated values the
 * lint checks. `docs/format.md` is the authority; this file and
 * `assets/AGENT-RULES.md` follow it by hand, and `agentRules.test.ts` checks
 * that they name the same fields.
 */

export interface AgentFieldSpec {
  key: string;
  /** Core fields are filled whenever the agent can; the rest only when relevant. */
  core: boolean;
}

export const AGENT_FIELDS: readonly AgentFieldSpec[] = [
  { key: 'purpose', core: true },
  { key: 'usage', core: true },
  { key: 'constraint', core: true },
  { key: 'verify', core: true },
  { key: 'log', core: true },
  { key: 'verified', core: true },
  { key: 'risk', core: false },
  { key: 'runtime', core: false },
  { key: 'exposure', core: false },
  { key: 'bump-with', core: false },
  { key: 'remove-when', core: false },
  { key: 'alternatives', core: false },
  { key: 'owner', core: false },
  { key: 'status', core: false },
  { key: 'links', core: false },
  { key: 'note', core: false },
];

export const RUNTIME_VALUES = ['server', 'client', 'build', 'dev', 'deploy'] as const;
export const EXPOSURE_VALUES = ['untrusted-input', 'internal'] as const;
