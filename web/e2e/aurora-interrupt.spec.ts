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

  await expect(page.getByLabel("Aurora 当前回应状态")).toContainText(/正在理解|正在组织|正在回应|在这里/);

  const interrupt = page.getByRole("button", { name: "打断并发送" });
  await expect(interrupt).toBeVisible();
  await composer.fill("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ");
  await interrupt.click();

  await expect(page.getByText("等等，我不想先分析原因。我想先确认这种害怕是不是可以被接住。 ")).toBeVisible();
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);
  await expect(page.getByLabel("Aurora 当前回应状态")).toContainText("理解与表达双核协作");

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

test("user previews and confirms an authoritative understanding correction", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const space = page.getByRole("region", { name: "校准 Aurora 对我的理解" });
  await expect(space.getByRole("heading", { name: "如果这不太是你" })).toBeVisible();
  await space.getByLabel("Aurora 原先怎样理解（可选）").fill("我总是在逃避");
  await space.getByLabel("更准确的你是").fill("我是在谨慎选择下一步");
  await space.getByRole("button", { name: "预览会改变什么" }).click();
  const preview = space.getByRole("region", { name: "纠正影响预览" });
  await expect(preview).toContainText("Aurora 对你的当前理解");
  await expect(space.locator(".claim-card")).toHaveCount(0);
  await preview.getByRole("button", { name: "确认，这是更准确的我" }).click();
  await expect(space.locator(".claim-card").first()).toContainText("我是在谨慎选择下一步");
  await expect(page.getByRole("status")).toContainText("已校准");
});

test("memory starfield switches between time, theme and people without losing its accessible view", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const cosmos = page.getByRole("region", { name: "记忆星空" });
  await expect(cosmos.getByRole("heading", { name: "你的记忆不是档案柜" })).toBeVisible();
  const accessible = cosmos.getByRole("list", { name: "记忆星空可访问列表" });
  await expect(accessible.locator("li").first()).toBeVisible();
  await accessible.locator("li").first().getByRole("button", { name: "查看来源与变化" }).click();
  const provenance = cosmos.getByLabel("记忆来源与变化");
  await expect(provenance).toContainText("为什么它在这里");
  await expect(provenance).toContainText("当前版本");
  await provenance.getByRole("button", { name: "关闭记忆来源" }).click();
  await expect(cosmos.getByRole("button", { name: "时间" })).toHaveAttribute("aria-pressed", "true");
  await cosmos.getByRole("button", { name: "主题" }).click();
  await expect(cosmos.locator(".cosmos-explanation")).toContainText("相近主题聚成星座");
  await expect(cosmos.getByRole("button", { name: "主题" })).toHaveAttribute("aria-pressed", "true");
  await cosmos.getByRole("button", { name: "人物" }).click();
  await expect(cosmos.locator(".cosmos-explanation")).toContainText("人物标签形成共同轨道");
  await expect(accessible.locator("li").first()).toBeVisible();
});

test("user can roll back a memory change while permanent forgetting stays irreversible", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const originalTitle = await page.getByRole("list", { name: "记忆星空可访问列表" }).locator("li strong").first().innerText();
  await page.evaluate(async title => {
    const sceneEnvelope = await fetch("/api/memory/starfield/v2?mode=TIME", { credentials: "include" }).then(response => response.json());
    const card = sceneEnvelope.data.stars[0];
    let response: Response | null = null;
    for (let attempt = 0; attempt < 2; attempt++) {
      const csrfEnvelope = await fetch(`/api/auth/csrf?fresh=${Date.now()}-${attempt}`, { credentials: "include", cache: "no-store" }).then(result => result.json());
      response = await fetch("/api/memory/operations", {
        method: "POST", credentials: "include", headers: {
          "Content-Type": "application/json", [csrfEnvelope.data.headerName]: csrfEnvelope.data.token
        }, body: JSON.stringify({ operationType: "UPDATE", primaryMemoryId: card.id,
          title: `${title}（正在校准）`, summary: card.summary ?? "一次可撤回的校准" })
      });
      if (response.ok || response.status !== 403) break;
    }
    if (!response?.ok) throw new Error(`operation setup failed: ${response?.status} ${await response?.text()}`);
  }, originalTitle);
  await page.reload();
  await loginIfNeeded(page);
  const history = page.getByLabel("记忆变更历史");
  await expect(history).toContainText("UPDATE");
  await history.getByRole("button", { name: "撤回这次变更" }).first().click();
  await expect(page.getByRole("status")).toContainText("已撤回这次UPDATE");
  await expect(page.getByRole("list", { name: "记忆星空可访问列表" }).getByText(originalTitle, { exact: true })).toBeVisible();
  await expect(history).toContainText("已撤回");
});

