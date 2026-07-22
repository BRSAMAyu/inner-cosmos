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

test("creating a group against the real backend shows it in the list and its owner as a member", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(page.getByPlaceholder("群组名字")).toBeVisible({ timeout: 15000 });

  const groupName = `测试群组-${Date.now()}`;
  await page.getByPlaceholder("群组名字").fill(groupName);
  await page.getByRole("button", { name: "创建群组" }).click();

  await expect(page.getByRole("button", { name: groupName })).toBeVisible({ timeout: 15000 });
  await page.getByRole("button", { name: groupName }).click();
  await expect(page.getByText("群主")).toBeVisible({ timeout: 15000 });
});
