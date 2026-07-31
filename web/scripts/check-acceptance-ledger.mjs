import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(scriptDirectory, '..', '..')
const ledgerPath = resolve(
  repositoryRoot,
  process.argv[2] ?? 'docs/goal/complete-product-acceptance.yml',
)

const validStatuses = new Set([
  'UNASSESSED',
  'IN_PROGRESS',
  'BLOCKED',
  'FAIL',
  'PASS',
])
const validGoalStatuses = new Set([
  ...validStatuses,
  'COMPLETE',
  'RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE',
])
const repositoryPathPrefixes = [
  '.github/',
  'deploy/',
  'docs/',
  'evidence/',
  'scripts/',
  'src/',
  'web/',
]

const failures = []
const fail = (message) => failures.push(message)
const requireArray = (value, label) => {
  if (!Array.isArray(value)) {
    fail(`${label} must be an array`)
    return []
  }
  return value
}
const requireStatus = (value, label) => {
  if (!validStatuses.has(value)) {
    fail(`${label} has invalid status ${JSON.stringify(value)}`)
  }
}

let ledger
try {
  ledger = parse(readFileSync(ledgerPath, 'utf8'))
} catch (error) {
  console.error(`Acceptance ledger integrity FAIL: cannot parse ${ledgerPath}`)
  console.error(error instanceof Error ? error.message : String(error))
  process.exit(1)
}

if (!ledger || typeof ledger !== 'object') {
  fail('ledger root must be a mapping')
}
if (!validGoalStatuses.has(ledger?.status)) {
  fail(`ledger has invalid goal status ${JSON.stringify(ledger?.status)}`)
}
if (!/^[0-9a-f]{40}$/u.test(ledger?.last_reconciled_commit ?? '')) {
  fail('last_reconciled_commit must be a full 40-character Git SHA')
}

const ids = new Map()
const rememberId = (id, location) => {
  if (typeof id !== 'string' || id.length === 0) {
    fail(`${location} must have a non-empty id`)
    return
  }
  if (ids.has(id)) {
    fail(`duplicate id ${id}: ${ids.get(id)} and ${location}`)
    return
  }
  ids.set(id, location)
}

let evidenceReferenceCount = 0
const verifyEvidence = (owner, evidence) => {
  for (const reference of requireArray(evidence, `${owner}.evidence`)) {
    if (typeof reference !== 'string' || reference.length === 0) {
      fail(`${owner}.evidence contains a non-string or empty reference`)
      continue
    }
    if (!repositoryPathPrefixes.some((prefix) => reference.startsWith(prefix))) {
      continue
    }
    evidenceReferenceCount += 1
    const normalized = reference.endsWith('/') ? reference.slice(0, -1) : reference
    if (!existsSync(resolve(repositoryRoot, normalized))) {
      fail(`${owner}.evidence path does not exist: ${reference}`)
    }
  }
}

const humanGates = requireArray(ledger?.human_gates, 'human_gates')
for (const [index, gate] of humanGates.entries()) {
  const label = `human_gates[${index}]`
  rememberId(gate?.id, label)
  requireStatus(gate?.status, `${label} (${gate?.id ?? 'missing id'})`)
  verifyEvidence(gate?.id ?? label, gate?.evidence)
}

const gates = requireArray(ledger?.gates, 'gates')
const acceptanceItems = []
for (const [gateIndex, gate] of gates.entries()) {
  const gateLabel = `gates[${gateIndex}]`
  rememberId(gate?.id, gateLabel)
  requireStatus(gate?.status, `${gateLabel} (${gate?.id ?? 'missing id'})`)
  const acceptance = requireArray(gate?.acceptance, `${gate?.id ?? gateLabel}.acceptance`)
  if (acceptance.length === 0) {
    fail(`${gate?.id ?? gateLabel} must contain at least one acceptance item`)
  }
  for (const [acceptanceIndex, item] of acceptance.entries()) {
    const itemLabel = `${gate?.id ?? gateLabel}.acceptance[${acceptanceIndex}]`
    rememberId(item?.id, itemLabel)
    requireStatus(item?.status, `${itemLabel} (${item?.id ?? 'missing id'})`)
    verifyEvidence(item?.id ?? itemLabel, item?.evidence)
    acceptanceItems.push({ ...item, parent: gate?.id, parentRequired: gate?.required === true })
  }
  if (gate?.status === 'PASS' && acceptance.some((item) => item?.status !== 'PASS')) {
    fail(`${gate?.id} is PASS while one or more acceptance items are not PASS`)
  }
  if (gate?.status === 'UNASSESSED' && acceptance.some((item) => item?.status !== 'UNASSESSED')) {
    fail(`${gate?.id} is UNASSESSED while one or more acceptance items have been assessed`)
  }
  if (acceptance.every((item) => item?.status === 'PASS') && gate?.status !== 'PASS') {
    fail(`${gate?.id} is ${gate?.status} although every acceptance item is PASS`)
  }
}

const requiredHumanOpen = humanGates.filter(
  (gate) => gate?.required === true && gate?.status !== 'PASS',
)
const requiredAcceptanceOpen = acceptanceItems.filter(
  (item) => item.parentRequired && item?.status !== 'PASS',
)

if (ledger?.status === 'COMPLETE' && (requiredHumanOpen.length > 0 || requiredAcceptanceOpen.length > 0)) {
  fail('goal is COMPLETE while required human or acceptance gates remain open')
}
if (ledger?.status === 'RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE') {
  const machineWork = requiredAcceptanceOpen.filter((item) => item?.status !== 'BLOCKED')
  if (machineWork.length > 0) {
    fail(
      'goal claims RELEASE_CANDIDATE_BLOCKED_BY_HUMAN_GATE while machine-assessable items remain: '
      + machineWork.map((item) => item.id).join(', '),
    )
  }
}

if (failures.length > 0) {
  console.error(`Acceptance ledger integrity FAIL (${failures.length} issue${failures.length === 1 ? '' : 's'}):`)
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exit(1)
}

console.log(
  'Acceptance ledger integrity PASS: '
  + `${gates.length} gates, ${acceptanceItems.length} acceptance items, `
  + `${humanGates.length} human gates, ${evidenceReferenceCount} repository evidence paths`,
)
