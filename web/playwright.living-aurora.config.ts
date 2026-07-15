import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "living-aurora-experience.spec.ts",
  timeout: 120_000,
  expect: { timeout: 25_000 },
  use: {
    baseURL: "http://127.0.0.1:8081",
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-experience-report" }]],
  webServer: {
    command: "java -jar ../target/inner-cosmos-0.1.0.jar --server.address=127.0.0.1 --server.port=8081 --inner-cosmos.demo.seed-enabled=true --spring.task.scheduling.enabled=true --inner-cosmos.wake-intent.poll-delay-ms=200 \"--spring.datasource.url=jdbc:h2:mem:living-aurora-experience;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1\"",
    url: "http://127.0.0.1:8081/app/aurora/index.html",
    timeout: 120_000,
    reuseExistingServer: false
  }
});
