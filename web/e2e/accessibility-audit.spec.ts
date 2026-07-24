import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

type ThemeChoice = "day" | "night";
type LocaleChoice = "zh-CN" | "en-SG";
type ProductSpace = "aurora" | "cosmos" | "resonance" | "letters" | "me";

const MATRIX: Array<{ theme: ThemeChoice; locale: LocaleChoice }> = [
  { theme: "day", locale: "zh-CN" },
  { theme: "night", locale: "zh-CN" },
  { theme: "day", locale: "en-SG" },
  { theme: "night", locale: "en-SG" },
];

const SPACE_LABELS: Record<LocaleChoice, Record<ProductSpace, string>> = {
  "zh-CN": {
    aurora: "今天",
    cosmos: "内宇宙",
    resonance: "共鸣",
    letters: "连接",
    me: "我的",
  },
  "en-SG": {
    aurora: "Today",
    cosmos: "Cosmos",
    resonance: "Resonance",
    letters: "Connect",
    me: "Me",
  },
};

const SPACES: ProductSpace[] = ["aurora", "cosmos", "resonance", "letters", "me"];

function navName(locale: LocaleChoice): string {
  return locale === "en-SG" ? "Inner Cosmos, five spaces" : "Inner Cosmos 五个空间";
}

function anchored(text: string): RegExp {
  return new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
}

async function primeThemeAndLocale(page: Page, theme: ThemeChoice, locale: LocaleChoice) {
  await page.addInitScript(
    ([t, l]) => {
      window.localStorage.setItem("ic-color-scheme", t);
      window.localStorage.setItem("ic.locale", l);
    },
    [theme, locale] as const,
  );
}

async function login(page: Page, locale: LocaleChoice) {
  await page.goto("/app/aurora/index.html");
  const copy = locale === "en-SG"
    ? { heading: "Back to your inner cosmos", username: "Username", password: "Password", button: "Log in", dismiss: "Got it" }
    : { heading: "回到你的内宇宙", username: "用户名", password: "密码", button: "登录", dismiss: "知道了" };
  const loginHeading = page.getByRole("heading", { name: copy.heading });
  const appShell = page.getByRole("navigation", { name: navName(locale) });
  await expect(loginHeading.or(appShell)).toBeVisible();
  if (await loginHeading.isVisible().catch(() => false)) {
    await page.getByLabel(copy.username, { exact: true }).fill(process.env.E2E_USERNAME ?? "demo");
    await page.getByLabel(copy.password, { exact: true }).fill(process.env.E2E_PASSWORD ?? "demo123");
    await page.getByRole("button", { name: copy.button, exact: true }).click();
  }
  await expect(appShell).toBeVisible();
  const offlineNotice = page.getByRole("button", { name: copy.dismiss, exact: true });
  if (await offlineNotice.isVisible().catch(() => false)) await offlineNotice.click();
}

test.describe("axe-core WCAG audit across product spaces, themes and locales", () => {
  for (const { theme, locale } of MATRIX) {
    test(`theme=${theme} locale=${locale}`, async ({ page }) => {
      test.setTimeout(180_000);
      await primeThemeAndLocale(page, theme, locale);
      await login(page, locale);

      const violations: Array<{
        space: ProductSpace;
        id: string;
        impact: string | null | undefined;
        help: string;
        targets: Array<string | string[]>;
      }> = [];

      for (const space of SPACES) {
        const appShell = page.getByRole("navigation", { name: navName(locale) });
        await appShell
          .getByRole("button", { name: anchored(SPACE_LABELS[locale][space]) })
          .click();
        await page.waitForTimeout(300);

        const results = await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
          .analyze();
        for (const violation of results.violations) {
          violations.push({
            space,
            id: violation.id,
            impact: violation.impact,
            help: violation.help,
            targets: violation.nodes.map(node => node.target),
          });
        }
      }

      expect(
        violations,
        `axe violations (theme=${theme}, locale=${locale}): ${JSON.stringify(violations, null, 2)}`,
      ).toEqual([]);
    });
  }
});
