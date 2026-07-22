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
  await page.evaluate(() => {
    document.querySelectorAll(".pwa-banner-stack, .pwa-banner-offline-ready")
      .forEach(el => { (el as HTMLElement).style.display = "none"; });
  });
}

test("selecting a relation mention shows its real warmth score and timeline from the backend", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(page.getByRole("heading", { name: "关系的温度，慢慢看清" })).toBeVisible({ timeout: 15000 });

  const relations = page.locator(".relations-view");
  await expect(relations.getByRole("button", { name: /同组朋友/ })).toBeVisible({ timeout: 15000 });
  await relations.getByRole("button", { name: /同组朋友/ }).click();

  // Real health score computed by RelationNetworkServiceImpl.calculateHealthScore from the
  // seeded mention (no "积极" emotion tag -> 0% -> "需要关照").
  await expect(relations.locator(".relation-temperature")).toBeVisible({ timeout: 15000 });
  await expect(relations.getByText("关系温度")).toBeVisible();
  await expect(relations.locator(".relation-temp-row")).toContainText("需要关照");

  await expect(relations.locator(".relation-timeline-title")).toContainText("同组朋友");
  await expect(relations.locator(".relation-timeline-point").first()).toContainText("委屈");
  await expect(relations.locator(".relation-timeline-point").first()).toContainText("一句玩笑触发了用户对被轻视的担心");

  await page.screenshot({ path: "test-results/relations-view.png", fullPage: true });

  // Selecting a different relation swaps the timeline rather than appending to it.
  await relations.getByRole("button", { name: /未来的共鸣者/ }).click();
  await expect(relations.locator(".relation-timeline-title")).toContainText("未来的共鸣者");
  await expect(relations.locator(".relation-timeline-point")).toHaveCount(1);
});
