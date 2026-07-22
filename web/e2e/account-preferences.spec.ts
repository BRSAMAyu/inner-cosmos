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

test("Aurora preferences round-trip through the real backend, surviving a reload", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByLabel("对话风格")).toBeVisible({ timeout: 15000 });

  await page.getByLabel("对话风格").selectOption("理性清晰");
  const focusMode = page.getByLabel("专注模式");
  const wasChecked = await focusMode.isChecked();
  await focusMode.setChecked(!wasChecked);
  await page.getByRole("button", { name: "保存偏好设置" }).click();
  await expect(page.getByText("偏好设置已保存")).toBeVisible({ timeout: 15000 });

  await page.reload();
  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByLabel("对话风格")).toHaveValue("理性清晰", { timeout: 15000 });
  await expect(page.getByLabel("专注模式")).toBeChecked({ checked: !wasChecked });
});
