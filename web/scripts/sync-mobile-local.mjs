import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const pnpmEntrypoint = process.env.npm_execpath;
if (!pnpmEntrypoint) throw new Error("pnpm entrypoint is unavailable; run this through pnpm");

function run(args, extraEnv = {}) {
  const result = spawnSync(process.execPath, [pnpmEntrypoint, ...args], {
    cwd: process.cwd(),
    env: { ...process.env, ...extraEnv },
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run(["run", "build:mobile:local"]);
run(["exec", "cap", "sync", "android"]);

// Keep the checked-in Capacitor contract production-safe. Only the generated Android
// debug asset receives the local HTTP scheme, avoiding HTTPS-to-HTTP mixed-content while
// preserving the signed build's HTTPS origin.
const generatedConfig = resolve("android/app/src/main/assets/capacitor.config.json");
const config = JSON.parse(readFileSync(generatedConfig, "utf8"));
config.appName = "Inner Cosmos Dev";
config.server = { ...(config.server ?? {}), androidScheme: "http", hostname: "localhost" };
writeFileSync(generatedConfig, `${JSON.stringify(config, null, 2)}\n`, "utf8");
