import test from 'node:test'
import assert from 'node:assert/strict'
import { validateOperationsContract } from './operations-contract-rules.mjs'

const ids = ['release', 'rollback', 'incident', 'provider-failure', 'data-rights', 'disaster-recovery']
const buildContract = () => ({
  schema_version: 1,
  status: 'IN_PROGRESS',
  evidence_boundary: 'READY_FOR_REHEARSAL_NOT_INDEPENDENTLY_EXERCISED',
  runbook: 'docs/operations/README.md',
  required_scenarios: [...ids],
  scenarios: ids.map((id, index) => ({
    id,
    heading: `## ${index + 1}. ${id}`,
    owner_role: 'operator',
    exercise_status: 'READY_FOR_REHEARSAL',
    artifacts: [{ path: `${id}.txt`, contains: [`token-${id}`] }],
    preconditions: ['ready'],
    success_gates: ['pass'],
    stop_conditions: ['stop'],
    recovery: ['recover'],
    evidence_fields: ['timestamp'],
    remaining: 'Independent exercise remains open.',
  })),
})

const validate = (contract, overrides = {}) => validateOperationsContract(contract, {
  repositoryRoot: '/repo',
  artifactExists: () => true,
  readArtifact: (path) => path.endsWith('README.md')
    ? ids.map((id, index) => `## ${index + 1}. ${id}`).join('\n')
    : `token-${path.split(/[\\/]/u).at(-1).replace('.txt', '')}`,
  ...overrides,
})

test('accepts a complete rehearsal-ready six-scenario contract', () => {
  const result = validate(buildContract())
  assert.deepEqual(result.failures, [])
  assert.equal(result.scenarioCount, 6)
  assert.equal(result.artifactCount, 6)
  assert.equal(result.tokenCount, 6)
})

test('rejects missing scenarios and incomplete recovery fields', () => {
  const contract = buildContract()
  contract.scenarios.pop()
  contract.scenarios[0].recovery = []
  const failures = validate(contract).failures.join('\n')
  assert.match(failures, /required scenario is missing: disaster-recovery/u)
  assert.match(failures, /release\.recovery/u)
})

test('rejects a duplicate required-scenario declaration', () => {
  const contract = buildContract()
  contract.required_scenarios[5] = 'release'
  assert.match(
    validate(contract).failures.join('\n'),
    /required_scenarios must contain exactly the six operational scenarios/u,
  )
})

test('rejects missing source artifacts and drifted source assertions', () => {
  const contract = buildContract()
  const missing = validate(contract, { artifactExists: (path) => !path.endsWith('release.txt') })
  assert.match(missing.failures.join('\n'), /artifact does not exist/u)
  const drifted = validate(contract, {
    readArtifact: (path) => path.endsWith('README.md')
      ? ids.map((id, index) => `## ${index + 1}. ${id}`).join('\n')
      : 'drifted',
  })
  assert.match(drifted.failures.join('\n'), /is missing token/u)
})

test('rejects false PASS claims and secret-shaped contract values', () => {
  const contract = buildContract()
  contract.status = 'PASS'
  contract.scenarios[0].remaining = ['s', 'k-', '123456789012345678901234'].join('')
  const failures = validate(contract).failures.join('\n')
  assert.match(failures, /cannot be PASS/u)
  assert.match(failures, /forbidden secret-shaped value/u)
})

test('returns a structured failure for an invalid document root', () => {
  assert.deepEqual(validate(null), {
    failures: ['contract root must be a mapping'],
    artifactCount: 0,
    tokenCount: 0,
    scenarioCount: 0,
  })
})
