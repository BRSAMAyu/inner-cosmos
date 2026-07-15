import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: process.env.INNER_COSMOS_BASE_URL ?? "http://127.0.0.1:8080",
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  webServer: process.env.INNER_COSMOS_BASE_URL ? undefined : {
    command: "java -jar ../target/inner-cosmos-0.1.0.jar --server.address=127.0.0.1 --server.port=8080 --inner-cosmos.demo.seed-enabled=true --spring.task.scheduling.enabled=false \"--spring.datasource.url=jdbc:h2:mem:living-aurora-e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1\"",
    url: "http://127.0.0.1:8080/app/aurora/index.html",
    timeout: 120_000,
    reuseExistingServer: false
  }
});
