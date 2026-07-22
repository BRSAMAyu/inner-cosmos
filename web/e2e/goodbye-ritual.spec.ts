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

test("the goodbye ritual shows a real farewell line in a distinct closing card, then dismisses", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  // Establish a real session before the ritual can be triggered against a real turn/context.
  await page.getByLabel("写给 Aurora").fill("今天想先记录一下现在的状态。");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByRole("button", { name: "沉淀今天" })).toBeVisible({ timeout: 15000 });

  await page.getByRole("button", { name: "沉淀今天" }).click();

  const card = page.locator(".goodbye-ritual-card");
  await expect(card).toBeVisible({ timeout: 15000 });
  await expect(card.locator(".goodbye-ritual-line")).not.toBeEmpty();

  await page.screenshot({ path: "test-results/goodbye-ritual.png", fullPage: true });

  await card.getByRole("button", { name: "好，先到这里" }).click();
  await expect(card).toBeHidden();
});
