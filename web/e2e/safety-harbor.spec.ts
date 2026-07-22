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

test("a crisis-triggering message surfaces a persistent, real crisis-resource card, not just a status line", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByLabel("写给 Aurora").fill("我最近撑不下去了，有点想死。");
  await page.getByRole("button", { name: "发送" }).click();

  const alert = page.getByRole("alert");
  await expect(alert).toBeVisible();
  await expect(alert).toContainText("先照顾好自己");
  // Real backend content (SafetyServiceImpl.resources()), not invented placeholder text.
  await expect(alert).toContainText("110");
  await expect(page.getByRole("link", { name: /拨打 110/ })).toHaveAttribute("href", "tel:110");

  await page.screenshot({ path: "test-results/safety-alert.png", fullPage: true });

  // A later, unrelated Aurora status update must not silently clear the alert.
  await page.waitForTimeout(1500);
  await expect(alert).toBeVisible();

  await alert.getByRole("button", { name: "我看到了，先关闭" }).click();
  await expect(alert).toBeHidden();
});

test("the safety harbor is reachable from Me space at any time and shows breathing, grounding and real resources", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^我的/ }).click();
  await page.getByRole("button", { name: "打开安全避风港" }).click();

  await expect(page.getByRole("heading", { name: "安全避风港" })).toBeVisible();
  await expect(page.getByText("你能看到的东西")).toBeVisible();
  await expect(page.getByText("喝一杯温水")).toBeVisible();
  await expect(page.getByRole("link", { name: /拨打 110/ })).toBeVisible();

  await page.screenshot({ path: "test-results/safety-harbor.png", fullPage: true });

  await page.getByRole("button", { name: "和 Aurora 聊聊" }).click();
  await expect(page.getByLabel("写给 Aurora")).toBeVisible();
});
