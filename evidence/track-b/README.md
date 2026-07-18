# Track B Evidence Index

> Track: `codex/track-b-complete-experience` — Complete Experience & Delivery.
> Spec: `docs/tracks/TRACK-B-COMPLETE-EXPERIENCE.md`. Status: `docs/goal/tracks/track-b-status.yml`.

## Workstream B0 — Experience observation (this checkpoint)

| Artifact | What it is |
|---|---|
| [`experience-inventory.md`](./experience-inventory.md) | Route/space-by-space inventory of the live product (desktop + mobile), auth/entry findings, shared primitives worth keeping, screenshot manifest, reproduction steps |
| [`golden-journeys.md`](./golden-journeys.md) | J1 golden-first-experience, J2 longitudinal-return, J3 resonance journeys plus 7 recovery journeys, each with target vs. observed behavior and a LIVE/SOURCE/UNVERIFIED verification tag |
| [`screenshots/`](./screenshots/) | Desktop (1440×900) and mobile (390×844) screenshots of all five spaces on a zero-content account, the registration flow, a live Aurora conversation (thinking/multi-message/settled states), a forced-offline reload, and an expired-auth deep link. See the manifest table in `experience-inventory.md` §6. |
| [`scripts/observe-b0-journeys.mjs`](./scripts/observe-b0-journeys.mjs) | The Playwright script used to produce the above — registers a fresh account, walks all five spaces at both viewports, drives one real conversation turn, and forces the offline/expired-auth recovery states. Must be run with `cwd=web/` (see reproduction steps in `experience-inventory.md` §7). |

### How this evidence was produced

`dev` profile (H2 + Mock AI provider), local `./mvnw.cmd spring-boot:run` after a fresh
`cd web && npm ci && npm run build`, against `http://localhost:8080`. No real provider keys, no
Docker/Testcontainers involved — this is a frontend/UX observation pass, not a backend or AI-quality
evaluation (that is Track A's evidence root, `evidence/track-a/`).

### Key findings carried into B1+ (see `golden-journeys.md` "What this unblocks for B1" for the full list)

1. The React AppShell (`/app/aurora/`) has no registration entry point at all; the only working
   registration form is the legacy static page `/pages/register.html`, unlinked from the new app. This is
   the single largest blocker to the "golden first experience" journey.
2. Registration → AppShell session hand-off works correctly (cookie carries over, no forced re-login).
3. The Aurora conversation experience itself (multi-message pacing, stop/interrupt controls, a visible
   dual-core status signal) is already working well and should be preserved through the B1 refactor, not
   rebuilt.
4. No real routes exist — all navigation is a `?space=` query param on one path, and all five spaces are
   simultaneously mounted in the DOM and toggled with the `hidden` attribute out of one 1292-line
   `AuroraApp.tsx`.
5. Mobile bottom navigation is not fixed/sticky; on the Aurora space it appears after several unrelated
   content blocks, pushing the composer below the fold on first load.
6. No PWA offline shell exists — a forced-offline reload produces a blank browser network-error page.
7. Expired-auth deep links silently drop the intended target and show an unexplained bare login form.
8. The Resonance space ships 11 fully-written sample capsule personas visible to every fresh account;
   whether this is intentional sample content or leaked seed data is unresolved and blocks designing the
   right Resonance first-run state.
9. The People-Discovery list surfaces QA/test accounts (`CSRF Runtime`, `Smoke`, duplicate `Bo Observer`
   test users) to real users — a data-hygiene problem for any local-complete demo.
10. A shared loading/error primitive set (`web/src/loading.tsx`) already implements the spec's 3-tier
    delayed-loading rule and a retry-capable error component — B1's "single async/task/error model" should
    audit for consistent adoption rather than invent a new one.

None of the above were fixed in this pass — B0 is observation-only per the task charter. They are the
starting backlog for B1 (architecture/IA), B2 (visible innovation), B3 (design/content), and B5
(PWA/mobile).

## Workstream B1 — first checkpoint: unified login/register entry + mobile composer reorder

Fixes B0 finding #1 (no register entry point in the SPA) and the mobile half of finding #5 (composer
pushed below the fold). `B1-product-architecture`'s full scope (real routes, `AuroraApp.tsx`
decomposition, single async/task/error model, progressive disclosure) remains open — see
`docs/goal/tracks/track-b-status.yml` for the current front and handoff notes.

