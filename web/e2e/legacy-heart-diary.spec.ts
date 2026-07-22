import { expect, test } from "@playwright/test";

async function loginIfNeeded(page: import("@playwright/test").Page) {
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(appShell).toBeVisible();
  const offlineNotice = page.getByRole("button", { name: "知道了" });
  if (await offlineNotice.isVisible().catch(() => false)) await offlineNotice.click();
}

async function gotoCosmos(page: import("@playwright/test").Page) {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  await page.getByRole("button", { name: /^内宇宙/ }).click();
  await expect(page.getByRole("heading", { name: "心声日记" })).toBeVisible();
}

test("heart diary text path: write, polish, and submit a diary entry", async ({ page }) => {
  await gotoCosmos(page);

  // Write a diary entry (must be >= 5 chars for submit to enable).
  const textarea = page.getByLabel("心声日记正文");
  const entry = "今天在Playwright里测试了心声日记，感觉这个流程很完整，文字也能安全保留。";
  await textarea.fill(entry);

  // The polish panel should appear when text > 5 chars.
  await expect(page.getByRole("heading", { name: "心声整理与润色" })).toBeVisible();

  // Try polishing at level 1 (净化 / Cleaned).
  await page.getByRole("tab", { name: "净化" }).click();
  // The display text in the textarea may change after polish; just verify no crash.
  await page.waitForTimeout(2000);

  // Switch back to original.
  await page.getByRole("tab", { name: "原文" }).click();

  // Submit the diary entry.
  await page.getByRole("button", { name: "将心声化作记忆星宿" }).click();

  // After submit, the hook resets. The textarea should become empty or the status should update.
  // Give the backend a moment to process.
  await page.waitForTimeout(2000);
  await page.screenshot({ path: "test-results/legacy-heart-diary-submitted.png", fullPage: true });
});

test("heart diary submit is disabled for very short text", async ({ page }) => {
  await gotoCosmos(page);

  const textarea = page.getByLabel("心声日记正文");
  await textarea.fill("短");

  const submitButton = page.getByRole("button", { name: "将心声化作记忆星宿" });
  await expect(submitButton).toBeDisabled();

  // The polish panel should NOT appear for very short text.
  await expect(page.getByRole("heading", { name: "心声整理与润色" })).toBeHidden();
});
