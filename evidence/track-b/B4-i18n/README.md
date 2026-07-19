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

## 3b. Live in-browser verification (this session)

Booted the real app (dev H2 + Mock), registered, opened the Me space and drove the switcher:

- Both new surfaces render in the real shell: the `语言 / 中文 / English` toggle and the
  `Aurora 停止使用了什么` data-rights panel (initial locale zh-CN via detection).
- Clicking **English** flipped `aria-pressed` (English=true, 中文=false) **and** switched the panel
  copy live — heading `What Aurora stopped using`, intro in English, button `View data-rights
  receipts`.
- The choice **persisted**: `localStorage["ic.locale"] === "en-SG"` (so `loadLocale()` restores it on
  next boot). **Zero console errors** across the flow.
- Also surfaced+fixed a real gotcha: the PWA service worker precaches the unhashed `app.js`, so a
  stale bundle is served after a rebuild until the SW is updated — hard-reload / SW-update needed to
  see new code (relevant to B5's update flow; the reload beat was already added there).

## 3c. Me control hub migrated (follow-up)
- `MeSpace` (the 我的 / 控制与边界 hub in `ProductShell.tsx`) is now fully bilingual via a
  `Record<Locale, …>` copy map, with English count pluralization (`1 active plan` /
  `3 mutual connections`); `AuroraApp` passes `locale={skillLocale}`. So switching language now
  translates the whole Me space (switcher + panel + hub), not just the data-rights panel.
- ProductShell.test now 12/12 (added a bilingual MeSpace case).

## 3d. Aurora conversation surface migrated (follow-up)
- `AuroraConversation` (the main chat surface) is now fully bilingual via a `Record<Locale, …>` map:
  empty state, speaker "you", partial-reply hint, the inline thinking beat copy, composer placeholders,
  voice-input labels, and the stop / interrupt-send / send controls. `AuroraApp` passes
  `locale={skillLocale}`. AuroraConversation.test now 6/6 (+1 en-SG case).

## 3e. AccountSettings migrated — the Me space is now fully bilingual (follow-up)
- `AccountSettings` (export / change-password / delete-account, including the client-side validation
  messages and the irreversibility warning) now renders via a `Record<Locale, …>` map;
  `AuroraApp` passes `locale={skillLocale}`. With MeSpace + DataRightsPanel + LocaleToggle already
  bilingual, the **entire Me / 控制与边界 space is now bilingual**. AccountSettings.test 5/5.

## 3f. All major component surfaces migrated (follow-up)
Every major user-facing component now renders via a `Record<Locale, …>` map and takes a `locale` prop
threaded from `AuroraApp`'s `skillLocale`, each with an added en-SG test case:
AuthGate, AuroraConversation, MeSpace, AccountSettings, DataRightsPanel, LocaleToggle,
UnderstandingCorrection, MemoryStarfield, AuroraSelfSpace, PeopleDiscovery, PlazaDirectory,
ResonanceNetwork, CapsuleWorkbench, LettersInbox. Full Vitest **227/227**; build PASS.

## 4. Remaining
- Component-level i18n is essentially complete (all five spaces + auth switch language end to end via
  the shared LocaleToggle). What's left: a handful of strings inlined directly in `AuroraApp.tsx`
  (the Aurora hero banner + mobile-presence section + a few transient status-banner messages) and any
  minor sub-panels; these live in the 1000-line shell rather than a component, so they're migrated as
  a separate focused pass. WCAG/a11y audit and non-author en-SG copy review also remain.
- WCAG/a11y audit and non-author en-SG copy review remain open B4 items.
