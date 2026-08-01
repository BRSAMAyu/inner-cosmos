import { existsSync, readFileSync } from 'node:fs'
import { isAbsolute, relative, resolve } from 'node:path'

const expectedScenarioIds = new Set([
  'release',
  'rollback',
  'incident',
  'provider-failure',
  'data-rights',
  'disaster-recovery',
])
const validContractStatuses = new Set(['IN_PROGRESS', 'PASS'])
const validExerciseStatuses = new Set(['READY_FOR_REHEARSAL', 'PARTIAL', 'PASS'])
const sensitivePatterns = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u,
  /\bAKIA[0-9A-Z]{16}\b/u,
  /\bAIza[0-9A-Za-z_-]{30,}\b/u,
  /\bsk-[0-9A-Za-z_-]{20,}\b/u,
]

const nonEmptyStrings = (value) => Array.isArray(value)
  && value.length > 0
  && value.every((item) => typeof item === 'string' && item.trim().length > 0)

export function validateOperationsContract(contract, options) {
  const {
    repositoryRoot,
    artifactExists = existsSync,
    readArtifact = (path) => readFileSync(path, 'utf8'),
  } = options
  const failures = []
  let artifactCount = 0
  let tokenCount = 0
  const fail = (message) => failures.push(message)

  if (!contract || typeof contract !== 'object') {
    return {
      failures: ['contract root must be a mapping'],
      artifactCount,
      tokenCount,
      scenarioCount: 0,
    }
  }
  if (contract.schema_version !== 1) fail('schema_version must be 1')
  if (!validContractStatuses.has(contract.status)) fail(`invalid contract status ${JSON.stringify(contract.status)}`)
  if (typeof contract.evidence_boundary !== 'string' || contract.evidence_boundary.length === 0) {
    fail('evidence_boundary must be explicit')
  }

  const requiredIds = Array.isArray(contract.required_scenarios) ? contract.required_scenarios : []
  if (requiredIds.length !== expectedScenarioIds.size
      || new Set(requiredIds).size !== expectedScenarioIds.size
      || requiredIds.some((id) => !expectedScenarioIds.has(id))) {
    fail('required_scenarios must contain exactly the six operational scenarios')
  }

  const runbookReference = contract.runbook
  if (typeof runbookReference !== 'string' || runbookReference.length === 0) {
    fail('runbook must be a repository-relative path')
  }
  let runbookText = ''
  if (typeof runbookReference === 'string' && runbookReference.length > 0) {
    const runbookPath = resolve(repositoryRoot, runbookReference)
    if (isAbsolute(runbookReference) || relative(repositoryRoot, runbookPath).startsWith('..')) {
      fail(`runbook path escapes the repository: ${runbookReference}`)
    } else if (!artifactExists(runbookPath)) {
      fail(`runbook does not exist: ${runbookReference}`)
    } else {
      runbookText = readArtifact(runbookPath)
    }
  }

  const scenarios = Array.isArray(contract.scenarios) ? contract.scenarios : []
  const seen = new Set()
  for (const scenario of scenarios) {
    const label = scenario?.id ?? 'missing-id'
    if (!expectedScenarioIds.has(label)) fail(`unexpected scenario id ${JSON.stringify(label)}`)
    if (seen.has(label)) fail(`duplicate scenario id ${label}`)
    seen.add(label)
    if (!validExerciseStatuses.has(scenario?.exercise_status)) {
      fail(`${label} has invalid exercise_status ${JSON.stringify(scenario?.exercise_status)}`)
    }
    if (scenario?.exercise_status !== 'PASS'
        && (typeof scenario?.remaining !== 'string' || scenario.remaining.trim().length === 0)) {
      fail(`${label} must explain remaining work while not PASS`)
    }
    if (typeof scenario?.heading !== 'string' || !runbookText.includes(scenario.heading)) {
      fail(`${label} heading is missing from the runbook: ${JSON.stringify(scenario?.heading)}`)
    }
    if (typeof scenario?.owner_role !== 'string' || scenario.owner_role.length === 0) {
      fail(`${label} must name an owner_role`)
    }
    for (const field of ['preconditions', 'success_gates', 'stop_conditions', 'recovery', 'evidence_fields']) {
      if (!nonEmptyStrings(scenario?.[field])) fail(`${label}.${field} must be a non-empty string array`)
    }

    if (!Array.isArray(scenario?.artifacts) || scenario.artifacts.length === 0) {
      fail(`${label}.artifacts must contain at least one source artifact`)
      continue
    }
    for (const artifact of scenario.artifacts) {
      const reference = artifact?.path
      if (typeof reference !== 'string' || reference.length === 0) {
        fail(`${label} contains an artifact without a path`)
        continue
      }
      const absolutePath = resolve(repositoryRoot, reference)
      if (isAbsolute(reference) || relative(repositoryRoot, absolutePath).startsWith('..')) {
        fail(`${label} artifact escapes the repository: ${reference}`)
        continue
      }
      artifactCount += 1
      if (!artifactExists(absolutePath)) {
        fail(`${label} artifact does not exist: ${reference}`)
        continue
      }
      const tokens = Array.isArray(artifact?.contains) ? artifact.contains : []
      if (!nonEmptyStrings(tokens)) {
        fail(`${label} artifact ${reference} must declare non-empty contains tokens`)
        continue
      }
      const source = readArtifact(absolutePath)
      for (const token of tokens) {
        tokenCount += 1
        if (!source.includes(token)) fail(`${label} artifact ${reference} is missing token ${JSON.stringify(token)}`)
      }
    }
  }

  for (const id of expectedScenarioIds) {
    if (!seen.has(id)) fail(`required scenario is missing: ${id}`)
  }
  if (contract.status === 'PASS' && scenarios.some((scenario) => scenario?.exercise_status !== 'PASS')) {
    fail('contract cannot be PASS while an operational scenario is not PASS')
  }
  const serialized = JSON.stringify(contract)
  for (const pattern of sensitivePatterns) {
    if (pattern.test(serialized)) fail(`contract contains a forbidden secret-shaped value matching ${pattern}`)
  }

  return { failures, artifactCount, tokenCount, scenarioCount: scenarios.length }
}
