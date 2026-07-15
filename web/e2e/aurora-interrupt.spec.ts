import { expect, test } from "@playwright/test";

async function loginIfNeeded(page: import("@playwright/test").Page) {
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const composer = page.getByLabel("写给 Aurora");
  await expect(login.or(composer)).toBeVisible();
  if (await login.isVisible()) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(composer).toBeVisible();
  return composer;
}

test("Aurora supports multi-bubble streaming, stop, interrupt and replanning", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  const composer = await loginIfNeeded(page);
  await composer.fill("我刚才说累，其实更准确的是害怕自己做不好。先陪我理一下。 ");
  const send = page.getByRole("button", { name: "发送" });
  await expect(send).toBeEnabled();
  await send.click();

  const interrupt = page.getByRole("button", { name: "打断并发送" });
  await expect(interrupt).toBeVisible();
  await composer.fill("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ");
  await interrupt.click();

  await expect(page.getByText("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ")).toBeVisible();
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);

  const stop = page.getByRole("button", { name: "停止回应" });
  if (await stop.isVisible().catch(() => false)) await stop.click();
  await expect(page.getByRole("status")).toContainText(/已停在这里|Aurora 在听|已从时间线恢复/);
});

test("Aurora resumes from the durable timeline after the live SSE connection breaks", async ({ page }) => {
  await page.addInitScript(() => {
    const nativeFetch = window.fetch.bind(window);
    let injected = false;
    Object.defineProperty(window, "__auroraSseFaultInjected", { get: () => injected });
    window.fetch = async (...args) => {
      const raw = args[0];
      const url = typeof raw === "string" ? raw : raw instanceof Request ? raw.url : String(raw);
      const response = await nativeFetch(...args);
      if (injected || !url.includes("/api/aurora/stream?") || !response.body) return response;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let observed = "";
      let failNextRead = false;
      const body = new ReadableStream<Uint8Array>({
        async pull(controller) {
          if (failNextRead) {
            injected = true;
            await reader.cancel("playwright injected live-SSE disconnect");
            controller.error(new TypeError("playwright injected live-SSE disconnect"));
            return;
          }
          const { done, value } = await reader.read();
          if (done) {
            controller.close();
            return;
          }
          controller.enqueue(value);
          observed = (observed + decoder.decode(value, { stream: true })).slice(-1024);
          if (/event:\s*turn\.started/.test(observed)) failNextRead = true;
        },
        cancel(reason) { return reader.cancel(reason); }
      });
      return new Response(body, {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers
      });
    };
  });

  await page.goto("/app/aurora/index.html");
  const composer = await loginIfNeeded(page);
  await composer.fill("如果网络突然断开，请仍然把这一刻完整地接回来。 ");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => page.evaluate(() => Boolean(
    (window as Window & { __auroraSseFaultInjected?: boolean }).__auroraSseFaultInjected
  ))).toBe(true);
  await expect(page.getByRole("status")).toContainText(/已从时间线恢复完整回应|已恢复到打断发生的位置/);
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);
});

test("user can create, postpone and cancel an explainable Aurora return", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  await page.getByLabel("回来时间").fill("2 小时后");
  await page.getByRole("button", { name: "和 Aurora 约好" }).click();
  const card = page.locator(".return-card").last();
  await expect(card).toContainText("因为还有话没有说完");
  await card.getByRole("button", { name: "晚一小时" }).click();
  await expect(page.getByRole("status")).toContainText("已为你推迟一小时");
  await card.getByRole("button", { name: "取消" }).click();
  await expect(card).toHaveCount(0);
  await expect(page.getByRole("status")).toContainText("已取消");
});

test("Aurora Self changes stay visible, evaluated, consented and rollbackable", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const selfSpace = page.getByRole("region", { name: "Aurora 的连续自我" });
  await expect(selfSpace).toBeVisible();
  await expect(selfSpace.getByRole("heading", { name: "她最近学会了什么" })).toBeVisible();

  const preview = selfSpace.getByRole("button", { name: "预览这次变化" }).first();
  if (await preview.isVisible().catch(() => false)) {
    await preview.click();
    const draft = selfSpace.locator(".self-card.draft").first();
    await expect(draft).toContainText("等待沙盒评测");
    await draft.getByRole("button", { name: "运行变化评测" }).click();
    const evaluated = selfSpace.locator(".self-card.evaluated").first();
    await expect(evaluated).toContainText("评测通过，等你确认");
    await evaluated.getByRole("button", { name: "允许她记住这次成长" }).click();
    await expect(selfSpace.locator(".self-version")).toContainText(/v(?:[2-9]|\d{2,})/);
    await expect(selfSpace.getByRole("button", { name: /回到 v1/ })).toBeVisible();
  } else {
    await expect(selfSpace.locator(".self-version")).toContainText(/v(?:[2-9]|\d{2,})/);
  }
});
