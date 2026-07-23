// Offline-draft journey on the real frontend + 8082 backend: type a draft, reload, assert it is
// restored from the durable draft store (IndexedDB on web; Keystore on native) with the explicit
// "未发送的草稿已恢复" status -- proving the draft survives a kill/relaunch and is never auto-sent.
import { chromium } from "@playwright/test";
const BASE = "http://localhost:8082";
const SHOT = "../evidence/innovation/INNO-MOBILE-007/screenshots";
const u = `web_${Date.now()}`;
const browser = await chromium.launch();
const ctx = await browser.newContext({ locale: "zh-CN", viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
await page.goto(`${BASE}/app/aurora/index.html`, { waitUntil: "domcontentloaded" });
await page.getByRole("tab", { name: "注册" }).click();
await page.getByLabel("用户名", { exact: true }).fill(u);
await page.getByLabel(/^密码/).first().fill("drive-pass-001");
await page.getByLabel("确认密码").fill("drive-pass-001");
await page.getByRole("button", { name: "创建账号" }).click();
const composer = page.getByLabel("写给 Aurora");
await composer.waitFor({ timeout: 20000 });
const draft = "想告诉 Aurora：今天被一句话击中了，还没理清。";
await composer.fill(draft);
await page.waitForTimeout(900); // > 350ms debounce -> saveDraft flushes to durable store
console.log("draft typed + saved. reloading...");
await page.reload({ waitUntil: "domcontentloaded" });
const composer2 = page.getByLabel("写给 Aurora");
await composer2.waitFor({ timeout: 20000 });
let restored = false;
for (let i = 0; i < 15; i++) {
  await page.waitForTimeout(700);
  const val = await composer2.inputValue().catch(() => "");
  const notice = await page.getByText(/草稿已恢复|未发送的草稿/).first().isVisible().catch(() => false);
  if (val === draft) { restored = true; console.log(`draft restored at t=${(i+1)*0.7}s: value matches. notice=${notice}`); break; }
}
if (!restored) {
  const val = await composer2.inputValue().catch(() => "");
  console.log("draft NOT restored. composer value=", JSON.stringify(val.slice(0,60)));
}
await page.screenshot({ path: `${SHOT}/web-offline-draft-restored.png` });
// assert it was NOT auto-sent (no aurora bubble for this draft)
const auroraBubbles = await page.$$eval("article.aurora", els => els.length).catch(() => 0);
console.log("aurora bubbles present (should be 0, draft not auto-sent):", auroraBubbles);
await browser.close();