| Artifact | What it is |
|---|---|
| `web/src/components/AuthGate.tsx` | New component: the single entry surface for `/app/aurora/`. A `role="tablist"` login/register mode toggle on the same screen, same warm-theme markup as the previous inline `Login`. Native/Capacitor OIDC branch is untouched. |
| `web/src/components/AuthGate.test.tsx` | 13 Vitest/RTL cases: characterizes the pre-existing login-only behavior first, then covers the mode toggle, register validation (password length, confirmation mismatch), the exact `api.register(username, nickname, password)` contract (nickname optional, falls back server-side too but mirrored client-side), success/failure paths, and that the native branch is unaffected. |
| `web/src/api.ts` | Added `api.register(username, nickname, password)` calling `POST /api/v1/auth/register`, matching `register.html`'s exact contract (`{ username, nickname: nickname \|\| username, password }`). `AuthController.register` already rotates/creates the session the same way `login` does, so a successful call can drive the same `onSuccess`/bootstrap path as login — confirmed live, not just read from source. |
| `web/src/AuroraApp.tsx` | Swapped the inline `Login` function for `<AuthGate>`. Also merged the Aurora space's two disconnected `product-space` blocks (B0 finding: "not contiguous in JSX") into one, and moved the composer (`SkillSuggestionBanner` + `AuroraConversation`) to sit directly after the hero/mode-picker, ahead of the WakeIntent ("回来约定") and Self/Emergence ("AURORA, BECOMING") panels. Pure reorder — no state/handler changes. |
| `web/src/styles.css` | Added `.auth-copy` / `.auth-mode-switch` styles for the new toggle, reusing the existing pill-button visual language (same pattern as `.appearance-options`/`.strategy-switcher`). No other selectors changed. |
| `evidence/track-b/scripts/observe-b1-register.mjs` | New Playwright script: fresh visit to `/app/aurora/` → register via the new toggle → password-mismatch inline error → successful register lands authenticated in the AppShell → logout → re-login via the same toggle → expired/cleared-session deep link also offers the register tab → mobile viewport of the toggle. |
| `evidence/track-b/screenshots/b1-01…b1-12*` | Live screenshots from the above, plus two ad hoc composer-order checks (`b1-12-aurora-composer-order-{mobile,desktop}.png`) confirming the composer is now within the initial viewport (no scroll) on both breakpoints. |

### How this was verified

- `cd web && npx vitest run` — 138/138 passing (24 files), including the new 13-case `AuthGate.test.tsx`.
- `cd web && npm run build` — clean `tsc -b && vite build`, output written to
  `src/main/resources/static/app/aurora/`.
- Live run: `dev` profile (H2 + Mock provider), `JAVA_HOME=jdk-21.0.10`, `.\mvnw.cmd spring-boot:run`,
  server restarted after each frontend rebuild so the freshly built static assets are actually served
  (Spring Boot copies `src/main/resources/static` into the classpath at boot; a running instance does not
  pick up a later `npm run build` without a restart — worth remembering for future B-track checkpoints).
