import { expect, test, type Page } from "@playwright/test";

async function login(page: Page) {
  await page.goto("/app/aurora/index.html");
  const loginHeading = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(loginHeading.or(appShell)).toBeVisible();
  if (await loginHeading.isVisible().catch(() => false)) {
    await page.getByLabel("用户名", { exact: true }).fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码", { exact: true }).fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录", exact: true }).click();
  }
  await expect(appShell).toBeVisible();
}

test("Aurora initial load stays within the deterministic loopback regression budget", async ({ page }) => {
  test.setTimeout(60_000);
  await page.addInitScript(() => {
    const state = window as unknown as {
      __innerCosmosVitals: { lcp: number; cls: number; longTasks: number[] };
    };
    state.__innerCosmosVitals = { lcp: 0, cls: 0, longTasks: [] };
    try {
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) state.__innerCosmosVitals.lcp = entry.startTime;
      }).observe({ type: "largest-contentful-paint", buffered: true });
    } catch { /* Browser does not expose this observer. */ }
    try {
      new PerformanceObserver(list => {
        for (const entry of list.getEntries() as Array<PerformanceEntry & { value: number; hadRecentInput: boolean }>) {
          if (!entry.hadRecentInput) state.__innerCosmosVitals.cls += entry.value;
        }
      }).observe({ type: "layout-shift", buffered: true });
    } catch { /* Browser does not expose this observer. */ }
    try {
      new PerformanceObserver(list => {
        for (const entry of list.getEntries()) state.__innerCosmosVitals.longTasks.push(entry.duration);
      }).observe({ type: "longtask", buffered: true });
    } catch { /* Browser does not expose this observer. */ }
  });

  const startedAt = Date.now();
  await login(page);
  await page.getByLabel("写给 Aurora").waitFor({ state: "visible", timeout: 30_000 });
  const interactiveMs = Date.now() - startedAt;
  await page.waitForTimeout(2_000);

  const metrics = await page.evaluate(() => {
    const navigation = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    const firstContentfulPaint = performance.getEntriesByType("paint")
      .find(entry => entry.name === "first-contentful-paint")?.startTime ?? null;
    const state = (window as unknown as {
      __innerCosmosVitals: { lcp: number; cls: number; longTasks: number[] };
    }).__innerCosmosVitals;
    return {
      ttfb: navigation ? navigation.responseStart - navigation.requestStart : null,
      domContentLoaded: navigation ? navigation.domContentLoadedEventEnd - navigation.startTime : null,
      loadEvent: navigation ? navigation.loadEventEnd - navigation.startTime : null,
      fcp: firstContentfulPaint,
      lcp: state.lcp || null,
      cls: state.cls,
      tbtApprox: state.longTasks.reduce((sum, duration) => sum + Math.max(0, duration - 50), 0),
      longTaskCount: state.longTasks.length,
    };
  });

  const evidence = { ...metrics, composerInteractiveMs: interactiveMs };
  console.log(`PERF_METRICS ${JSON.stringify(evidence)}`);
  expect(interactiveMs, JSON.stringify(evidence)).toBeLessThan(15_000);
  expect(metrics.cls, JSON.stringify(evidence)).toBeLessThan(0.1);
});
