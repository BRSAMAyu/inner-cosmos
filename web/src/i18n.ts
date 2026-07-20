// B4 i18n groundwork. The app already ships a bilingual convention -- components take a
// `locale: "zh-CN" | "en-SG"` and look up a `Record<Locale, ...>` dictionary (see
// PsychologySkillStudio's SkillLocale and ClaimCandidateReview). This module gives that convention a
// single shared type plus locale DETECTION/persistence, so new surfaces can be bilingual without each
// re-deriving "which language" from scratch. It does not force a big-bang string migration; surfaces
// adopt the `locale` prop incrementally.

export type Locale = "zh-CN" | "en-SG";
export const LOCALES: readonly Locale[] = ["zh-CN", "en-SG"] as const;
export const DEFAULT_LOCALE: Locale = "zh-CN";
const STORAGE_KEY = "ic.locale";

/** Coerce any raw value (a stored pref or a navigator.language tag) to a supported Locale, or null. */
export function normalizeLocale(value: string | null | undefined): Locale | null {
  if (value === "zh-CN" || value === "en-SG") return value;
  if (!value) return null;
  const lower = value.toLowerCase();
  if (lower.startsWith("en")) return "en-SG";
  if (lower.startsWith("zh")) return "zh-CN";
  return null;
}

/** A stored preference wins over the browser language; fall back to the default when neither maps. */
export function detectLocale(opts?: { stored?: string | null; nav?: string | null }): Locale {
  return normalizeLocale(opts?.stored) ?? normalizeLocale(opts?.nav) ?? DEFAULT_LOCALE;
}

/** Browser convenience: resolve the active locale from localStorage then navigator. SSR/test-safe. */
export function loadLocale(): Locale {
  const stored = typeof localStorage !== "undefined" ? localStorage.getItem(STORAGE_KEY) : null;
  const nav = typeof navigator !== "undefined" ? navigator.language : null;
  return detectLocale({ stored, nav });
}

export function saveLocale(locale: Locale): void {
  if (typeof localStorage !== "undefined") localStorage.setItem(STORAGE_KEY, locale);
}
