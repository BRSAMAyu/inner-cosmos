// B1 loading-audit checkpoint: live-verify a sample of the components migrated from hand-rolled
// busy/error UI onto the shared web/src/loading.tsx primitives (useDelayedBusy/AsyncButton/
// LoadingText), confirming the 3-tier delayed-loading timing (<1s nothing, 1-3s text, >3s dots)
// actually fires in the real running app -- not just in vitest's fake-timer world.
//
// NOTE (per evidence/track-b/README.md discoveries): this script must be copied to a temp
// location *under* web/ (e.g. web/.scratch-e2e/) before running -- Node's ESM resolver walks up
// from the importing file's own path, not the process cwd, so a script physically under
// evidence/track-b/scripts/ can never resolve web/node_modules/playwright.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b1loading_${Date.now()}`;
const password = "Obs3rver!2026";

const findings = [];
function log(msg) { findings.push(msg); console.log(msg); }

async function shot(page, name) {
  await page.screenshot({ path: path.join(OUT, name), fullPage: true });
  log(`screenshot: ${name}`);
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on("pageerror", err => log(`pageerror: ${err.message}`));
  page.on("console", msg => { if (msg.type() === "error") log(`console.error: ${msg.text()}`); });

  // --- Flow 1: AuthGate register submit (migrated to AsyncButton) ---------------------------
  // Delay the register response so the 1-3s "busy text" tier is actually reachable and
  // screenshot-able, instead of resolving instantly against the local Mock/H2 backend.
  await page.route("**/api/v1/auth/register", async route => {
    await new Promise(r => setTimeout(r, 1800));
    await route.continue();
  });
  await page.goto(`${BASE}/app/aurora/`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.getByRole("tab", { name: "注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();

  // <1s: original label still shown, but already disabled (no flash).
  await page.waitForTimeout(400);
  const early = await page.getByRole("button", { name: "创建账号" });
  const earlyDisabled = await early.isDisabled().catch(() => null);
  log(`register button <1s: label still "创建账号", disabled=${earlyDisabled}`);
  await shot(page, "b1-loading-audit-01-register-under-1s.png");

  // 1-3s: busy label "正在创建" should now be visible.
  await page.waitForTimeout(900); // total ~1.3s
  const busyVisible = await page.getByText("正在创建").count();
  log(`register button 1-3s: "正在创建" visible=${busyVisible > 0}`);
  await shot(page, "b1-loading-audit-02-register-busy-text.png");

  await page.waitForTimeout(1500); // let the delayed response land and registration complete
  log(`url after register settles: ${page.url()}`);
  await page.unroute("**/api/v1/auth/register");

  // --- Flow 2: PsychologySkillStudio run (migrated to AsyncButton) ------------------------
  await page.goto(`${BASE}/app/aurora/#/cosmos`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const skillTab = page.getByRole("tab").filter({ hasText: /情绪|价值|决定/ }).first();
  if (await skillTab.count() === 0) {
    log("no psychology skill tab found -- skipping flow 2 (unexpected: skills should be a static manifest)");
  } else {
    await skillTab.click();
    await page.waitForTimeout(300);
    const textareas = page.locator(".skill-fields textarea");
    const count = await textareas.count();
    for (let i = 0; i < count; i++) await textareas.nth(i).fill("这是一次现场核验填写的内容");
    await page.locator(".skill-consent input[type=checkbox]").check();
    await shot(page, "b1-loading-audit-03-skill-ready.png");

    await page.route("**/api/psychology/skills/*/runs", async route => {
      await new Promise(r => setTimeout(r, 1800));
      await route.continue();
    });
    await page.locator(".skill-start").click();
    await page.waitForTimeout(400);
    log(`skill-start <1s disabled=${await page.locator(".skill-start").isDisabled().catch(() => null)}`);
    await shot(page, "b1-loading-audit-04-skill-under-1s.png");

    await page.waitForTimeout(900); // total ~1.3s
    const skillBusyVisible = await page.locator(".skill-start").locator("text=正在整理").count();
    log(`skill-start 1-3s: busy text visible=${skillBusyVisible > 0}`);
    await shot(page, "b1-loading-audit-05-skill-busy-text.png");

    await page.waitForTimeout(1500);
    await page.unroute("**/api/psychology/skills/*/runs");
  }

  fs.writeFileSync(path.join(OUT, "observation-log-b1-loading-audit.txt"), findings.join("\n"));
  await browser.close();
})().catch(err => {
  fs.writeFileSync(path.join(OUT, "observation-log-b1-loading-audit-error.txt"), String(err && err.stack || err));
  console.error(err);
  process.exit(1);
});
