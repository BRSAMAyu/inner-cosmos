import { expect, test } from "@playwright/test";

// W2 voice-feature golden journeys (machine evidence for G7.UX-COMPLETE / G9.FINAL-E2E).
// Verifies the integrated voice stack RENDERS and does not error end-to-end on the current HEAD:
//   1. Me/Settings voice picker shows the 6 presets and a delivery-mode radio group;
//      changing the preset is an observable, error-free instant save.
//   2. A voice preview tap produces an observable response (busy -> settled), never an unhandled
//      page error -- Mock chat has no real TTS key, so the preview may legitimately fail, but it
//      must fail gracefully (inline error), not crash.
//   3. The slow-letter tap-to-play affordance ("▶ 朗读这封信") renders on a delivered letter.
//   4. An Aurora turn still completes cleanly (bubble + "在这里" status) with the inner-voice
//      event path wired in -- the inner_voice SSE handling must not break the normal turn.

async function login(page: import("@playwright/test").Page) {
  const login = page.getByRole("heading", { name: "回到你的内宇宙" });
  const appShell = page.getByRole("navigation", { name: "Inner Cosmos 五个空间" });
  await expect(login.or(appShell)).toBeVisible();
  if (await login.isVisible().catch(() => false)) {
    await page.getByLabel("用户名").fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel("密码").fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: "登录" }).click();
  }
  await expect(appShell).toBeVisible();
  const offlineNotice = page.getByRole("button", { name: "知道了" });
  if (await offlineNotice.isVisible().catch(() => false)) await offlineNotice.click();
}

function openSpace(page: import("@playwright/test").Page, name: "今天" | "内宇宙" | "共鸣" | "连接" | "我的") {
  return page.getByRole("navigation", { name: "Inner Cosmos 五个空间" })
    .getByRole("button", { name: new RegExp(`^${name}`) }).click();
}

test("Me/Settings voice picker renders the 6 presets and instant-saves a new pick", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("/app/aurora/index.html");
  await login(page);

  await openSpace(page, "我的");
  // The voice section renders once ttsPreferences resolves (fetched on bootstrap).
  const voiceTitle = page.getByText("Aurora 的声音").first();
  await expect(voiceTitle).toBeVisible({ timeout: 15000 });

  // Exactly 6 voice preset radios, matching TtsVoicePresets.ALL.
  const presets = page.locator('input[type="radio"][name="inner-voice-preset"]');
  await expect(presets).toHaveCount(6);

  // The delivery-mode radio group (AMBIENT / ON_DEMAND) is present.
  await expect(page.locator('input[type="radio"][name="inner-voice-mode"]')).toHaveCount(2);

  // Pick the last preset (different from the default warm_gentle_female) and confirm it selects.
  const last = presets.nth(5);
  const lastId = await last.getAttribute("value");
  await last.check();
  await expect(last).toBeChecked();
  expect(["warm_gentle_female", "calm_steady_female", "deep_soothing_male",
    "bright_young_female", "bright_young_male", "warm_expressive_female"]).toContain(lastId);

  // No unhandled errors from the instant-save PATCH path.
  expect(errors, errors.join("; ")).toEqual([]);
});

test("a voice preview tap always produces an observable response and never a page error", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("/app/aurora/index.html");
  await login(page);
  await openSpace(page, "我的");
  await expect(page.getByText("Aurora 的声音").first()).toBeVisible({ timeout: 15000 });

  const previewBtn = page.getByRole("button", { name: "▶ 试听" }).first();
  await expect(previewBtn).toBeVisible();
  // Clicking preview must change SOMETHING within a beat: the button goes busy ("试听中…"),
  // an inline audio player renders (Mock may synthesize), or a graceful error appears.
  await previewBtn.click();
  await expect.poll(async () => {
    const busy = await page.getByRole("button", { name: "试听中…" }).count();
    const audio = await page.locator("audio").count();
    const errMsg = await page.locator(".voice-error").count();
    return busy > 0 || audio > 0 || errMsg > 0;
  }, { timeout: 12000 }).toBeTruthy();

  // No unhandled JS error from the preview / audio-playback path.
  expect(errors, errors.join("; ")).toEqual([]);
});

test("the slow-letter tap-to-play affordance renders on a delivered letter", async ({ page }) => {
  await page.goto("/app/aurora/index.html");
  await login(page);
  await openSpace(page, "连接");
  // The seed delivers a river->demo letter ("你写的黄昏让我停了一下", status DELIVERED).
  const inbox = page.getByRole("region", { name: "慢信收件箱与寄件箱" });
  await expect(inbox).toBeVisible({ timeout: 15000 });
  // The "▶ 朗读这封信" affordance is shown on each delivered letter when voice is wired.
  await expect(page.getByRole("button", { name: "▶ 朗读这封信" }).first()).toBeVisible({ timeout: 15000 });
});

test("an Aurora turn completes cleanly with the inner-voice event path wired in", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("/app/aurora/index.html");
  const composer = await (async () => {
    await login(page);
    return page.getByLabel("写给 Aurora");
  })();
  await composer.fill("今天有点累，想被接住一下。 ");
  await page.getByRole("button", { name: "发送" }).click();
  // The turn must reach the settled "在这里" status (inner_voice event handling must not derail it).
  await expect(page.getByLabel("Aurora 当前回应状态")).toContainText("在这里", { timeout: 30000 });
  await expect(page.locator("article.aurora").last()).toContainText(/.+/);
  expect(errors, errors.join("; ")).toEqual([]);
});
