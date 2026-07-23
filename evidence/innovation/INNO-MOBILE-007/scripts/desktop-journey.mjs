// Drives the REAL Inner Cosmos frontend (the committed web bundle served by the 8082 Mock backend)
// through the register -> Aurora streamed-multi-bubble-chat journey. This is the exact React source
// the Tauri WebView2 runs; in a browser `mobileState.native` is false, so the mobile-env safety gate
// is skipped and session-cookie auth works, letting us drive a full logged-in journey without the
// OIDC IdP the native PKCE path requires (see INNO-MOBILE-004 for that PKCE proof).
import { chromium } from "@playwright/test";
const BASE = "http://localhost:8082";
const SHOT = "../evidence/innovation/INNO-MOBILE-007/screenshots";
const u = `web_${Date.now()}`;
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: "zh-CN", viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
const sse = [];
page.on("request", r => { if (/\/api\/aurora\/stream/.test(r.url())) sse.push("stream-open " + r.url()); });
page.on("response", r => { if (/\/api\/aurora\/stream/.test(r.url())) sse.push("stream-resp " + r.status()); });
const errs = []; page.on("pageerror", e => errs.push(String(e).slice(0,200)));
console.log("1) load app");
await page.goto(`${BASE}/app/aurora/index.html`, { waitUntil: "domcontentloaded" });
await page.getByRole("tab", { name: "注册" }).click();
console.log("2) register", u);
await page.getByLabel("用户名", { exact: true }).fill(u);
await page.getByLabel(/^密码/).fill("drive-pass-001");
await page.getByLabel("确认密码").fill("drive-pass-001");
await page.getByRole("button", { name: "创建账号" }).click();
await page.getByLabel("写给 Aurora").waitFor({ timeout: 20000 });
console.log("3) logged in -> composer visible");
const msg = "明天的汇报让我害怕，我想先被接住，之后再拆第一步。";
await page.getByLabel("写给 Aurora").fill(msg);
await page.getByRole("button", { name: "发送" }).click();
// wait for at least one aurora reply bubble with text
await page.waitForFunction(() => {
  const arts = document.querySelectorAll("article.aurora");
  return Array.from(arts).some(a => (a.innerText ?? "").trim().length > 2);
}, { timeout: 30000 });
const bubbles = await page.$$eval("article.aurora", els => els.map(e => (e.innerText ?? "").trim().slice(0,80)));
console.log("4) aurora bubbles visible:", bubbles.length);
bubbles.forEach((b,i) => console.log(`   bubble[${i}]: ${b}`));
await page.waitForTimeout(2500); // let streaming settle
await page.screenshot({ path: `${SHOT}/web-aurora-chat.png`, fullPage: false });
const bubbles2 = await page.$$eval("article.aurora", els => els.map(e => (e.innerText ?? "").trim().slice(0,90)));
console.log("5) final bubble count:", bubbles2.length);
bubbles2.forEach((b,i) => console.log(`   final[${i}]: ${b}`));
console.log("=== SSE stream events observed ===");
console.log(sse.length ? sse.join("\n") : "(none)");
console.log("=== pageerrors ===");
console.log(errs.length ? errs.join("\n") : "(none)");
await browser.close();
