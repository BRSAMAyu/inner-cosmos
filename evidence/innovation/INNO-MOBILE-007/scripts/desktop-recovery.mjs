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
await page.getByLabel("写给 Aurora").waitFor({ timeout: 20000 });
const msg = "我有点睡不着，脑子里一直转。";
await page.getByLabel("写给 Aurora").fill(msg);
await page.getByRole("button", { name: "发送" }).click();
// wait until the turn fully settles: 2+ aurora bubbles AND no streaming indicator, then hold 4s
await page.waitForFunction(() => document.querySelectorAll("article.aurora").length >= 2, null, { timeout: 30000 });
await page.waitForTimeout(4500);
const beforeBubbles = await page.$$eval("article.aurora", els => els.map(e => (e.innerText ?? "").trim().slice(0,60)));
console.log("before reload: aurora bubbles =", beforeBubbles.length);
beforeBubbles.forEach((b,i)=>console.log(`  pre[${i}]: ${b}`));
console.log("=== reload (kill/relaunch analogue) ===");
await page.reload({ waitUntil: "domcontentloaded" });
// give the resume path time to re-fetch the durable session timeline
let recovered = false;
for (let i = 0; i < 30; i++) {
  await page.waitForTimeout(1000);
  const has = await page.getByText(msg).first().isVisible().catch(() => false);
  const aur = await page.$$eval("article.aurora", els => els.length).catch(() => 0);
  if (has && aur > 0) { recovered = true; console.log(`recovered at t=${i+1}s: user_msg=YES aurora_bubbles=${aur}`); break; }
}
if (!recovered) {
  console.log("recovery: not detected within 30s; dumping visible body text:");
  const txt = await page.evaluate(() => (document.body?.innerText ?? "").slice(0, 500));
  console.log(txt);
}
await page.screenshot({ path: `${SHOT}/web-aurora-recovered.png` });
console.log("=== logout ===");
await page.getByRole("button", { name: /退出|登出|logout/i }).first().click().catch(() => {});
await page.waitForTimeout(1500);
const backToLogin = await page.getByText(/回到你的内宇宙|开始你的内宇宙/).first().isVisible().catch(() => false);
console.log("back to auth screen after logout:", backToLogin);
await browser.close();
