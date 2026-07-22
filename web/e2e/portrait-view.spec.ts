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

test("viewing a portrait dimension's history and submitting a \"not quite me\" calibration persists through the real backend", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^我的/ }).click();
  await expect(page.getByRole("heading", { name: "Aurora 眼中的你" })).toBeVisible({ timeout: 15000 });

  const card = page.locator(".portrait-dim-card").first();
  await expect(card).toBeVisible({ timeout: 15000 });

  await card.getByRole("button", { name: "看它怎么变的" }).click();
  await expect(card.locator(".portrait-history")).toBeVisible({ timeout: 15000 });

  await card.getByRole("button", { name: "这不太是我" }).click();
  await expect(card.locator(".portrait-calibrate")).toBeVisible();
  await card.getByPlaceholder("比如：我其实不是外向，只是在熟人面前才放得开…")
    .fill("这不完全是我，我其实更看重被认真倾听，而不是被安慰。");
  await card.getByRole("button", { name: "告诉 Aurora" }).click();

  await expect(card.locator(".portrait-note")).toBeVisible({ timeout: 15000 });
  await expect(card.locator(".portrait-note")).toContainText("Aurora 会把你的看法和它的观察并在一起");
  await expect(page.getByText("记下了。我会带着你这份看法继续理解你。")).toBeVisible({ timeout: 15000 });

  await page.screenshot({ path: "test-results/portrait-view.png", fullPage: true });
});
