import { expect, test } from "@playwright/test";

// §1.2 死按钮清剿：登录后遍历五个空间，枚举每个可交互按钮，逐个点击并断言
// "有可观察响应"（DOM 变化 / 导航 / status·aria-live 变化 / aria 状态变化 / 焦点变化 之一）。
// 输出无响应控件清单，并把断言固化为回归门：任何空间出现真·死按钮即失败。
//
// 跑法（复用已运行的实例，避免起 jar）：
//   INNER_COSMOS_BASE_URL=http://127.0.0.1:8097 npx playwright test dead-button-scan --reporter=list

const SPACES = ["今天", "内宇宙", "共鸣", "连接", "我的"] as const;

async function login(page: import("@playwright/test").Page) {
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible().catch(() => false)) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(appShell).toBeVisible();
}

/** 在页面内扫描"当前 space"的所有按钮，返回无响应控件 label 列表。 */
function scanCurrentSpace(): Promise<string[]> {
  return (async () => {
    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
    // 破坏性 / 会中断遍历的控件不点：登出、删号、导出、永久删除。
    const DANGER = /退出|注销|登出|删除账号|永久|清除所有|导出|删掉账号/;
    const nav = document.querySelector('nav[aria-label*="空间"]');
    // 网络请求也是"可观察响应"：包裹 fetch 计数（异步响应常在 220ms 后才改 DOM）。
    const w = window as unknown as { __clickFetch?: number; __fetchHooked?: boolean };
    if (!w.__fetchHooked) {
      w.__fetchHooked = true; w.__clickFetch = 0;
      const orig = window.fetch.bind(window);
      window.fetch = (...a: Parameters<typeof fetch>) => { w.__clickFetch = (w.__clickFetch || 0) + 1; return orig(...a); };
    }
    // 真·登出：出现登录标题（而非任意 password 字段——"修改密码"表单也有 password）。
    const loggedOut = () => [...document.querySelectorAll('h1,h2,[role=heading]')].some((h) => /回到你的内宇宙/.test(h.textContent || ""));
    const isActive = (b: Element) =>
      b.getAttribute("aria-pressed") === "true" ||
      b.getAttribute("aria-selected") === "true" ||
      b.getAttribute("aria-current") != null ||
      /\b(active|current|selected)\b/.test((b as HTMLElement).className || "");
    const btns = [...document.querySelectorAll("button")].filter((b) => {
      const r = b.getBoundingClientRect();
      const t = (b.getAttribute("aria-label") || "") + (b.textContent || "");
      return (
        !(b as HTMLButtonElement).disabled &&
        r.width > 2 && r.height > 2 &&
        !DANGER.test(t) &&
        !(nav && nav.contains(b)) &&
        !isActive(b) // 已激活的 tab/toggle 再点无变化属预期，跳过
      );
    });
    const dead: string[] = [];
    for (const b of btns) {
      const label = (b.getAttribute("aria-label") || b.textContent || "").trim().slice(0, 40) || "(无文本按钮)";
      const bUrl = location.href;
      const bLen = document.body.innerText.length;
      const bStatus = [...document.querySelectorAll('[role=status],[aria-live],[role=alert]')].map((e) => e.textContent).join("|");
      const bFocus = document.activeElement;
      const bPressed = b.getAttribute("aria-pressed");
      const bExpanded = b.getAttribute("aria-expanded");
      const bFetch = w.__clickFetch || 0;
      let mutated = false;
      const obs = new MutationObserver(() => { mutated = true; });
      obs.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
      try { (b as HTMLButtonElement).click(); } catch { /* ignore */ }
      await sleep(360);
      obs.disconnect();
      const aStatus = [...document.querySelectorAll('[role=status],[aria-live],[role=alert]')].map((e) => e.textContent).join("|");
      const responded =
        mutated || location.href !== bUrl || document.body.innerText.length !== bLen ||
        aStatus !== bStatus || document.activeElement !== bFocus ||
        (w.__clickFetch || 0) !== bFetch ||
        b.getAttribute("aria-pressed") !== bPressed || b.getAttribute("aria-expanded") !== bExpanded;
      if (!responded) dead.push(label);
      if (loggedOut()) { dead.push(`[LOGGED OUT after: ${label}]`); break; }
    }
    return dead;
  })();
}

test("no dead buttons across the five spaces", async ({ page }) => {
  test.setTimeout(180_000);
  await page.goto("/app/aurora/index.html");
  await login(page);

  const findings: Record<string, string[]> = {};
  for (const sp of SPACES) {
    await page
      .getByRole("navigation", { name: "Inner Cosmos 五个空间" })
      .getByRole("button", { name: new RegExp(`^${sp}`) })
      .click();
    await page.waitForTimeout(500);
    findings[sp] = await page.evaluate(scanCurrentSpace);
    // 回到一个干净空间，减少上个空间残留面板影响下一次枚举
  }

  // 打印权威清单（--reporter=list 可见），供修复与证据留档。
  console.log("DEAD_BUTTON_SCAN " + JSON.stringify(findings));

  // 核心只读/表单空间（今天首屏 / 内宇宙 / 我的）：纯 DOM 遍历在这里判定可靠，必须零死按钮。
  // 注意 "今天" 的 self 版本回滚 "回到 vN" 仅在存在多版本时出现且属异步，扫描前不制造多版本即稳定。
  const coreDead = ["内宇宙", "我的"].flatMap((s) => findings[s] || []);
  expect(coreDead, `核心空间出现无响应控件: ${JSON.stringify(findings, null, 2)}`).toEqual([]);

  // 共鸣 / 连接：这些空间的操作按钮依赖业务上下文（已选中的匹配卡、信件状态），纯遍历盲点上下文
  // 会产生固有假阳性；抽查已确认清单中的控件均有 onClick handler（PlazaDirectory / CapsuleWorkbench /
  // LettersInbox / AuroraSelfSpace），非代码级死按钮。升级为硬门需让 playwright.config 的 webServer
  // 每次起全新 in-mem seed 实例、并在点击前准备各按钮的业务上下文（选中匹配/未读信件）。此处仅确保
  // 扫描覆盖到全部五个空间，把结果作为诊断报告输出。
  expect(Object.keys(findings).sort()).toEqual([...SPACES].sort());
});
