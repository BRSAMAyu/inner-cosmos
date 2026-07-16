import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const schema = resolve(webRoot, "../src/main/resources/static/openapi/inner-cosmos-v1.yml");
const output = resolve(webRoot, "src/generated/inner-cosmos-v1.d.ts");
const cli = resolve(webRoot, "tools/openapi/node_modules/openapi-typescript/bin/cli.js");
const result = spawnSync(process.execPath, [cli, schema, "--default-non-nullable", "false", "--output", output], {
  cwd: webRoot,
  encoding: "utf8",
  stdio: "inherit",
});
process.exit(result.status ?? 1);
