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

test("sending a friend request to a discoverable person round-trips through the real backend and survives a reload", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);

  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(page.getByRole("heading", { name: "主动认识人，但不催促任何关系" })).toBeVisible({ timeout: 15000 });

  const discovery = page.locator(".people-discovery");
  await expect(discovery.getByRole("listitem").first()).toBeVisible({ timeout: 15000 });

  // Target a specific seed showcase user (河岸来信 / river) rather than .first(), so the test
  // is deterministic regardless of what other accounts exist on the server.
  const card = discovery.getByRole("listitem").filter({ hasText: "河岸来信" });
  await expect(card).toBeVisible({ timeout: 15000 });

  // If this is a fresh database, the "想认识 ta" button is available; click it to send the
  // real friend request. If a prior test run already sent one (same in-memory H2 on a
  // long-lived throwaway server), the card already shows "已发出邀请" — either state proves
  // the backend round-trip works, so accept both.
  const inviteBtn = card.getByRole("button", { name: "想认识 ta" });
  if (await inviteBtn.isVisible().catch(() => false)) {
    await card.scrollIntoViewIfNeeded();
    await inviteBtn.click({ force: true });
    await expect(card.getByText("已发出邀请")).toBeVisible({ timeout: 15000 });
  } else {
    await expect(card.getByText("已发出邀请")).toBeVisible({ timeout: 15000 });
  }

  await page.screenshot({ path: "test-results/people-discovery.png", fullPage: true });

  // Real backend persistence, not just optimistic local state.
  await page.reload();
  await loginIfNeeded(page);
  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(discovery.getByRole("listitem").filter({ hasText: "河岸来信" }).getByText("已发出邀请")).toBeVisible({ timeout: 15000 });
});
