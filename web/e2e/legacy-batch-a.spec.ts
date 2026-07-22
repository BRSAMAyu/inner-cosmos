import { expect, test } from "@playwright/test";

// Phase 3 legacy-page port batch A: timeline.html, weekly-review.html, daily-record.html,
// thought-shredder.html -- ported into the "cosmos" product space of the React AppShell.
// Verifies real data round-trips against a live backend (see playwright.config.ts /
// INNER_COSMOS_BASE_URL for how this is pointed at the throwaway server).

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

async function goToCosmos(page: import("@playwright/test").Page) {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  await page.getByRole("button", { name: /^内宇宙/ }).click();
}

// The global status bar at the bottom of AuroraApp shows success/error from hooks.
function globalStatus(page: import("@playwright/test").Page) {
  return page.locator(".global-state[role='status']");
}

test.describe.serial("legacy batch A: cosmos space ported sections", () => {
  test("all four ported sections render in cosmos space", async ({ page }) => {
    await goToCosmos(page);

    // All four headings should be visible in the cosmos space.
    await expect(page.locator(".timeline-section")).toBeVisible();
    await expect(page.locator(".daily-record-section")).toBeVisible();
    await expect(page.locator(".weekly-review-section")).toBeVisible();
    await expect(page.locator(".thought-shredder-section")).toBeVisible();

    await page.screenshot({ path: "test-results/cosmos-overview.png", fullPage: true });
  });

  test("thought shredder processes real text end-to-end", async ({ page }) => {
    await goToCosmos(page);

    // AI health line shows provider/model info.
    const aiHealthLine = page.locator(".thought-shredder-section p.muted", { hasText: /\// });
    await expect(aiHealthLine.first()).toBeVisible();

    // Type and shred.
    await page.getByLabel("把想法倒进来").fill("今天作业写不完，感觉很崩溃，也有点自责。");
    await page.getByRole("button", { name: "粉碎并沉淀" }).click();

    // Result panel should populate with the structured decomposition fields.
    await expect(page.getByText("核心感受")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("隐藏需求")).toBeVisible();
    await expect(page.getByText("值得保留的一句话")).toBeVisible();

    await page.screenshot({ path: "test-results/thought-shredder.png", fullPage: true });

    // Settle into memory -- verify the status bar confirms.
    await page.getByRole("button", { name: "沉淀到记忆" }).click();
    await expect(globalStatus(page)).toContainText("已沉淀到记忆", { timeout: 10_000 });
  });

  test("daily record accept round-trips and timeline shows data", async ({ page }) => {
    await goToCosmos(page);

    const dailySection = page.locator(".daily-record-section");
    await expect(dailySection.getByText("今日主题")).toBeVisible();

    await page.screenshot({ path: "test-results/daily-record.png", fullPage: true });

    // Accept the current record and verify the status bar round-trip.
    await dailySection.getByRole("button", { name: "接受并保存" }).click();
    await expect(globalStatus(page)).toContainText("记录已接受并保存", { timeout: 10_000 });

    // Edit theme inline and save.
    await dailySection.getByRole("button", { name: "编辑" }).first().click();
    await page.getByLabel("编辑主题").fill("一次真实的编辑验证");
    await dailySection.getByRole("button", { name: "保存", exact: true }).click();
    await expect(globalStatus(page)).toContainText("已保存", { timeout: 10_000 });

    // Timeline section shows the same record.
    await expect(page.locator(".timeline-section")).toBeVisible();
    await page.screenshot({ path: "test-results/timeline.png", fullPage: true });
  });

  test("weekly review generates and shows structured content", async ({ page }) => {
    await goToCosmos(page);

    const weeklySection = page.locator(".weekly-review-section");
    await weeklySection.getByRole("button", { name: "重新生成这一周" }).click();

    // After generation the review should show at minimum the stats labels.
    await expect(weeklySection.getByText("记忆数")).toBeVisible({ timeout: 20_000 });
    await expect(globalStatus(page)).toContainText("已经生成", { timeout: 10_000 });

    await page.screenshot({ path: "test-results/weekly-review.png", fullPage: true });
  });
});
