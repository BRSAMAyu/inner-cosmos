import { expect, test } from "@playwright/test";

const ADMIN_USER = process.env.E2E_ADMIN_USERNAME ?? "admin";
const ADMIN_PASS = process.env.E2E_ADMIN_PASSWORD ?? "admin123";

/**
 * Phase 3 e2e for the React AdminConsole port (standalone /admin route).
 * Logs in as the seeded admin, verifies overview metrics, switches through
 * every tab, and performs a real report-resolution round-trip against the
 * backend.  Legacy /pages/admin.html is NOT retired by this spec -- it only
 * proves the new React console has backend-verified parity.
 */

async function loginAsAdmin(page: import("@playwright/test").Page) {
  await page.goto("/app/aurora/");
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(ADMIN_USER);
    await page.getByLabel("密码").fill(ADMIN_PASS);
    await page.getByRole("button", { name: "登录" }).click();
    // Wait for the session to be established before navigating away --
    // a goto() before login completes will land back on the login screen.
    await expect(appShell).toBeVisible({ timeout: 15_000 });
    const offlineNotice = page.getByRole("button", { name: "知道了" });
    if (await offlineNotice.isVisible().catch(() => false)) await offlineNotice.click();
  }
}

async function dismissPwaBanner(page: import("@playwright/test").Page) {
  await page.evaluate(() => {
    document.querySelectorAll(".pwa-banner-stack, .pwa-banner-offline-ready")
      .forEach(el => { (el as HTMLElement).style.display = "none"; });
  });
}

// Regression (Gemini audit / doc 24's four-branch integration table): the resolve test below
// used to skip its own write-path assertion whenever the seed happened to have no pending report
// at the moment it ran ("如果没有 pending report 就 skip"). Create one deterministically instead,
// from a fresh throwaway account (never "demo"), by opening a real persona chat with a seeded
// public capsule and reporting that session -- a genuine tb_report_record row every run.
async function createRealPendingReport(page: import("@playwright/test").Page) {
  const username = `e2ereporter${Date.now()}`;
  await page.goto("/app/aurora/index.html");
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible();
  await dismissPwaBanner(page);
  await page.getByRole("tab", { name: "注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill("throwaway-pw-1");
  await page.getByLabel("确认密码").fill("throwaway-pw-1");
  await page.getByRole("button", { name: "创建账号" }).click();
  await expect(page.getByRole("navigation", { name: "Inner Cosmos 五个空间" })).toBeVisible({ timeout: 15000 });
  await dismissPwaBanner(page);

  await page.evaluate(async () => {
    const capsules = await fetch("/api/plaza/capsules").then(r => r.json());
    const target = capsules.data.find((c: { pseudonym: string }) => c.pseudonym === "苏格拉底");
    const session = await fetch("/api/v1/persona-chat/session/create", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ capsuleId: target.id })
    }).then(r => r.json());
    await fetch(`/api/persona-chat/session/${session.data.id}/report`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: "E2E deterministic pending-report fixture" })
    });
  });

  // Log out the throwaway reporter so loginAsAdmin's subsequent goto() actually sees the login
  // form again, instead of silently staying authenticated as the wrong (non-admin) account.
  await page.evaluate(() => fetch("/api/auth/logout", { method: "POST" }));
  await page.goto("/app/aurora/index.html");
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible({ timeout: 15000 });
}

test("admin console shows overview metrics and all 8 tabs with real seed data", async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto("/app/aurora/admin");

  // Overview hero + metrics section.
  await expect(page.getByRole("heading", { name: "管理后台" })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("注册用户")).toBeVisible();
  await expect(page.getByText("公开共鸣体")).toBeVisible();

  await page.screenshot({ path: "test-results/admin-overview.png", fullPage: true });

  // Walk every tab and confirm it renders without crashing.
  const tabs = ["用户", "共鸣体", "举报", "A/B 测试", "AI 日志", "安全", "模型", "审计"] as const;
  for (const label of tabs) {
    await page.getByRole("tab", { name: label }).click();
    // Each tab panel should remain visible (no white-screen crash).
    await expect(page.getByRole("tabpanel")).toBeVisible();
  }

  await page.screenshot({ path: "test-results/admin-tabs.png", fullPage: true });
});

test("resolving a pending report round-trips through the real backend", async ({ page }) => {
  // Regression: this used to skip its own write-path assertion whenever the seed happened to
  // have no pending report at the moment it ran. Create a real one deterministically first.
  await createRealPendingReport(page);

  await loginAsAdmin(page);
  await page.goto("/app/aurora/admin");
  await expect(page.getByRole("heading", { name: "管理后台" })).toBeVisible({ timeout: 15_000 });

  // Switch to the reports tab.
  await page.getByRole("tab", { name: "举报" }).click();

  const pendingBadge = page.locator(".admin-badge.is-pending").first();
  await expect(pendingBadge).toBeVisible({ timeout: 15_000 });

  // Click "忽略" (dismiss) on the first pending report.
  await page.getByRole("button", { name: "忽略" }).first().click();
  // Enter a reason and confirm.
  const reasonInput = page.locator(".admin-reason-form input").first();
  await expect(reasonInput).toBeVisible();
  await reasonInput.fill("E2E automated dismissal");
  await page.getByRole("button", { name: "确认处理" }).click();

  // The status banner should confirm the action.
  await expect(page.getByRole("status")).toContainText("举报已处理", { timeout: 15_000 });

  await page.screenshot({ path: "test-results/admin-report-resolved.png", fullPage: true });
});

test("non-admin user is redirected away from /admin", async ({ page }) => {
  // Log in as the regular demo user.
  await page.goto("/app/aurora/");
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(appShell).toBeVisible();

  // Try to visit /admin -- should redirect back to a consumer space.
  await page.goto("/app/aurora/admin");
  await expect(appShell).toBeVisible({ timeout: 10_000 });
  // The admin console heading must NOT appear for a non-admin.
  await expect(page.getByRole("heading", { name: "管理后台" })).toHaveCount(0);
});
