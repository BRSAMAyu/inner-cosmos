import { describe, expect, it } from "vitest";
import { detectLocale, DEFAULT_LOCALE, normalizeLocale, syncDocumentLocale } from "./i18n";

describe("i18n", () => {
  it("normalizes exact, prefixed and unknown locale values", () => {
    expect(normalizeLocale("zh-CN")).toBe("zh-CN");
    expect(normalizeLocale("en-SG")).toBe("en-SG");
    expect(normalizeLocale("en-US")).toBe("en-SG");
    expect(normalizeLocale("EN")).toBe("en-SG");
    expect(normalizeLocale("zh-Hans")).toBe("zh-CN");
    expect(normalizeLocale("fr-FR")).toBeNull();
    expect(normalizeLocale("")).toBeNull();
    expect(normalizeLocale(null)).toBeNull();
  });

  it("prefers a stored preference over the browser language", () => {
    expect(detectLocale({ stored: "en-SG", nav: "zh-CN" })).toBe("en-SG");
    expect(detectLocale({ stored: "zh-CN", nav: "en-US" })).toBe("zh-CN");
  });

  it("falls back to the browser language, then the default, when there is no valid stored pref", () => {
    expect(detectLocale({ stored: null, nav: "en-GB" })).toBe("en-SG");
    expect(detectLocale({ stored: "fr-FR", nav: "en-GB" })).toBe("en-SG");
    expect(detectLocale({ stored: null, nav: "fr-FR" })).toBe(DEFAULT_LOCALE);
    expect(detectLocale()).toBe(DEFAULT_LOCALE);
  });

  it("keeps the document language metadata aligned with the rendered locale", () => {
    syncDocumentLocale("en-SG");
    expect(document.documentElement.lang).toBe("en-SG");
    expect(document.documentElement.dir).toBe("ltr");

    syncDocumentLocale("zh-CN");
    expect(document.documentElement.lang).toBe("zh-CN");
  });
});
