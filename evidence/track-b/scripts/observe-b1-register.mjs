// B1 observation script: live-verify the new in-app register flow added to /app/aurora/'s
// AuthGate (login/register mode toggle on the same screen). Complements, does not replace,
// evidence/track-b/scripts/observe-b0-journeys.mjs.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b1register_${Date.now()}`;
const password = "Obs3rver!2026";

const desktop = { width: 1440, height: 900 };
const mobile = { width: 390, height: 844 };

const findings = [];
function log(msg) { findings.push(msg); console.log(msg); }

async function shot(page, name) {
  await page.screenshot({ path: path.join(OUT, name), fullPage: true });
  log(`screenshot: ${name}`);
}

async function goto(page, url) {
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
}

(async () => {
  const browser = await chromium.launch();

  // 1. Fresh, cookie-less visit to /app/aurora/ -- the only documented entry URL.
  const context = await browser.newContext({ viewport: desktop });
  const page = await context.newPage();
  page.on("console", msg => { if (msg.type() === "error") log(`console.error: ${msg.text()}`); });
  page.on("pageerror", err => log(`pageerror: ${err.message}`));

  await goto(page, `${BASE}/app/aurora/`);
  await page.waitForTimeout(1500);
  await shot(page, "b1-01-entry-login-desktop.png");
  const hasTabs = await page.$('[role="tablist"]');
  log(`login/register tablist present on first visit: ${Boolean(hasTabs)}`);
  const hasRegisterTab = await page.getByRole("tab", { name: "注册" }).count();
  log(`"注册" tab present: ${hasRegisterTab > 0}`);

  // 2. Switch to register mode.
  await page.getByRole("tab", { name: "注册" }).click();
  await page.waitForTimeout(300);
  await shot(page, "b1-02-entry-register-empty-desktop.png");

  // 3. Password mismatch -- inline error, no navigation away.
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码").fill("somethingElse!123");
  await page.getByRole("button", { name: "创建账号" }).click();
  await page.waitForTimeout(300);
  await shot(page, "b1-03-register-password-mismatch-desktop.png");
  const mismatchAlert = await page.getByRole("alert").innerText().catch(() => "");
  log(`mismatch alert text: ${mismatchAlert}`);

  // 4. Fix the confirmation, fill nickname, submit for real.
  await page.getByLabel("确认密码").fill(password);
  await page.getByLabel(/昵称/).fill("B1 注册观察");
  await shot(page, "b1-04-register-filled-desktop.png");
  await page.getByRole("button", { name: "创建账号" }).click();
  await page.waitForTimeout(2500);
  await shot(page, "b1-05-post-register-authenticated-desktop.png");
  log(`url after register submit: ${page.url()}`);
  const stillHasLoginForm = await page.$("main.login-shell form");
  log(`login/register form still present after submit (should be false): ${Boolean(stillHasLoginForm)}`);
  const nav = await page.$(".app-shell-nav");
  log(`app shell navigation present (authenticated landed in AppShell): ${Boolean(nav)}`);

  // 5. Log out, then log back in with the same credentials to confirm the login half of the
  // same toggle still works end to end after this change.
  const logoutBtn = await page.$("footer button");
  if (logoutBtn) { await logoutBtn.click(); await page.waitForTimeout(1500); }
  await shot(page, "b1-06-after-logout-desktop.png");
  const loginTabAfterLogout = await page.getByRole("tab", { name: "登录" }).count();
  log(`"登录" tab present after logout: ${loginTabAfterLogout > 0}`);
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForTimeout(2000);
  const navAfterRelogin = await page.$(".app-shell-nav");
  log(`re-login via the same toggle screen succeeded: ${Boolean(navAfterRelogin)}`);
  await shot(page, "b1-07-relogin-authenticated-desktop.png");

  await context.close();

  // 6. Expired/cleared-session deep link now also gets the register affordance (fix once,
  // at the shared entry point, per the task brief).
  const ectx = await browser.newContext({ viewport: desktop });
  const epage = await ectx.newPage();
  await goto(epage, `${BASE}/app/aurora/?space=cosmos`);
  await epage.waitForTimeout(1200);
  await shot(epage, "b1-08-expired-auth-with-register-desktop.png");
  const expiredHasRegisterTab = await epage.getByRole("tab", { name: "注册" }).count();
  log(`expired/cleared-session deep-link screen also offers the register tab: ${expiredHasRegisterTab > 0}`);
  await ectx.close();

  // 7. Mobile viewport of the unified entry surface.
  const mctx = await browser.newContext({ viewport: mobile, isMobile: true, hasTouch: true });
  const mpage = await mctx.newPage();
  await goto(mpage, `${BASE}/app/aurora/`);
  await mpage.waitForTimeout(1200);
  await shot(mpage, "b1-09-entry-login-mobile.png");
  await mpage.getByRole("tab", { name: "注册" }).click();
  await mpage.waitForTimeout(300);
  await shot(mpage, "b1-10-entry-register-mobile.png");
  await mctx.close();

  await browser.close();

  fs.writeFileSync(path.join(OUT, "observation-log-b1-register.txt"), findings.join("\n"));
  console.log("DONE");
})().catch(err => {
  console.error("SCRIPT ERROR", err);
  fs.writeFileSync(path.join(OUT, "observation-log-b1-register-error.txt"), String(err && err.stack || err));
  process.exit(1);
});