- `node evidence/track-b/scripts/observe-b1-register.mjs`, executed from a temporary copy under `web/`
  (Node's ESM resolver walks up from the *script file's own path*, not `cwd`, so a script physically
  outside `web/` cannot resolve `web/node_modules/playwright` no matter the working directory — this
  contradicts `experience-inventory.md` §7's "must run with cwd=web/" note, which does not actually work
  in this Node 22.20 environment; noted here so the next session doesn't rediscover this the hard way).
  Confirmed live: register tab discoverable on first visit, password-mismatch shows an inline error and
  never calls the API, successful register lands fully authenticated in the AppShell (composer visible,
  five-space nav present), logout → re-login through the same toggle works, and the previously bare
  expired-auth deep link (`?space=cosmos` with cleared cookies) now also offers the register tab.
- Ad hoc mobile/desktop check confirmed `.composer` is within the initial viewport's bounding rect on both
  390×844 and 1440×900 after the JSX reorder, and that `.app-shell-nav` remains reachable in both cases
  (`position: fixed` at mobile, `position: sticky` at desktop — this was already correct pre-existing CSS;
  B0's claim that the mobile nav itself was not fixed/sticky did not reproduce in this pass, only the
  composer-below-the-fold half of that finding did — see `docs/goal/tracks/track-b-status.yml` discoveries).

### What is still open for B1

- No real client routes yet (still one `?space=` query param). `AuroraApp.tsx` is still ~1290 lines with
  60+ `useState` hooks — this checkpoint reduced the Aurora space's JSX fragmentation but did not touch
  the domain-hook/store decomposition called for in the spec.
- The theme-inconsistency finding (register.html vs. the SPA rendering opposite day/night themes) is now
  moot for any *new* user, since the primary path is the SPA's own login/register toggle sharing one theme
  implementation; `register.html` itself is untouched and still exists as a secondary, unlinked path (see
  "should register.html retire" below).
- `register.html`/`login.html` retirement: not done this pass, per the task brief's explicit "do not delete
  yet." Recommendation for a later checkpoint: once the SPA register path has a few checkpoints of real
  usage confirming no regressions (e.g. locale/timezone handling, mobile Capacitor build), redirect
  `/pages/register.html` and `/pages/login.html` to `/app/aurora/` (or remove them) and drop the now-dead
  `doRegister()`/`doLogin()` legacy JS — but keep them until then since they are still the only tested path
  for anyone with a bookmarked/linked URL.
- Mobile Aurora-space reordering is now solved for the *composer*; the WakeIntent/Self-Emergence panels
  still render as full-detail sections rather than progressively disclosed — that's B2/B3 territory
  (progressive disclosure, not just ordering).

## Workstream B1 — second checkpoint: real client routing

Replaces the `?space=` query param with real, addressable client-side routes (`react-router`), the
prerequisite the spec (§5) calls out for shareable deep links and for eventually splitting
`AuroraApp.tsx`'s remaining ~60 `useState` hooks into per-route domain hooks.

| Artifact | What it is |
|---|---|
| `web/src/components/ProductShell.tsx` | Added `spacePath(space)` (space → real path, e.g. `letters` → `/connections/letters`) and `productSpaceFromPath(pathname)` (route → space, matching nested sub-paths too so a future per-space route split doesn't need to touch this resolver). `initialProductSpace` (the old `?space=` reader) is kept as a characterization-tested legacy-link redirect target, not the live navigation mechanism anymore. |
| `web/src/main.tsx` | Mounts `HashRouter` (not `BrowserRouter`) around `<AuroraApp>` — Spring's static handling only forwards the exact `/app/aurora`/`/app/aurora/` paths, with no `/app/aurora/**` catch-all, so a `BrowserRouter` path would 404 on refresh. Filed as `TB-REQ-002`; the rest of the app depends only on react-router's location/navigate API, so swapping routers later is a one-line change. |
| `web/src/AuroraApp.tsx` | `productSpace` is now derived from `useLocation().pathname` via `useMemo`, not copied into `useState` once at mount — this is also what makes an expired-auth deep link resume the right space after re-login "for free," since the route itself never changes underneath the `AuthGate` swap. `navigateSpace` now calls react-router's `navigate()`. A one-time effect redirects legacy `?space=` bookmarks to their real-route equivalent. A `<Routes>` block canonicalizes the URL (root and unrecognized paths redirect to `/aurora`); each space's content still mounts unconditionally and toggles via `hidden`, preserving in-progress state exactly as before (draft text, scroll position, sandbox results). |
| `web/src/components/ProductShell.test.tsx` | New cases for `spacePath`, `productSpaceFromPath` (exact, nested-subroute, and fallback cases), alongside the pre-existing `initialProductSpace` characterization test. |
| `evidence/track-b/scripts/observe-b1-routes.mjs` | New Playwright script: register → click through all five nav items, asserting the URL becomes the real hash route each time → browser back → direct hash deep link to `/#/resonance` → legacy `?space=letters` link redirect. |
| `evidence/track-b/screenshots/b1-routes-*.png` | Screenshots from the above. |
| `docs/goal/tracks/track-b-integration-requests.yml` | `TB-REQ-002`: server-side `/app/aurora/**` SPA fallback, to unblock swapping `HashRouter` → `BrowserRouter` later. |

### How this was verified

- `cd web && npx vitest run` — 142/142 passing (24 files).
- `cd web && npm run build` — clean `tsc -b && vite build`.
- Live run (`dev` profile, H2 + Mock, `.\mvnw.cmd spring-boot:run`) via
  `evidence/track-b/scripts/observe-b1-routes.mjs`: fresh register lands on `#/aurora`; clicking
  each of the five nav items (共鸣/内宇宙/连接/我的/今天) updates the URL to
  `#/resonance`, `#/cosmos`, `#/connections/letters`, `#/me`, `#/aurora` respectively (all
  confirmed true); `goBack()` correctly returns to the previous space's route; a direct visit to
  `/app/aurora/#/resonance` (simulating a bookmarked/shared link) renders resonance content
  immediately with no click-through needed; a legacy `/app/aurora/?space=letters` link redirects to
  `#/connections/letters` instead of 404ing or being silently ignored.
- Methodology note: the first run of this script used loose, unscoped `getByRole("button", {name})`
  selectors and produced a false-looking failure (nav click for "我" landed on `#/cosmos`) — this was
  a test-script bug (matching a same-substring element elsewhere in the simultaneously-mounted DOM,
  not the actual nav item, and the actual label is "我的" not "我"), not an app bug. Fixed by scoping
  to the `nav[aria-label="Inner Cosmos 五个空间"]` landmark and using each space's exact label. See
  `docs/goal/tracks/track-b-status.yml` discoveries for the general lesson.

### What is still open for B1

- `AuroraApp.tsx` is still ~1270 lines with 60+ `useState` hooks — routing gives real addresses to
  hang per-route domain hooks/state off of, but the extraction itself is the next step, not done here.
- Only the five top-level spaces have real routes. Nested sub-routes from the spec's suggested model
  (`/cosmos/starfield` vs `/cosmos/portrait`, `/resonance/capsule/:id`, `/connections/letters/:id`) are
  not yet split out — `productSpaceFromPath` already resolves nested sub-paths to their parent space,
  so adding these later is additive, not a rework.
- No single async/task/error model consolidation yet (separate from routing; see the B0 finding about
  `web/src/loading.tsx`'s existing primitives).
- `HashRouter` is an intentional, documented interim choice (`TB-REQ-002`), not the end state.
