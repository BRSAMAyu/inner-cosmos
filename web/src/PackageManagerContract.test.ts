import { readFileSync, existsSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * CP-49 supply-chain contract: ONE package manager with ONE authoritative lockfile, no
 * uncontrolled version ranges. The repo once carried BOTH package-lock.json and
 * pnpm-lock.yaml with `latest` devDependency ranges — two competing resolution truths and
 * an unpinned supply chain. This contract fails the build the moment any of that comes back.
 */

const webRoot = resolve(__dirname, "..");
const pkg = JSON.parse(readFileSync(resolve(webRoot, "package.json"), "utf-8"));

describe("CP-49 package manager contract", () => {
  it("declares exactly one pinned package manager: pnpm@<exact>", () => {
    expect(pkg.packageManager).toMatch(/^pnpm@\d+\.\d+\.\d+$/);
  });

  it("has exactly one authoritative lockfile — pnpm-lock.yaml, and no npm lockfile", () => {
    expect(existsSync(resolve(webRoot, "pnpm-lock.yaml")), "pnpm-lock.yaml is the authority")
      .toBe(true);
    expect(existsSync(resolve(webRoot, "package-lock.json")),
      "package-lock.json must not come back — two lockfiles are two resolution truths")
      .toBe(false);
    expect(existsSync(resolve(webRoot, "npm-shrinkwrap.json"))).toBe(false);
    expect(existsSync(resolve(webRoot, "yarn.lock"))).toBe(false);
    const lock = readFileSync(resolve(webRoot, "pnpm-lock.yaml"), "utf-8");
    expect(lock.length).toBeGreaterThan(100);
    expect(lock).toMatch(/lockfileVersion:/);
  });

  it("resolves no dependency through latest / * / empty ranges", () => {
    const offenders: string[] = [];
    for (const section of ["dependencies", "devDependencies", "optionalDependencies"] as const) {
      for (const [name, range] of Object.entries(pkg[section] ?? {})) {
        if (typeof range !== "string") continue;
        const raw = range.startsWith("workspace:") ? range.slice("workspace:".length) : range;
        if (raw === "latest" || raw === "*" || raw === "" || raw === "x") {
          offenders.push(`${section}.${name}: "${range}"`);
        }
      }
    }
    expect(offenders, offenders.join("; ")).toEqual([]);
  });

  it("keeps the declared manager the only one scripted (no npm ci/install in workflows)", () => {
    const workflows = readdirSync(resolve(webRoot, "../.github/workflows"));
    for (const workflow of workflows) {
      const body = readFileSync(resolve(webRoot, "../.github/workflows", workflow), "utf-8");
      const npmUsage = body.match(/^\s*(?:run:\s*)?npm (?:ci|install)\b/m);
      expect(npmUsage, `${workflow} must use pnpm, not npm install/ci`).toBeNull();
    }
  });
});
