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
  // Belief gallery lives behind the "信念与自我理解" cosmos sub-tab (doc 24 section 3.3: ported
  // legacy modules must not stack vertically in one page).
  await page.getByRole("navigation", { name: "内宇宙分区导航" }).getByRole("button", { name: "信念与自我理解", exact: true }).click();
  await expect(page.getByRole("heading", { name: "信念画廊" })).toBeVisible();
}

// The contradictions section repeats belief content text, so scope assertions to .belief-list
// (the main gallery, not the contradictions block).

test("seeded belief patterns are visible with strength scores", async ({ page }) => {
  await gotoCosmos(page);

  const beliefList = page.locator(".belief-list").first();
  await expect(beliefList.getByText("做不好一件事，并不代表我整个人不行。")).toBeVisible();
  await expect(beliefList.getByText("只要拆出足够小的第一步，我就能推进下去。")).toBeVisible();

  await expect(page.getByText(/强度\s*62%/).first()).toBeVisible();
  await expect(page.getByText(/强度\s*70%/).first()).toBeVisible();

  await page.screenshot({ path: "test-results/legacy-belief-seeded.png", fullPage: true });
});

test("strong-belief filter shows only beliefs above 0.5 strength", async ({ page }) => {
  await gotoCosmos(page);

  await page.getByRole("tab", { name: /强信念/ }).click();

  // The weaker belief (0.48 strength) should NOT be in the main belief list.
  const beliefList = page.locator(".belief-list").first();
  await expect(beliefList.getByText("在高压时，我没办法证明自己足够好。")).toBeHidden();

  // The strong beliefs should still be visible.
  await expect(beliefList.getByText("做不好一件事，并不代表我整个人不行。")).toBeVisible();

  await page.screenshot({ path: "test-results/legacy-belief-strong.png", fullPage: true });
});

test("by-category filter shows categories and lets user select one", async ({ page }) => {
  await gotoCosmos(page);

  await page.getByRole("tab", { name: "按类别" }).click();

  await expect(page.getByRole("button", { name: "self_worth" })).toBeVisible();
  await expect(page.getByRole("button", { name: "agency" })).toBeVisible();

  await page.getByRole("button", { name: "self_worth" }).click();

  // After selecting a category, the beliefs render in a .belief-list.
  const categoryList = page.locator(".belief-list").first();
  await expect(categoryList.getByText("做不好一件事，并不代表我整个人不行。")).toBeVisible();
  await expect(categoryList.getByText("在高压时，我没办法证明自己足够好。")).toBeVisible();

  await page.screenshot({ path: "test-results/legacy-belief-category.png", fullPage: true });
});
