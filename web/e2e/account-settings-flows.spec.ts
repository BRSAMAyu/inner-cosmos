import { expect, test } from "@playwright/test";

// account-preferences.spec.ts already covers AccountSettings' Aurora-preferences panel.
// This file covers AccountSettings' other three flows: change password, export data, and
// the delete-account confirmation. Each uses a throwaway account registered by the test
// itself (never the shared seeded "demo" account) so the destructive delete-account flow
// can be run all the way to completion against the real backend without harming seed data.

// The PWA offline-ready banner can overlay the auth form and intercept clicks. It re-renders
// after dismissal, so a single click is not enough; hide the entire banner stack via JS.
async function dismissPwaBanner(page: import("@playwright/test").Page) {
  await page.evaluate(() => {
    document.querySelectorAll(".pwa-banner-stack, .pwa-banner-offline-ready")
      .forEach(el => { (el as HTMLElement).style.display = "none"; });
  });
}

async function registerThrowawayAccount(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/app/aurora/index.html");
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible();
  await dismissPwaBanner(page);
  await page.getByRole("tab", { name: "注册" }).click();
  await expect(page.getByRole("heading", { name: "开始你的内宇宙" })).toBeVisible();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码").fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();

  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(appShell).toBeVisible({ timeout: 15000 });
  await dismissPwaBanner(page);
}

test("changing password validates input, then round-trips through the real backend", async ({ page }) => {
  const username = `e2eacct${Date.now()}`;
  const oldPassword = "throwaway-pw-1";
  const newPassword = "throwaway-pw-2";
  await registerThrowawayAccount(page, username, oldPassword);

  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByRole("heading", { name: "账户与数据" })).toBeVisible({ timeout: 15000 });

  await page.getByRole("button", { name: "修改密码", exact: true }).click();
  await page.getByPlaceholder("当前密码").fill(oldPassword);
  await page.getByPlaceholder("新密码（至少 8 位）").fill("short");
  await page.getByPlaceholder("再次输入新密码").fill("short");
  await page.getByRole("button", { name: "确认修改" }).click();
  await expect(page.getByText("新密码至少 8 位")).toBeVisible();

  await page.getByPlaceholder("新密码（至少 8 位）").fill(newPassword);
  await page.getByPlaceholder("再次输入新密码").fill("mismatched-confirm");
  await page.getByRole("button", { name: "确认修改" }).click();
  await expect(page.getByText("两次输入的新密码不一致")).toBeVisible();

  await page.getByPlaceholder("再次输入新密码").fill(newPassword);
  await page.getByRole("button", { name: "确认修改" }).click();
  await expect(page.getByText("密码已更新")).toBeVisible({ timeout: 15000 });

  await page.screenshot({ path: "test-results/account-settings-password.png", fullPage: true });

  // Prove it is a real backend round-trip: the old password no longer works, the new one does.
  await page.getByRole("button", { name: "安全退出这台设备" }).click();
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible({ timeout: 15000 });
  await dismissPwaBanner(page);

  await page.getByLabel("用户名").fill(username);
  await page.getByLabel("密码").fill(oldPassword);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByText("用户名或密码不正确")).toBeVisible({ timeout: 15000 });

  await page.getByLabel("密码").fill(newPassword);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByRole("navigation", { name: "Inner Cosmos 五个空间" })).toBeVisible({ timeout: 15000 });
});

test("exporting account data downloads a real JSON file from the backend", async ({ page }) => {
  const username = `e2eacct${Date.now()}exp`;
  await registerThrowawayAccount(page, username, "throwaway-pw-1");

  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByRole("heading", { name: "账户与数据" })).toBeVisible({ timeout: 15000 });

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "导出数据" }).click()
  ]);
  expect(download.suggestedFilename()).toMatch(/^inner-cosmos-export-\d{4}-\d{2}-\d{2}\.json$/);
  await expect(page.getByText("数据已导出")).toBeVisible({ timeout: 15000 });

  const exportPath = await download.path();
  expect(exportPath).toBeTruthy();

  await page.screenshot({ path: "test-results/account-settings-export.png", fullPage: true });
});

test("the delete-account flow validates confirmation input, then permanently removes a throwaway account", async ({ page }) => {
  const username = `e2eacct${Date.now()}del`;
  const password = "throwaway-pw-1";
  await registerThrowawayAccount(page, username, password);

  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByRole("heading", { name: "账户与数据" })).toBeVisible({ timeout: 15000 });

  await page.getByRole("button", { name: "删除账户" }).click();
  const warning = page.locator(".account-warning");
  await expect(warning).toContainText("这将永久删除你的账户和所有数据");
  await expect(warning.locator("strong")).toHaveText("此操作不可撤销。");

  // Submitting without a password surfaces the real client-side confirmation guard.
  await page.getByRole("button", { name: "确认删除" }).click();
  await expect(page.getByText("请输入密码以确认")).toBeVisible();

  await page.screenshot({ path: "test-results/account-settings-delete-confirm.png", fullPage: true });

  // This is a throwaway, self-registered account (never the seeded "demo" account), so it is
  // safe to complete the deletion for real and verify the backend actually removed it.
  await page.getByPlaceholder("密码").fill(password);
  await page.getByRole("button", { name: "确认删除" }).click();

  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible({ timeout: 15000 });
  await dismissPwaBanner(page);

  await page.getByLabel("用户名").fill(username);
  await page.getByLabel("密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByText("用户名或密码不正确")).toBeVisible({ timeout: 15000 });
});
