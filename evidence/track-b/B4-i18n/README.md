# Track B — B4 i18n groundwork (shared locale detection + first new bilingual surface)

> Status: BUILDER_VERIFIED_IN_PROGRESS
> Binds to: Track B workstream B4 ("Chinese and English/en-SG").

## 1. What was implemented (frontend only)

The app already had a per-component bilingual convention (`SkillLocale = "zh-CN" | "en-SG"` +
`Record<Locale, dict>` lookups, e.g. `ClaimCandidateReview`), but no shared way to *decide* which
locale is active — the one live consumer hard-coded `"zh-CN"`. This slice adds that missing seam and
proves it on a real surface, without a big-bang string migration:

- `web/src/i18n.ts` — shared `Locale` type, `normalizeLocale()` (maps any `en*`/`zh*` tag or exact
  value to a supported locale, else null), `detectLocale({stored, nav})` (stored preference wins,
  then browser language, then `zh-CN`), and SSR/test-safe `loadLocale()` / `saveLocale()`.
- `web/src/components/DataRightsPanel.tsx` — now takes an optional `locale` prop (default `zh-CN`) and
  is fully bilingual (heading, intro, button, empty state, derivative/action labels, item counts,
  localized timestamps) via the same `Record<Locale, …>` pattern.
- `web/src/AuroraApp.tsx` — initializes the existing `skillLocale` from `loadLocale()` (so the app now
  respects a stored preference / browser language instead of always `zh-CN`) and passes it to
  `DataRightsPanel`.

## 2. Verification
- `npm test -- --run src/i18n.test.ts src/components/DataRightsPanel.test.tsx` → **7/7** (i18n
  normalization + detection precedence; DataRightsPanel renders both zh-CN default and en-SG copy).
- Full suite `npm test -- --run --no-file-parallelism` → **210/210**; `npm run build` PASS (18 PWA
  precache entries), static assets rebuilt.

## 3. In-app language switcher (follow-up)

- `web/src/components/LocaleToggle.tsx` — an accessible 中文/English switcher (role=group,
  `aria-pressed`, self-labels in the active language, reuses `.appearance-toggle` styling) rendered in
  the Me space next to the theme toggle.
- `AuroraApp` wires it via `changeLocale`, which sets the shared `skillLocale` and persists through
  `i18n.saveLocale`, so the choice survives reloads (and `loadLocale` picks it up next boot).
- `web/src/components/LocaleToggle.test.tsx` (2 tests): active-locale `aria-pressed` + self-labelling,
  and the chosen locale is emitted on click.

`npm test -- --run src/components/LocaleToggle.test.tsx` → **2/2**; full suite **212/212**; build PASS.

## 4. Remaining
- This is groundwork + two migrated surfaces (DataRightsPanel) + the switcher, not full coverage. The
  rest of the shell is still Chinese-only; each surface adopts the `locale` prop incrementally.
- Live in-browser verification of the switcher flipping copy is a follow-up (deterministically covered
  here); WCAG/a11y audit and non-author en-SG copy review remain open B4 items.
