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
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]]
});
