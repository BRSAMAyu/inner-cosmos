import { chromium } from "@playwright/test";
const browser = await chromium.connectOverCDP("http://127.0.0.1:9222");
const ctx = browser.contexts()[0] ?? await browser.newContext();
const pages = ctx.pages();
const page = pages.find(p => /localhost:5173/.test(p.url())) ?? pages[0] ?? await ctx.newPage();
const logs = []; const errors = []; const net = [];
page.on("console", m => logs.push(`${m.type()}: ${m.text().slice(0,200)}`));
page.on("pageerror", e => errors.push(String(e).slice(0,300)));
page.on("request", r => { if (/\/api\//.test(r.url())) net.push(`${r.method()} ${r.url()}`); });
await page.waitForTimeout(1500);
const probe = await page.evaluate(() => ({
  url: location.href, title: document.title,
  tauri: "__TAURI_INTERNALS__" in window,
  bodyText: (document.body?.innerText ?? "").slice(0, 700),
  readyState: document.readyState,
}));
console.log("=== DESKTOP SHELL (Tauri WebView2 via CDP) ===");
console.log(JSON.stringify(probe, null, 2));
console.log("=== /api calls observed from webview (last 1.5s) ===");
console.log(net.length ? [...new Set(net)].slice(0,12).join("\n") : "(none in this window)");
console.log("=== pageerrors ===");
console.log(errors.length ? errors.join("\n") : "(none)");
await page.screenshot({ path: "../evidence/innovation/INNO-MOBILE-007/screenshots/tauri-webview-boot.png" }).catch(e => console.log("ss-fail:", e.message));
await browser.close();
