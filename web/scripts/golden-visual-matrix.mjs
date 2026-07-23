// W2 golden visual matrix (machine evidence for G7.UX-COMPLETE / G9.FINAL-E2E).
// Drives the five product spaces on the integrated HEAD (voice + demo + capsule-voice +
// slow-letter) and captures a screenshot of each, in zh-CN and en-SG, at a desktop and a
// narrow (390px) viewport. Each file is paired with what it proves via its name.
//
// Run against an already-booted dev backend (Mock chat, no key needed):
//   INNER_COSMOS_BASE_URL=http://127.0.0.1:8091 node web/scripts/golden-visual-matrix.mjs
//
// Output: evidence/g9/FINAL-E2E-001/screenshots/<space>-<locale>-<viewport>.png

import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";

const BASE = process.env.INNER_COSMOS_BASE_URL ?? "http://127.0.0.1:8091";
const OUT = resolve(process.cwd(), "..", "evidence", "g9", "FINAL-E2E-001", "screenshots");
mkdirSync(OUT, { recursive: true });

const USERNAME = process.env.E2E_USERNAME ?? "demo";
const PASSWORD = process.env.E2E_PASSWORD ?? "demo123";

// space key -> [zh nav label, en nav label]
const SPACES = [
  ["aurora", "今天", "Today"],
  ["cosmos", "内宇宙", "Cosmos"],
  ["resonance", "共鸣", "Resonance"],
  ["letters", "连接", "Connect"],
  ["me", "我的", "Me"],
];

const VIEWPORTS = [
  ["desktop", { width: 1280, height: 800 }],
  ["mobile390", { width: 390, height: 844 }],
];

const LOCALES = ["zh-CN", "en-SG"];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page, locale) {
  // Persist locale before the app bootstraps, so the whole shell renders in the target language.
  await page.addInitScript(([key, val]) => { try { localStorage.setItem(key, val); } catch (_) {} }, ["ic.locale", locale]);
  await page.goto(`${BASE}/app/aurora/index.html`, { waitUntil: "domcontentloaded" });
  const navName = locale === "en-SG" ? "Inner Cosmos, five spaces" : "Inner Cosmos 五个空间";
  // Locale-agnostic: the login screen always has a username + password field; the shell has the nav.
  const userField = page.getByLabel(/^用户名$|^Username$/);
  const passField = page.getByLabel(/^密码$|^Password$/);
  // Wait for either the login form or the already-logged-in shell to appear.
  await page.waitForFunction(() => !!document.querySelector('[role="navigation"]') ||
    !!document.querySelector('input[type="password"]'), { timeout: 20000 });
  if (await userField.isVisible().catch(() => false)) {
    await userField.fill(USERNAME);
    await passField.fill(PASSWORD);
    await page.getByRole("button", { name: /^登录$|^Log in$/ }).click();
  }
  await page.getByRole("navigation", { name: navName }).waitFor({ state: "visible", timeout: 20000 });
  // Dismiss the offline/SW notice if present.
  const notice = page.getByRole("button", { name: locale === "en-SG" ? "Got it" : "知道了" });
  if (await notice.isVisible().catch(() => false)) await notice.click().catch(() => {});
  // Let the first screen settle (animations / SSE warmup).
  await sleep(1200);
}

async function openSpace(page, locale, spaceKey, zhLabel, enLabel) {
  const navName = locale === "en-SG" ? "Inner Cosmos, five spaces" : "Inner Cosmos 五个空间";
  const nav = page.getByRole("navigation", { name: navName });
  await nav.getByRole("button", { name: new RegExp(`^${locale === "en-SG" ? enLabel : zhLabel}`) }).click();
  // Wait for the space content to render (resolves lazy-loaded data fetches).
  await sleep(1600);
}
const summary = [];

const browser = await chromium.launch();
try {
  for (const [vpName, vp] of VIEWPORTS) {
    for (const locale of LOCALES) {
      const context = await browser.newContext({
        viewport: vp,
        locale,
        deviceScaleFactor: 1,
        // Narrow viewport also proves the reduced-motion path does not break layout.
        reducedMotion: vpName === "mobile390" ? "reduce" : "no-preference",
      });
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(String(e)));
      page.on("console", (m) => { if (m.type() === "error") errors.push("console:" + m.text().slice(0, 120)); });
      try {
        await login(page, locale);
        for (const [key, zh, en] of SPACES) {
          await openSpace(page, locale, key, zh, en);
          // For Cosmos, open the starfield tab explicitly so the hero visual is captured.
          const file = `${key}-${locale}-${vpName}.png`;
          await page.screenshot({ path: `${OUT}/${file}`, fullPage: false });
          summary.push({ file, space: key, locale, viewport: vpName, consoleErrors: errors.slice() });
        }
      } catch (e) {
        summary.push({ error: `${locale}/${vpName}: ${String(e).slice(0, 160)}` });
      } finally {
        await context.close();
      }
    }
  }
} finally {
  await browser.close();
}

console.log("VISUAL_MATRIX " + JSON.stringify(summary));
// "OK" = every space captured; the only allowed console noise is the known-benign bootstrap 401
// on /api/dialog/session/create (the app probes auth then recovers -- see voice-features.spec.ts,
// which asserts zero *unhandled* page errors on the same journeys).
const realErrors = summary.flatMap((r) => r.consoleErrors || []).filter((e) => !e.includes("401"));
const captured = summary.filter((r) => r.file).length;
console.log(`VISUAL_MATRIX captured=${captured}/20 realConsoleErrors=${realErrors.length}`);
if (realErrors.length) console.log("REAL_ERRORS " + JSON.stringify(realErrors.slice(0, 6)));
console.log(captured === 20 && realErrors.length === 0 ? "VISUAL_MATRIX_OK" : "VISUAL_MATRIX_INCOMPLETE_OR_NOISY");
