import { expect, test } from "@playwright/test";

/**
 * CP-43 (closing-checklist §2-22) — the automatable slice of the 大陆 Web/PWA submission
 * checklist (docs/commercialization/store-submissions/web-pwa.checklist.yml), exercised
 * through every declared browser-matrix project (chromium baseline; firefox/webkit legs
 * run where those engines are installed — see playwright.config.ts).
 *
 * Covers, per checklist item:
 *  - pwa-update (Service Worker 更新…): the service worker must actually register,
 *    activate and control the app scope. The "旧 bundle/新 API 兼容" upgrade-interruption
 *    record itself stays with the operator — an automated smoke can never stand in for it.
 *  - 发布完整性静态腿 (shared by download-parity): the web manifest and every icon it
 *    declares are served intact, so an installed PWA shell can resolve them.
 *
 * Deliberately NOT claimed here (operator receipts only): domain-icp 备案回执, https-cert
 * 公网证书, account-flows J01/J09 演示记录, payment-permit 法务意见, seo-public-only
 * 抓取审计 — none of these have an automatable PASS on a local run.
 */
test.describe("CP-43 web/PWA submission smoke — automatable checklist slice", () => {
  test("serves the app shell with an installable manifest and all declared icons", async ({ page, request }) => {
    const response = await page.goto("/app/aurora/");
    expect(response?.status()).toBeLessThan(400);

    const manifestHref = await page.locator('link[rel="manifest"]').first().getAttribute("href");
    expect(manifestHref, "app shell must declare <link rel=manifest>").toBeTruthy();

    const manifestUrl = new URL(manifestHref!, page.url()).toString();
    const manifestResponse = await request.get(manifestUrl);
    expect(manifestResponse.status(), `manifest ${manifestUrl} must be served`).toBe(200);

    const manifest = await manifestResponse.json() as {
      name?: string;
      short_name?: string;
      icons?: Array<{ src: string; sizes?: string; type?: string }>;
    };
    expect(manifest.name, "manifest.name is required for installability").toBeTruthy();
    expect(manifest.short_name).toBeTruthy();
    expect(manifest.icons?.length, "manifest must declare icons").toBeGreaterThan(0);

    for (const icon of manifest.icons ?? []) {
      const iconUrl = new URL(icon.src, manifestUrl).toString();
      const iconResponse = await request.get(iconUrl);
      expect(
        iconResponse.status(),
        `declared icon ${icon.src} (${icon.sizes ?? "?"}) must be served`,
      ).toBe(200);
    }
  });

  test("registers and activates the service worker over the app scope (pwa-update leg)", async ({ page }) => {
    await page.goto("/app/aurora/");

    // The registration is immediate but activation lands a few seconds later, and
    // navigator.serviceWorker.ready may resolve before .active is populated — so poll for
    // the installed-and-activated worker instead of trusting ready. This is the
    // automatable half of the pwa-update item: register → install → activate.
    await expect
      .poll(
        async () => page.evaluate(async () => {
          const registration = await navigator.serviceWorker.getRegistration();
          return registration?.active?.scriptURL ?? "";
        }),
        { timeout: 30_000, message: "service worker must install and activate" },
      )
      .toContain("/app/aurora/sw.js");

    // This build registers with an explicit update prompt (no clientsClaim), so the
    // current page is deliberately not yet controlled — control starts on the next load.
    // Reload once and require the worker to actually control the app shell: that is the
    // offline/update-capable state the pwa-update item is about.
    await page.reload();
    const controller = await page.evaluate(
      () => navigator.serviceWorker.controller?.scriptURL ?? "",
    );
    expect(controller, "after reload the service worker must control the app shell").toContain(
      "/app/aurora/sw.js",
    );
  });
});
