// B1 domain-hook decomposition (second slice, useConnectionsAndLetters) live-verification script.
// Confirms extracting the connections/letters product space's state+logic out of AuroraApp.tsx into
// web/src/hooks/useConnectionsAndLetters.ts did not change any visible behavior: People Discovery
// renders and a connection request can be sent, a relation's timeline opens, and the letters
// inbox/outbox/threads tabs still render correctly -- against the real running app (dev profile,
// H2 + Mock AI provider).
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:8080";
const OUT = "D:/code/inner cosmos/evidence/track-b/screenshots";
fs.mkdirSync(OUT, { recursive: true });

const password = "Obs3rver!2026";
const stamp = Date.now();
const usernameA = `b1connA_${stamp}`;
const usernameB = `b1connB_${stamp}`;

const findings = [];
function log(msg) { findings.push(msg); console.log(msg); }

async function shot(page, name) {
  await page.screenshot({ path: path.join(OUT, name), fullPage: true });
  log(`screenshot: ${name}`);
}

async function register(page, username) {
  await page.goto(`${BASE}/app/aurora/`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.getByRole("tab", { name: "注册" }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "创建账号" }).click();
  await page.waitForTimeout(1500);
  log(`registered ${username}, url after register: ${page.url()}`);
}

(async () => {
  const browser = await chromium.launch();

  // A second account exists first, purely so account A's People Discovery list is non-empty and a
  // real connection request has somewhere real to go (a brand-new first-ever account would see
  // only whatever earlier test accounts already exist on this dev DB, which is fine too, but
  // registering B first makes the scenario reproducible and self-contained).
  const contextB = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const pageB = await contextB.newPage();
  await register(pageB, usernameB);
  await contextB.close();

  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on("pageerror", err => log(`pageerror: ${err.message}`));
  page.on("console", msg => { if (msg.type() === "error") log(`console.error: ${msg.text()}`); });

  await register(page, usernameA);

  // ---- Resonance space: send a real slow letter to a capsule match, so the Letters outbox has a
  // real, freshly-created entry to verify against connections/letters afterwards. ----
  await page.locator("nav[aria-label='Inner Cosmos 五个空间']").getByRole("button", { name: /共鸣/ }).click();
  await page.waitForTimeout(300);
  const startChatButton = page.getByRole("button", { name: "开始对话" }).first();
  if (await startChatButton.count() > 0) {
    await startChatButton.click();
    await page.waitForTimeout(600);
  }
  const letterTitleInput = page.getByLabel("信件标题", { exact: false }).first();
  const hasLetterForm = await letterTitleInput.count() > 0;
  if (hasLetterForm) {
    await letterTitleInput.fill("想把刚才的共鸣慢慢写下来");
    await page.getByLabel("信件正文", { exact: false }).first().fill("这段观察检查慢信是否仍然照常寄出。");
    await page.getByRole("button", { name: "把这些话寄给", exact: false }).click().catch(async () => {
      await page.getByRole("button", { name: /寄出|寄给/ }).first().click();
    });
    await page.waitForTimeout(800);
    log("sent a slow letter from the Resonance space to a capsule match");
  } else {
    log("no visitor match / letter form reachable this run -- resonance sample content may differ; letters outbox check will rely on whatever pre-existing state exists");
  }

  // ---- Connections/letters space ----
  await page.locator("nav[aria-label='Inner Cosmos 五个空间']").getByRole("button", { name: /连接/ }).click();
  await page.waitForTimeout(500);
  await shot(page, "b1-decompose-connections-01-space.png");

  const peopleSection = page.locator("section.people-discovery");
  const peopleCount = await peopleSection.locator(".person-card").count();
  log(`People Discovery: ${peopleCount} discoverable people rendered`);
  const targetCard = peopleSection.locator(".person-card", { hasText: usernameB }).first();
  const targetVisible = await targetCard.count() > 0;
  log(`account B (${usernameB}) visible in People Discovery: ${targetVisible}`);
  const requestButton = (targetVisible ? targetCard : peopleSection.locator(".person-card").first()).getByRole("button", { name: "想认识 ta" });
  if (await requestButton.count() > 0) {
    await requestButton.click();
    await page.waitForTimeout(1200);
    log("clicked '想认识 ta' on a discoverable person");
    await shot(page, "b1-decompose-connections-02-after-request.png");
    const statusAfter = await (targetVisible ? targetCard : peopleSection.locator(".person-card").first()).innerText();
    log(`person card text after request: ${statusAfter.replace(/\n/g, " | ")}`);
  } else {
    log("no requestable ('NONE' status) person card found this run");
  }

  // ---- Relations ----
  const relationsSection = page.locator("section.relations-view");
  const relationCount = await relationsSection.locator(".relation-card").count();
  log(`Relations: ${relationCount} relation mentions rendered`);
  if (relationCount > 0) {
    await relationsSection.locator(".relation-card").first().click();
    await page.waitForTimeout(800);
    await shot(page, "b1-decompose-connections-03-relation-timeline.png");
    const detailText = await relationsSection.locator(".relation-detail").innerText();
    log(`relation-detail panel text after opening a relation: ${detailText.slice(0, 200).replace(/\n/g, " | ")}`);
  } else {
    log("no relation mentions exist yet for this fresh account (relation extraction runs from real conversation history, matching the already-logged B0/B1 finding that J2/J3-style longitudinal state is hard to fabricate in one observation pass) -- verified instead that the empty state renders: " +
      (await relationsSection.innerText()).slice(0, 150).replace(/\n/g, " | "));
  }

  // ---- Letters inbox/outbox/threads ----
  const lettersSection = page.locator("section.letter-inbox");
  await lettersSection.getByRole("tab", { name: "寄出的" }).click();
  await page.waitForTimeout(300);
  await shot(page, "b1-decompose-connections-04-letters-outbox.png");
  const outboxText = await lettersSection.innerText();
  log(`letters outbox tab text: ${outboxText.slice(0, 200).replace(/\n/g, " | ")}`);
  await lettersSection.getByRole("tab", { name: "收到的" }).click();
  await page.waitForTimeout(200);
  const inboxText = await lettersSection.innerText();
  log(`letters inbox tab text: ${inboxText.slice(0, 150).replace(/\n/g, " | ")}`);
  await lettersSection.getByRole("tab", { name: "往来" }).click();
  await page.waitForTimeout(200);
  const threadsText = await lettersSection.innerText();
  log(`letters threads tab text: ${threadsText.slice(0, 150).replace(/\n/g, " | ")}`);
  await shot(page, "b1-decompose-connections-05-letters-threads.png");

  fs.writeFileSync(path.join(OUT, "observation-log-b1-decompose-connections.txt"), findings.join("\n"));
  await browser.close();
})().catch(err => {
  fs.writeFileSync(path.join(OUT, "observation-log-b1-decompose-connections-error.txt"), String(err && err.stack || err));
  console.error(err);
  process.exit(1);
});
