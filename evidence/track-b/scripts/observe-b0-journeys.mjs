// B0 observation script: walk the five-space AppShell at desktop and mobile widths.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b0observer_${Date.now()}`;
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
  const context = await browser.newContext({ viewport: desktop });
  const page = await context.newPage();
  page.on("console", msg => { if (msg.type() === "error") log(`console.error: ${msg.text()}`); });
  page.on("pageerror", err => log(`pageerror: ${err.message}`));

  // 1. Register via legacy static page
  await goto(page, `${BASE}/pages/register.html`);
  await page.waitForTimeout(1200);
  await shot(page, "01-register-page-desktop.png");
  const formHtml = await page.content();
  fs.writeFileSync(path.join(OUT, "01-register-page.html.txt"), formHtml.slice(0, 5000));

  const inputs = await page.$$eval("input", els => els.map(e => ({ name: e.name, id: e.id, type: e.type, placeholder: e.placeholder })));
  log(`register inputs: ${JSON.stringify(inputs)}`);

  async function fillFirst(pg, selectors, value) {
    for (const sel of selectors) {
      const el = await pg.$(sel);
      if (el) { await el.fill(value); return true; }
    }
    return false;
  }
  await fillFirst(page, ["#username", "input[name=username]", "input[placeholder*=用户]"], username);
  await fillFirst(page, ["#password", "input[name=password]", "input[type=password]"], password);
  const confirmFilled = await fillFirst(page, ["#confirmPassword", "#password2", "input[name=confirmPassword]"], password);
  log(`confirm password field filled: ${confirmFilled}`);
  const nickFilled = await fillFirst(page, ["#nickname", "input[name=nickname]", "input[placeholder*=昵称]"], "B0 Observer");
  log(`nickname field filled: ${nickFilled}`);

  await shot(page, "02-register-filled-desktop.png");

  const submitBtn = await page.$("#regBtn") ?? await page.$("button[type=submit]") ?? await page.$("form button");
  log(`register submit button found: ${Boolean(submitBtn)}`);
  if (submitBtn) {
    await submitBtn.click();
    await page.waitForTimeout(2500);
  }
  await shot(page, "03-register-result-desktop.png");
  log(`url after register submit: ${page.url()}`);
  await page.waitForTimeout(2000);
  await shot(page, "03b-register-after-redirect-desktop.png");
  log(`url after redirect wait: ${page.url()}`);

  // 2. Confirm the new AppShell (should already be here after auto-redirect)
  if (!page.url().includes("/app/aurora/")) { await goto(page, `${BASE}/app/aurora/`); }
  await page.waitForTimeout(2000);
  await shot(page, "04-aurora-shell-initial-desktop.png");
  log(`url at aurora shell: ${page.url()}`);
  const bodyText1 = await page.innerText("body").catch(() => "");
  log(`aurora shell initial text snippet: ${bodyText1.slice(0, 300).replace(/\n+/g, " | ")}`);

  const loginForm = await page.$("form.login, main.login-shell form");
  if (loginForm) {
    log("Login form present on /app/aurora/ after registration -- session was NOT carried over automatically; logging in explicitly.");
    await fillFirst(page, ["input[autocomplete=username]"], username);
    await fillFirst(page, ["input[autocomplete=current-password]"], password);
    await shot(page, "05-aurora-login-filled-desktop.png");
    const loginSubmit = await page.$("button.send[type=submit]");
    if (loginSubmit) { await loginSubmit.click(); await page.waitForTimeout(2000); }
    await shot(page, "06-aurora-post-login-desktop.png");
  } else {
    log("No login form found on /app/aurora/ -- registration session carried over automatically (cookie-based).");
  }

  // 3. Walk the five spaces at desktop width
  const spaces = ["aurora", "cosmos", "resonance", "letters", "me"];
  for (const space of spaces) {
    await goto(page, `${BASE}/app/aurora/?space=${space}`);
    await page.waitForTimeout(2200);
    await shot(page, `10-space-${space}-desktop.png`);
    const text = await page.innerText("body").catch(() => "");
    fs.writeFileSync(path.join(OUT, `10-space-${space}-desktop.text.txt`), text);
    log(`space=${space} desktop text length=${text.length}`);
  }

  // 4. Repeat at mobile width
  await context.close();
  const mctx = await browser.newContext({ viewport: mobile, isMobile: true, hasTouch: true, storageState: undefined });
  // Need to re-login for the new context since cookies are per-context
  const mpage = await mctx.newPage();
  mpage.on("pageerror", err => log(`mobile pageerror: ${err.message}`));
  await goto(mpage, `${BASE}/app/aurora/`);
  await mpage.waitForTimeout(1500);
  const mLoginForm = await mpage.$("form.login, main.login-shell form");
  if (mLoginForm) {
    await fillFirst(mpage, ["input[autocomplete=username]"], username);
    await fillFirst(mpage, ["input[autocomplete=current-password]"], password);
    await mpage.screenshot({ path: path.join(OUT, "07-aurora-login-mobile.png"), fullPage: true });
    const mLoginSubmit = await mpage.$("button.send[type=submit]");
    if (mLoginSubmit) { await mLoginSubmit.click(); await mpage.waitForTimeout(2000); }
  }
  for (const space of spaces) {
    await goto(mpage, `${BASE}/app/aurora/?space=${space}`);
    await mpage.waitForTimeout(2200);
    await mpage.screenshot({ path: path.join(OUT, `20-space-${space}-mobile.png`), fullPage: true });
    log(`screenshot: 20-space-${space}-mobile.png`);
  }

  // 5. Aurora conversation: send a message and observe multi-message/thinking states
  await mctx.close();
  const cctx = await browser.newContext({ viewport: desktop });
  const cpage = await cctx.newPage();
  await goto(cpage, `${BASE}/app/aurora/`);
  await cpage.waitForTimeout(1500);
  const cLoginForm = await cpage.$("form.login, main.login-shell form");
  if (cLoginForm) {
    await fillFirst(cpage, ["input[autocomplete=username]"], username);
    await fillFirst(cpage, ["input[autocomplete=current-password]"], password);
    const cLoginSubmit = await cpage.$("button.send[type=submit]");
    if (cLoginSubmit) { await cLoginSubmit.click(); await cpage.waitForTimeout(2000); }
  }
  await goto(cpage, `${BASE}/app/aurora/?space=aurora`);
  await cpage.waitForTimeout(1500);
  const textarea = await cpage.$(".composer textarea");
  log(`composer textarea found: ${Boolean(textarea)}`);
  if (textarea) {
    await textarea.fill("最近工作压力很大，总是睡不好，感觉自己有点撑不住了。");
    await shot(cpage, "30-aurora-composer-filled-desktop.png");
    const sendBtn = await cpage.$(".composer button.send");
    log(`send button found: ${Boolean(sendBtn)}`);
    if (sendBtn) {
      await sendBtn.click();
      await cpage.waitForTimeout(1200);
      await shot(cpage, "31-aurora-thinking-state-desktop.png");
      await cpage.waitForTimeout(4000);
      await shot(cpage, "32-aurora-mid-response-desktop.png");
      await cpage.waitForTimeout(6000);
      await shot(cpage, "33-aurora-response-settled-desktop.png");
      const convText = await cpage.innerText("body").catch(() => "");
      fs.writeFileSync(path.join(OUT, "33-aurora-conversation.text.txt"), convText);
    }
  }

  // 6. Try an offline/error observation: block network and reload
  await cctx.close();
  const octx = await browser.newContext({ viewport: desktop });
  const opage = await octx.newPage();
  await goto(opage, `${BASE}/app/aurora/`);
  await opage.waitForTimeout(1500);
  const oLoginForm = await opage.$("form.login, main.login-shell form");
  if (oLoginForm) {
    await fillFirst(opage, ["input[autocomplete=username]"], username);
    await fillFirst(opage, ["input[autocomplete=current-password]"], password);
    const oLoginSubmit = await opage.$("button.send[type=submit]");
    if (oLoginSubmit) { await oLoginSubmit.click(); await opage.waitForTimeout(2000); }
  }
  await goto(opage, `${BASE}/app/aurora/?space=aurora`);
  await opage.waitForTimeout(1000);
  await octx.setOffline(true);
  await opage.reload({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(e => log(`offline reload error: ${e.message}`));
  await opage.waitForTimeout(2000);
  await opage.screenshot({ path: path.join(OUT, "40-offline-reload-desktop.png"), fullPage: true }).catch(() => {});
  const offlineText = await opage.innerText("body").catch(() => "(failed to read body)");
  log(`offline body text snippet: ${offlineText.slice(0, 300).replace(/\n+/g, " | ")}`);
  await octx.setOffline(false);

  // 7. Expired/invalid auth observation: clear cookies then hit a space URL directly
  await octx.clearCookies();
  await goto(opage, `${BASE}/app/aurora/?space=cosmos`);
  await opage.waitForTimeout(1500);
  await opage.screenshot({ path: path.join(OUT, "41-expired-auth-desktop.png"), fullPage: true }).catch(() => {});
  const expiredText = await opage.innerText("body").catch(() => "");
  log(`expired-auth body text snippet: ${expiredText.slice(0, 300).replace(/\n+/g, " | ")}`);

  await browser.close();

  fs.writeFileSync(path.join(OUT, "observation-log.txt"), findings.join("\n"));
  console.log("DONE");
})().catch(err => {
  console.error("SCRIPT ERROR", err);
  fs.writeFileSync(path.join(OUT, "observation-log-error.txt"), String(err && err.stack || err));
  process.exit(1);
});
