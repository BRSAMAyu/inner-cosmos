import { defineConfig } from "@playwright/test";

// CP-43 (closing-checklist §2-22) browser-matrix automation leg.
//
// Chromium is the baseline engine: it is the engine every dev/CI machine running this
// config is expected to have installed, so the default invocation (`playwright test`,
// no env) runs the chromium project only and never silently claims engines it cannot
// launch.
//
// Firefox and WebKit matrix legs are declared here and opt-in via IC_BROWSER_MATRIX so
// a plain run does not break on machines where those engines are absent:
//   IC_BROWSER_MATRIX=full            -> chromium + firefox + webkit
//   IC_BROWSER_MATRIX=firefox,webkit  -> only the engines listed (comma separated)
// Install the extra engines first (`npx playwright install firefox webkit`). Where they
// are not installed the legs are execution-gated and reported as SKIPPED_BY_ENV in the
// CP-43 delivery note — Chrome/Edge/Safari real-device end states remain operator gates.
const matrixLegs = (process.env.IC_BROWSER_MATRIX ?? "chromium")
  .split(",")
  .map(leg => leg.trim())
  .filter(Boolean);
const wantsLeg = (leg: string) => matrixLegs.includes(leg) || matrixLegs.includes("full");

export default defineConfig({
  testDir: "./e2e",
  testIgnore: "living-aurora-experience.spec.ts",
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: process.env.INNER_COSMOS_BASE_URL ?? "http://127.0.0.1:8080",
    locale: "zh-CN",
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  webServer: process.env.INNER_COSMOS_BASE_URL ? undefined : {
    command: "java -jar ../target/inner-cosmos-0.1.0.jar --server.address=127.0.0.1 --server.port=8080 --inner-cosmos.demo.seed-enabled=true --spring.task.scheduling.enabled=false --inner-cosmos.security.rate-limit.login.capacity=100 --inner-cosmos.security.rate-limit.login.refill-per-minute=100 --inner-cosmos.security.rate-limit.login.advertised-limit=100 --inner-cosmos.security.rate-limit.aurora.capacity=100 --inner-cosmos.security.rate-limit.aurora.refill-per-minute=100 --inner-cosmos.security.rate-limit.aurora.advertised-limit=100 \"--spring.datasource.url=jdbc:h2:mem:living-aurora-e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1\"",
    url: "http://127.0.0.1:8080/app/aurora/",
    timeout: 120_000,
    reuseExistingServer: false
  },
  projects: [
    ...(wantsLeg("chromium")
      ? [{ name: "chromium", use: { browserName: "chromium" as const } }]
      : []),
    ...(wantsLeg("firefox")
      ? [{ name: "firefox", use: { browserName: "firefox" as const } }]
      : []),
    ...(wantsLeg("webkit")
      ? [{ name: "webkit", use: { browserName: "webkit" as const } }]
      : [])
  ]
});
