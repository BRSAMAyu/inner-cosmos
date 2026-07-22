import { expect, test } from "@playwright/test";

async function dismissPwaBanner(page: import("@playwright/test").Page) {
  await page.evaluate(() => {
    document.querySelectorAll(".pwa-banner-stack, .pwa-banner-offline-ready")
      .forEach(el => { (el as HTMLElement).style.display = "none"; });
  });
}

// Sending a friend request is a real, persistent mutation of the actor's relationship graph.
// remaining-work-handoff.md 2.2.6 forbids doing this as the shared seeded "demo" account (other
// specs log in as demo and must find its relationship state exactly as seeded). A fresh throwaway
// account is the actor instead; "河岸来信" is a seeded discovery target, not demo itself, so it is
// fine for that side to receive the request.
async function registerThrowawayAccount(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/app/aurora/index.html");
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible();
  await dismissPwaBanner(page);
  await page.getByRole("tab", { name: "注册" }).click();
  await expect(page.getByRole("heading", { name: "开始你的内宇宙" })).toBeVisible();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码").fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(appShell).toBeVisible({ timeout: 15000 });
  await dismissPwaBanner(page);
}

test("sending a friend request to a discoverable person round-trips through the real backend and survives a reload", async ({ page }) => {
  await registerThrowawayAccount(page, `e2epeople${Date.now()}`, "throwaway-pw-1");

  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(page.getByRole("heading", { name: "主动认识人，但不催促任何关系" })).toBeVisible({ timeout: 15000 });

  const discovery = page.locator(".people-discovery");
  await expect(discovery.getByRole("listitem").first()).toBeVisible({ timeout: 15000 });

  // Target a specific seed showcase user (河岸来信 / river) rather than .first(), so the test
  // is deterministic regardless of what other accounts exist on the server.
  const card = discovery.getByRole("listitem").filter({ hasText: "河岸来信" });
  await expect(card).toBeVisible({ timeout: 15000 });

  // A fresh throwaway account never has a prior request against this target, so the button is
  // always available -- send the real request.
  const inviteBtn = card.getByRole("button", { name: "想认识 ta" });
  await expect(inviteBtn).toBeVisible({ timeout: 15000 });
  await card.scrollIntoViewIfNeeded();
  await inviteBtn.click({ force: true });
  await expect(card.getByText("已发出邀请")).toBeVisible({ timeout: 15000 });

  await page.screenshot({ path: "test-results/people-discovery.png", fullPage: true });

  // Real backend persistence, not just optimistic local state. The session cookie survives a
  // reload, so no re-login is needed.
  await page.reload();
  await dismissPwaBanner(page);
  await page.getByRole("button", { name: /^连接/ }).click();
  await expect(discovery.getByRole("listitem").filter({ hasText: "河岸来信" }).getByText("已发出邀请")).toBeVisible({ timeout: 15000 });
});
