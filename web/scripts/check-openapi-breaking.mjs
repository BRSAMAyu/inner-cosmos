import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { parse } from "yaml";
import { findBreakingChanges } from "./openapi-breaking-rules.mjs";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = resolve(webRoot, "..");
const specPath = "src/main/resources/static/openapi/inner-cosmos-v1.yml";
const currentSpec = resolve(repoRoot, specPath);
const baseRef = process.env.OPENAPI_BASE_REF || process.argv[2] || "HEAD";

function git(args) {
  return spawnSync("git", args, { cwd: repoRoot, encoding: "utf8" });
}

const commit = git(["cat-file", "-e", `${baseRef}^{commit}`]);
if (commit.status !== 0) {
  console.error(`OpenAPI base ref is not a commit: ${baseRef}`);
  process.exit(2);
}

const baseObject = git(["show", `${baseRef}:${specPath}`]);
if (baseObject.status !== 0) {
  console.log(`No OpenAPI v1 contract exists at ${baseRef}; treating this as the initial contract publication.`);
  process.exit(0);
}

const oldSpec = parse(baseObject.stdout);
const newSpec = parse(await readFile(currentSpec, "utf8"));
const changes = findBreakingChanges(oldSpec, newSpec);
if (changes.length > 0) {
  console.error(`OpenAPI breaking-change gate failed against ${baseRef}:`);
  for (const change of changes) console.error(`- [${change.code}] ${change.location}: ${change.detail}`);
  process.exit(1);
}
console.log(`OpenAPI breaking-change gate passed against ${baseRef}.`);
