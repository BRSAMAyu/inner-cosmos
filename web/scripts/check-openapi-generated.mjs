import { readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const schema = resolve(webRoot, "../src/main/resources/static/openapi/inner-cosmos-v1.yml");
const checkedIn = resolve(webRoot, "src/generated/inner-cosmos-v1.d.ts");
const generated = resolve(tmpdir(), `inner-cosmos-openapi-${process.pid}.d.ts`);
const cli = resolve(webRoot, "tools/openapi/node_modules/openapi-typescript/bin/cli.js");

const result = spawnSync(process.execPath, [cli, schema, "--default-non-nullable", "false", "--output", generated], {
  cwd: webRoot,
  encoding: "utf8",
});
if (result.status !== 0) {
  process.stderr.write(result.stdout ?? "");
  process.stderr.write(result.stderr ?? "");
  process.exit(result.status ?? 1);
}

try {
  const normalize = (value) => value.replace(/\r\n/g, "\n");
  const expected = normalize(await readFile(generated, "utf8"));
  let actual = "";
  try {
    actual = normalize(await readFile(checkedIn, "utf8"));
  } catch {
    console.error("Generated API types are missing. Run `npm run api:generate` and commit the result.");
    process.exit(1);
  }
  if (actual !== expected) {
    console.error("Generated API types drifted from inner-cosmos-v1.yml. Run `npm run api:generate` and commit the result.");
    process.exit(1);
  }
  console.log("OpenAPI generated types are current.");
} finally {
  await rm(generated, { force: true });
}
