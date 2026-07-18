// B5-pwa-mobile (first slice): live-verify the PWA offline app shell.
// Complements observe-b1-routes.mjs's pattern. Must be run from a temporary copy placed
// under web/ (see evidence/track-b/README.md "Node ESM resolver" note) so Node can resolve
// web/node_modules/playwright regardless of process cwd.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const username = `b1pwa_${Date.now()}`;
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

  const apiRequestsDuringOfflineReload = [];
  page.on("request", req => {
    if (req.url().includes("/api/")) apiRequestsDuringOfflineReload.push({ phase: "unset", url: req.url() });
  });

  // 1) First visit, fully online -- register a fresh account, let the app boot, and give
  // the service worker time to install + precache the app shell.
  await page.goto(`${BASE}/app/aurora/`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.getByRole("tab", { name: "注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();
  await page.waitForTimeout(1500);
  log(`url after register: ${page.url()}`);
  await shot(page, "b1-pwa-offline-01-online-authenticated.png");

  const swState = await page.evaluate(async () => {
    if (!("serviceWorker" in navigator)) return "unsupported";
    const reg = await navigator.serviceWorker.ready.catch(() => null);
    return reg ? (reg.active ? reg.active.state : "no-active-worker") : "not-ready";
  });
  log(`service worker state after first load: ${swState}`);

  // Give the SW a moment past "activated" to finish precaching (install event awaits
  // cache.addAll before activation in workbox's generateSW output, but leave slack).
  await page.waitForTimeout(1500);

  const cachedUrls = await page.evaluate(async () => {
    const keys = await caches.keys();
    const all = [];
    for (const key of keys) {
      const cache = await caches.open(key);
      const reqs = await cache.keys();
      all.push(...reqs.map(r => r.url));
    }
    return all;
  });
  log(`cache entries after online load: ${cachedUrls.length}`);
  log(`sample cached urls: ${JSON.stringify(cachedUrls.slice(0, 8))}`);
  const cachedApiUrls = cachedUrls.filter(u => u.includes("/api/"));
  log(`cached /api/ urls (must be 0): ${cachedApiUrls.length} ${JSON.stringify(cachedApiUrls)}`);

  // 2) Reload once more online -- confirms adding the service worker introduces no
  // regression to the normal (non-offline) experience.
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1000);
  const onlineReloadBodyLen = (await page.locator("body").innerText()).length;
  log(`online reload body text length (no regression check): ${onlineReloadBodyLen}`);
  await shot(page, "b1-pwa-offline-02-online-reload-no-regression.png");

  // 3) Force the browser context offline (matching how B0 originally forced it) and reload.
  await context.setOffline(true);
  page.on("request", req => {
    if (req.url().includes("/api/")) apiRequestsDuringOfflineReload.push({ phase: "offline-reload", url: req.url() });
  });
  const responsesFromSW = [];
  page.on("response", res => {
    if (res.url().includes("/api/")) {
      responsesFromSW.push({ url: res.url(), fromServiceWorker: res.fromServiceWorker(), status: res.status() });
    }
  });
  let reloadError = null;
  try {
    await page.reload({ waitUntil: "domcontentloaded", timeout: 15000 });
  } catch (err) {
    reloadError = String(err);
  }
  log(`offline reload error (expected: null, navigateFallback should still resolve): ${reloadError}`);
  await page.waitForTimeout(2500);
  await shot(page, "b1-pwa-offline-03-offline-reload.png");

  const bodyText = await page.locator("body").innerText().catch(() => "");
  log(`offline reload body text length: ${bodyText.length}`);
  log(`offline reload body text (first 300 chars): ${JSON.stringify(bodyText.slice(0, 300))}`);
  const isBlank = bodyText.trim().length === 0;
  log(`offline reload page is BLANK (must be false): ${isBlank}`);
  const showsConnectError = bodyText.includes("没能连上你的内宇宙") || bodyText.includes("重试");
  log(`offline reload shows the branded connect-error state (ConnectError): ${showsConnectError}`);

  log(`/api/ requests observed during offline reload: ${JSON.stringify(apiRequestsDuringOfflineReload.filter(r => r.phase === "offline-reload"))}`);
  log(`/api/ responses served FROM the service worker cache during offline reload (must be 0): ${JSON.stringify(responsesFromSW.filter(r => r.fromServiceWorker))}`);

  // 4) Back online -- confirm recovery (the retry button / a fresh reload should work again).
  await context.setOffline(false);
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1000);
  const recoveredBodyLen = (await page.locator("body").innerText()).length;
  log(`back-online reload body text length (recovery check): ${recoveredBodyLen}`);
  await shot(page, "b1-pwa-offline-04-back-online-recovered.png");

  fs.writeFileSync(path.join(OUT, "observation-log-b1-pwa-offline.txt"), findings.join("\n"));
  await browser.close();
})().catch(err => {
  fs.writeFileSync(path.join(OUT, "observation-log-b1-pwa-offline-error.txt"), String(err && err.stack || err));
  console.error(err);
  process.exit(1);
});
