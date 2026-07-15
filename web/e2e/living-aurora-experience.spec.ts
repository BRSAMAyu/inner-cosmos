import { expect, test } from "@playwright/test";

async function login(page: import("@playwright/test").Page) {
  await page.goto("/app/aurora/index.html");
  await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
  await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByLabel("写给 Aurora")).toBeVisible();
}

test("a durable Aurora return arrives live, deep-links to its context, and records feedback", async ({ page }) => {
  await login(page);
  const original = "明天的汇报让我害怕。我想先被接住，之后再拆第一步。";
  await page.getByLabel("写给 Aurora").fill(original);
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText(original)).toBeVisible();
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);

  await page.getByLabel("回来时间").fill("1 分钟后");
  await page.getByRole("button", { name: "和 Aurora 约好" }).click();
  const agreement = page.locator(".return-card").last();
  await expect(agreement).toContainText("因为还有话没有说完");

  // Keep the public natural-language contract while moving its persisted window close enough
  // for a deterministic browser test. The real scheduler, relevance check and notification
  // transaction still execute; no delivery endpoint or database shortcut is used.
  await page.waitForTimeout(750);
  const intentId = await page.evaluate(async () => {
    const get = async (url: string) => {
      const envelope = await fetch(url, { credentials: "include" }).then(response => response.json());
      if (!envelope.success) throw new Error(envelope.message ?? url);
      return envelope.data;
    };
    const active = await get("/api/aurora/wake-intents") as Array<{ id: number }>;
    const intent = active.at(-1);
    if (!intent) throw new Error("wake intent was not persisted");
    const localIso = (value: number) => {
      const date = new Date(value);
      return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
    };
    const preferred = Date.now() + 4_000;
    for (let attempt = 0; attempt < 2; attempt++) {
      const csrf = await get(`/api/auth/csrf?fresh=${Date.now()}-${attempt}`) as { headerName: string; token: string };
      const response = await fetch(`/api/aurora/wake-intents/${intent.id}/schedule`, {
        method: "PUT", credentials: "include", headers: {
          "Content-Type": "application/json", [csrf.headerName]: csrf.token
        }, body: JSON.stringify({
          earliestAt: localIso(preferred - 1_000),
          preferredAt: localIso(preferred),
          latestAt: localIso(preferred + 60_000)
        })
      });
      if (response.ok) break;
      if (response.status !== 403 || attempt === 1) throw new Error(await response.text());
    }
    return intent.id;
  });

  const arrival = page.getByRole("region", { name: "Aurora 按约定回来" });
  await expect(arrival).toBeVisible();
  await expect(arrival).toContainText("我回来了");
  await expect(page.getByRole("status")).toContainText("耐久通知");
  await expect(agreement).toHaveCount(0);

  await arrival.getByRole("link", { name: "回到当时没说完的地方" }).click();
  await expect(page).toHaveURL(new RegExp(`wakeIntent=${intentId}$`));
  await expect(page.getByText(original)).toBeVisible();
  await expect(page.getByLabel("写给 Aurora")).toBeVisible();

  await page.getByRole("region", { name: "Aurora 按约定回来" })
    .getByRole("button", { name: "正合适" }).click();
  await expect(page.getByRole("status")).toContainText("记住这次节奏");
  await expect(page.getByRole("region", { name: "Aurora 按约定回来" })).toHaveCount(0);
  await expect.poll(async () => page.evaluate(async id => {
    const envelope = await fetch(`/api/aurora/wake-intents/${id}`, { credentials: "include" }).then(response => response.json());
    return envelope.data?.userFeedback;
  }, intentId)).toBe("MATCHED");
});
