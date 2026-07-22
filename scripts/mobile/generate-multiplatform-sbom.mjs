import { createHash, randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const web = join(root, "web");
const tauri = join(web, "src-tauri");
const output = join(root, "evidence", "mobile", "release", "sbom.cdx.json");

function run(command, args, cwd) {
  const executable = process.platform === "win32" && command === "pnpm" ? "pnpm.cmd" : command;
  const result = spawnSync(executable, args, { cwd, encoding: "utf8", maxBuffer: 64 * 1024 * 1024,
    shell: process.platform === "win32" });
  if (result.status !== 0) throw new Error(`${command} ${args.join(" ")} failed: ${result.error?.message || result.stderr || result.stdout}`);
  return result.stdout;
}

const components = new Map();
const add = component => components.set(component["bom-ref"], component);

function visitNpm(node) {
  for (const [name, value] of Object.entries(node?.dependencies ?? {})) {
    const version = value.version || "unknown";
    const ref = `pkg:npm/${encodeURIComponent(name)}@${encodeURIComponent(version)}`;
    add({ type: "library", name, version, purl: ref, "bom-ref": ref, scope: "required" });
    visitNpm(value);
  }
}

for (const project of JSON.parse(run("pnpm", ["list", "--prod", "--json", "--depth", "Infinity"], web))) visitNpm(project);

// Cargo metadata resolves every target and can download Apple/Android-only crates on Windows.
// Cargo.lock is the immutable cross-target dependency authority we need for an offline SBOM.
const cargoLock = readFileSync(join(tauri, "Cargo.lock"), "utf8");
for (const block of cargoLock.split(/\r?\n\[\[package\]\]\r?\n/).slice(1)) {
  const name = block.match(/^name\s*=\s*"([^"]+)"/m)?.[1];
  const version = block.match(/^version\s*=\s*"([^"]+)"/m)?.[1];
  if (!name || !version) continue;
  const ref = `pkg:cargo/${encodeURIComponent(name)}@${encodeURIComponent(version)}`;
  add({ type: "library", name, version, purl: ref, "bom-ref": ref, scope: "required" });
}

const artifacts = [
  join(web, "android", "app", "build", "outputs", "bundle", "release", "app-release.aab"),
  join(web, "android", "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
  join(tauri, "target", "release", "bundle", "msi", "Inner Cosmos_0.1.0_x64_en-US.msi"),
  join(tauri, "target", "release", "bundle", "nsis", "Inner Cosmos_0.1.0_x64-setup.exe")
];
for (const file of artifacts.filter(existsSync)) {
  const sha256 = createHash("sha256").update(readFileSync(file)).digest("hex");
  const ref = `file:${basename(file)}@sha256:${sha256}`;
  add({ type: "file", name: basename(file), version: "0.1.0", "bom-ref": ref,
    hashes: [{ alg: "SHA-256", content: sha256 }], properties: [{ name: "innercosmos:path", value: file.slice(root.length + 1).replaceAll("\\", "/") }] });
}

const bom = {
  bomFormat: "CycloneDX", specVersion: "1.6", serialNumber: `urn:uuid:${randomUUID()}`, version: 1,
  metadata: { timestamp: new Date().toISOString(), component: {
    type: "application", name: "Inner Cosmos multi-platform client", version: "0.1.0",
    "bom-ref": "pkg:generic/inner-cosmos-client@0.1.0"
  }, tools: { components: [{ type: "application", name: "inner-cosmos-sbom-generator", version: "1" }] } },
  components: [...components.values()].sort((a, b) => a["bom-ref"].localeCompare(b["bom-ref"]))
};
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, `${JSON.stringify(bom, null, 2)}\n`, "utf8");
console.log(`SBOM=${output}`);
console.log(`COMPONENTS=${bom.components.length}`);
