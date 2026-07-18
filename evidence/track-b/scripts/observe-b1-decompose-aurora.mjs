// B1 domain-hook decomposition (first slice, useAuroraSession) live-verification script.
// Confirms the extraction of Aurora conversation/session state+logic out of AuroraApp.tsx into
// web/src/hooks/useAuroraSession.ts did not change any visible behavior: register/login, mode
// picker, multi-message streaming, mid-stream interrupt, and WakeIntent negotiate all still work
// against the real running app (dev profile, H2 + Mock AI provider).
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b1decompose_${Date.now()}`;
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

  await page.goto(`${BASE}/app/aurora/`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.getByRole("tab", { name: "注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();
  await page.waitForTimeout(1500);
  log(`url after register: ${page.url()}`);
  await shot(page, "b1-decompose-01-post-register.png");

  // Mode picker: switching modes should toggle the "active" class (setMode from the hook).
  const modesNav = page.locator("nav.modes");
  await modesNav.getByRole("button", { name: "整理" }).click();
  await page.waitForTimeout(200);
  const clarifyActive = await modesNav.getByRole("button", { name: "整理" }).getAttribute("class");
  log(`mode picker "整理" active class after click: ${clarifyActive}`);
  await modesNav.getByRole("button", { name: "倾诉" }).click();
  await page.waitForTimeout(200);

  // Send a message and observe multi-message streaming (Mock provider).
  const textarea = page.getByLabel("写给 Aurora");
  await textarea.fill("今天有点累，但还好。");
  await page.getByRole("button", { name: "发送", exact: true }).click();
  await page.waitForTimeout(600);
  await shot(page, "b1-decompose-02-mid-stream.png");
  const stopButton = page.getByRole("button", { name: "停止回应" });
  const stopVisible = await stopButton.count() > 0 && await stopButton.isVisible().catch(() => false);
  log(`stop/interrupt control visible mid-stream: ${stopVisible}`);
  if (stopVisible) {
    await stopButton.click();
    await page.waitForTimeout(400);
    log("clicked stop/interrupt mid-stream");
  }
  await shot(page, "b1-decompose-03-after-stop.png");
  const statusText = await page.locator(".global-state").innerText().catch(() => "");
  log(`status banner after stop: ${statusText}`);

  // Send another message and let it settle to completion (dual-core status signal check).
  await textarea.fill("接着刚才说的，我想理一理思路。");
  await page.getByRole("button", { name: /发送|打断并发送/ }).click();
  await page.waitForTimeout(3000);
  await shot(page, "b1-decompose-04-settled.png");
  const messageCount = await page.locator(".conversation .message").count();
  log(`message bubbles present after second turn: ${messageCount}`);

  // WakeIntent negotiate: fill the return-time input and schedule a return.
  await page.getByLabel("回来时间").fill("明天早上 9:00");
  await page.getByRole("button", { name: "和 Aurora 约好" }).click();
  await page.waitForTimeout(1000);
  const returnCardCount = await page.locator(".return-card").count();
  log(`WakeIntent return-card count after scheduling: ${returnCardCount}`);
  await shot(page, "b1-decompose-05-wakeintent-scheduled.png");

  fs.writeFileSync(path.join(OUT, "observation-log-b1-decompose-aurora.txt"), findings.join("\n"));
  await browser.close();
})().catch(err => {
  fs.writeFileSync(path.join(OUT, "observation-log-b1-decompose-aurora-error.txt"), String(err && err.stack || err));
  console.error(err);
  process.exit(1);
});
