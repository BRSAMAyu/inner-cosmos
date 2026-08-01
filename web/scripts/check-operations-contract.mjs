import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'
import { validateOperationsContract } from './operations-contract-rules.mjs'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(scriptDirectory, '..', '..')
const contractPath = resolve(repositoryRoot, process.argv[2] ?? 'docs/operations/operations-contract.yml')

let contract
try {
  contract = parse(readFileSync(contractPath, 'utf8'))
} catch (error) {
  console.error(`Operations contract FAIL: cannot parse ${contractPath}`)
  console.error(error instanceof Error ? error.message : String(error))
  process.exit(1)
}

const result = validateOperationsContract(contract, { repositoryRoot })
if (result.failures.length > 0) {
  console.error(`Operations contract FAIL (${result.failures.length} issue${result.failures.length === 1 ? '' : 's'}):`)
  for (const failure of result.failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log(
  `Operations contract PASS: ${result.scenarioCount} scenarios, ${result.artifactCount} artifacts, `
  + `${result.tokenCount} source assertions; status=${contract.status}`,
)
