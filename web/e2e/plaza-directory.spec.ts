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

test("searching and filtering the resonance plaza narrows results against the real backend", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^共鸣/ }).click();
  await expect(page.getByRole("heading", { name: "主动走进广场，而不是只等推荐" })).toBeVisible({ timeout: 15000 });

  const plaza = page.locator(".plaza-directory");
  await expect(plaza.getByRole("listitem").first()).toBeVisible({ timeout: 15000 });

  // A query matching one seed capsule's name narrows the real, server-provided list.
  await plaza.getByLabel("搜索公开共鸣体").fill("苏格拉底");
  await expect(plaza.getByRole("listitem")).toHaveCount(1);
  await expect(plaza.getByRole("listitem").locator("strong")).toHaveText("苏格拉底");

  // A query matching nothing shows the real empty-filter state, not a stale list.
  await plaza.getByLabel("搜索公开共鸣体").fill("这个名字在广场上不存在xyz123");
  await expect(page.getByText("没有符合当前筛选的共鸣体")).toBeVisible();

  await plaza.getByLabel("搜索公开共鸣体").fill("");
  await expect(plaza.getByRole("listitem").first()).toBeVisible({ timeout: 15000 });
});

test("opening a capsule from the plaza reveals a real, authorized-AI preview", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^共鸣/ }).click();
  const plaza = page.locator(".plaza-directory");
  await plaza.getByLabel("搜索公开共鸣体").fill("苏格拉底");

  const card = plaza.getByRole("listitem").filter({ hasText: "苏格拉底" });
  await expect(card).toBeVisible({ timeout: 15000 });
  await card.getByRole("button", { name: "开始对话" }).click();

  const workbench = page.locator(".visitor-workbench");
  await expect(workbench).toBeVisible({ timeout: 15000 });
  await expect(workbench.locator("h3")).toHaveText("苏格拉底");
  await expect(workbench.getByText("授权 AI 共鸣体 · 不是真人实时在线")).toBeVisible();

  await page.screenshot({ path: "test-results/plaza-directory.png", fullPage: true });

  // Regression (remaining-work-handoff.md 2.2.6, "共鸣体 preview 至少发送一个授权 turn"): the test
  // used to stop at "the workbench opened", never actually exercising the real persona-chat round
  // trip a capsule preview exists for. Enter the conversation and send one real, authorized turn.
  await workbench.getByRole("button", { name: "进入有限但自然的对话" }).click();
  await expect(workbench.getByLabel("写给共鸣体")).toBeVisible({ timeout: 15000 });

  await workbench.getByLabel("写给共鸣体").fill("你如何理解认识你自己这句话？");
  await workbench.getByRole("button", { name: "发送这一轮" }).click();

  const history = workbench.locator(".persona-history");
  await expect(history.locator("article.visitor")).toContainText("你如何理解认识你自己这句话？", { timeout: 15000 });
  await expect(history.locator("article.capsule")).toBeVisible({ timeout: 20000 });

  await page.screenshot({ path: "test-results/plaza-directory-turn.png", fullPage: true });
});
