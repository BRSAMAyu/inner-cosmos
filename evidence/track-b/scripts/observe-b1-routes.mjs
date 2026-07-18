// B1 observation script: live-verify the HashRouter migration (real, shareable per-space
// routes replacing the old `?space=` query param). Complements observe-b0-journeys.mjs and
// observe-b1-register.mjs.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b1routes_${Date.now()}`;
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
  await shot(page, "b1-routes-01-post-register.png");

  // Nav clicks should update the URL to a real hash route, not a ?space= query param.
  // Scoped to the actual nav landmark (aria-label from ProductShell.tsx) so a same-substring
  // button elsewhere in the (simultaneously-mounted, hidden-toggled) DOM can't be matched
  // instead -- exact labels per productSpaces in ProductShell.tsx.
  const nav = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  for (const [label, expected] of [["共鸣", "#/resonance"], ["内宇宙", "#/cosmos"], ["连接", "#/connections/letters"], ["我的", "#/me"], ["今天", "#/aurora"]]) {
    const btn = nav.getByRole("button", { name: label, exact: false });
    if (await btn.count() === 0) { log(`nav button not found: ${label}`); continue; }
    await btn.click();
    await page.waitForTimeout(400);
    const url = page.url();
    log(`nav "${label}" -> ${url} (expected to contain ${expected}): ${url.includes(expected)}`);
  }
  await shot(page, "b1-routes-02-after-nav-clicks.png");

  // Browser back should move between spaces, not reload/break the app.
  await page.goBack();
  await page.waitForTimeout(400);
  log(`after goBack(): ${page.url()}`);
  await shot(page, "b1-routes-03-after-back.png");

  // Direct hash-route deep link (simulating a shared/bookmarked link) should land on that
  // space without needing a click-through.
  await page.goto(`${BASE}/app/aurora/#/resonance`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const resonanceHeading = await page.locator("text=PEOPLE, SLOWLY").count() + await page.locator("text=共鸣").count();
  log(`direct deep link to #/resonance rendered resonance-ish content: ${resonanceHeading > 0}`);
  await shot(page, "b1-routes-04-direct-deep-link-resonance.png");

  // Legacy ?space= link (pre-routing bookmark) should redirect to the real route, not 404
  // or silently stay put.
  await page.goto(`${BASE}/app/aurora/?space=letters`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  log(`legacy ?space=letters redirected to: ${page.url()}`);
  await shot(page, "b1-routes-05-legacy-query-redirect.png");

  fs.writeFileSync(path.join(OUT, "observation-log-b1-routes.txt"), findings.join("\n"));
  await browser.close();
})().catch(err => {
  fs.writeFileSync(path.join(OUT, "observation-log-b1-routes-error.txt"), String(err && err.stack || err));
  console.error(err);
  process.exit(1);
});