test("owner publishes, a visitor sends a slow letter, then withdrawal stops the resonance", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const resonance = page.getByRole("region", { name: "共鸣体创建与像不像我沙盒" });
  await expect(resonance.getByRole("heading", { name: "先确认像不像你，再让别人遇见" })).toBeVisible();
  const newCapsule = resonance.getByRole("tab", { name: /新建一个侧面/ });
  if (await newCapsule.isVisible().catch(() => false)) await newCapsule.click();

  const eligibleMemory = resonance.locator(".memory-consent-list label:not(.blocked) input").first();
  if (await eligibleMemory.isVisible().catch(() => false)) await eligibleMemory.check();
  const capsuleName = `盲体验回声-${Date.now()}`;
  await resonance.getByLabel("共鸣体名字").fill(capsuleName);
  await resonance.getByLabel("希望它保留的侧面").fill("遇到误解时先停一下，再温和但清楚地说出边界。");
  await resonance.getByRole("button", { name: "先看严格脱敏预览" }).click();
  await expect(resonance.getByLabel("共鸣体授权预览")).toContainText("WHAT IT MAY USE");
  await resonance.getByRole("button", { name: "编译为私密版本" }).click();

  await expect(resonance.locator(".genome-badge")).toContainText("v1");
  await expect(resonance).toContainText("仅自己可见");
  await resonance.getByRole("button", { name: "看看它会怎么说" }).click();
  const sandbox = resonance.locator(".sandbox-response");
  await expect(sandbox).toContainText("不会发送给其他人");
  await expect(sandbox).toContainText(/.+/);
  await sandbox.getByRole("button", { name: "像我", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("没有暗中漂移");

  await resonance.getByRole("button", { name: "确认并发布当前版本" }).click();
  await expect(resonance).toContainText("公开中");
  await page.screenshot({ path: "../evidence/innovation/INNO-CAP-003/resonance-owner-journey.png", fullPage: true });

  await page.getByRole("button", { name: "安全退出" }).click();
  await expect(page.getByRole("heading", { name: "回到你的内宇宙" })).toBeVisible();
  await page.getByLabel("用户名").fill("river");
  await page.getByLabel("密码").fill("demo123");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByLabel("写给 Aurora")).toBeVisible();

  const network = page.getByRole("region", { name: "发现共鸣并写一封慢信" });
  await expect(network.getByRole("heading", { name: "不是刷卡片，是理解为什么会相遇" })).toBeVisible();
  for (const [button, explanation] of [
    ["有意义的互补", "有意义的互补"], ["成长边缘", "成长边缘"],
    ["温和偶遇", "温和偶遇"], ["阶段同行", "阶段同行"]
  ]) {
    await network.getByRole("button", { name: button, exact: true }).click();
    await expect(network.locator(".strategy-explanation")).toContainText(explanation);
  }
  await network.getByRole("button", { name: "成长边缘", exact: true }).click();
  await page.screenshot({ path: "../evidence/innovation/INNO-CAP-005/resonance-strategy-switching.png", fullPage: true });
  await network.getByRole("button", { name: "相似共鸣", exact: true }).click();
  await network.locator(".match-card").filter({ hasText: capsuleName }).click();
  await expect(network).toContainText("授权 AI 共鸣体 · 不是真人实时在线");
  await network.getByRole("button", { name: "进入有限但自然的对话" }).click();
  await network.getByLabel("写给共鸣体").fill("我也很反感模板化安慰。你会怎样判断一句回应是真的理解，而不是话术？");
  await network.getByRole("button", { name: "发送这一轮" }).click();
  await expect(network.getByLabel("共鸣体对话记录").locator("article.capsule")).toBeVisible();
  await network.getByLabel("慢信正文").fill("你关于真实理解的表达让我停了一下。我不急着认识完整的你，只想先把这份共鸣认真地交给你。 ");
  await network.getByRole("button", { name: "让慢信启程" }).click();
  await expect(network.getByRole("status")).toContainText("慢信已启程");
  await page.screenshot({ path: "../evidence/innovation/INNO-CAP-004/resonance-visitor-letter-journey.png", fullPage: true });

  await page.getByRole("button", { name: "安全退出" }).click();
  await page.getByLabel("用户名").fill("demo");
  await page.getByLabel("密码").fill("demo123");
  await page.getByRole("button", { name: "登录" }).click();
  const inbox = page.getByRole("region", { name: "抵达我的慢信" });
  await expect(inbox.getByRole("heading", { name: "只在抵达之后，才由你决定关系往哪里走" })).toBeVisible();
  await page.screenshot({ path: "../evidence/innovation/INNO-CAP-006/owner-letter-inbox.png", fullPage: true });
  const ownerSpace = page.getByRole("region", { name: "共鸣体创建与像不像我沙盒" });
  await ownerSpace.getByRole("tab", { name: new RegExp(capsuleName) }).click();
  await ownerSpace.getByRole("button", { name: "撤回这个共鸣体" }).click();
  await expect(page.getByRole("status")).toContainText("已撤回");

  const consent = inbox.getByLabel("双向连接同意");
  const existingLeave = consent.getByRole("button", { name: "退出连接" }).first();
  if (await existingLeave.isVisible().catch(() => false)) await existingLeave.click();
  const arrivedFromRiver = inbox.locator(".inbox-list article").filter({ hasText: "你写的黄昏让我停了一下" });
  const markRead = arrivedFromRiver.getByRole("button", { name: "标记已读" });
  if (await markRead.isVisible().catch(() => false)) await markRead.click();
  await arrivedFromRiver.getByLabel("回复「你写的黄昏让我停了一下」").fill("我愿意认真回你：真实理解不是一句确认，而是愿意让对方纠正，并在之后真的改变。 ");
  await arrivedFromRiver.getByRole("button", { name: "让回复慢信启程" }).click();
  await expect(page.getByRole("status")).toContainText("回复慢信已启程");
  await arrivedFromRiver.getByRole("button", { name: "愿意认识对方" }).click();
  await expect(consent).toContainText("尚未同意，不会提前开放真人连接");

  await page.getByRole("button", { name: "安全退出" }).click();
  await page.getByLabel("用户名").fill("river");
  await page.getByLabel("密码").fill("demo123");
  await page.getByRole("button", { name: "登录" }).click();
  const riverConsent = page.getByLabel("双向连接同意");
  await riverConsent.getByRole("button", { name: "我也愿意" }).click();
  await expect(riverConsent).toContainText("双方已同意");
  await riverConsent.screenshot({ path: "../evidence/innovation/INNO-CAP-007/mutual-connection-consent.png" });
  await riverConsent.getByRole("button", { name: "退出连接" }).click();
  await expect(riverConsent).toContainText("还没有建立真人连接");
});

test("Inner Cosmos remains operable on a narrow mobile viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/app/aurora/index.html");
  await loginIfNeeded(page);
  const cosmos = page.getByRole("region", { name: "记忆星空" });
  await expect(cosmos).toBeVisible();
  await cosmos.getByRole("button", { name: "人物" }).click();
  await cosmos.getByRole("list", { name: "记忆星空可访问列表" }).locator("li").first()
    .getByRole("button", { name: "查看来源与变化" }).click();
  await expect(cosmos.getByLabel("记忆来源与变化")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
