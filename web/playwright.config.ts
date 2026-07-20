import { defineConfig } from "@playwright/test";

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
  }
});
