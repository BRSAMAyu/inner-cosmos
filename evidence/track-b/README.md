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

## Workstream B1 — third checkpoint: single async/task/error model audit

Audits every component under `web/src/components/` and `web/src/AuroraApp.tsx` for busy/loading/error
UI that hand-rolls what `web/src/loading.tsx`'s existing `useDelayedBusy`/`AsyncButton`/`LoadingText`/
`ConnectError` primitives already do correctly (B0 finding #10). No new primitive was invented; every
migration below adapts a call site onto the existing API. `web/src/loading.tsx` itself was **not**
touched — every hand-rolled instance found fit the existing API without modification.

### Migrated (mechanism swap, same copy, same disable logic)

| File | What changed |
|---|---|
| `web/src/components/MemoryStarfield.tsx` | Four buttons converted to `AsyncButton`, preserving their exact existing hand-written busy copy: the importance-save button (`"保存重要度"` / `"保存中…"`), the archive button (`"归档这颗记忆"` / `"归档中…"`), the per-star "查看来源与变化" reveal button (`"正在追溯…"`, with the original *global* `detailBusy !== null` disable preserved via `AsyncButton`'s explicit `disabled` prop alongside its own per-star `busy`), and the per-operation "撤回这次变更" rollback button (`"正在撤回…"`, same global-disable-plus-per-item-busy technique). The three `时间/主题/人物` mode-tab buttons were deliberately **not** migrated — see "Left alone" below. |
| `web/src/components/ClaimCandidateReview.tsx` | The confirm (`"对，就是我"` / `t.confirming`) and inline dismiss-confirm (`"确认忽略"` / `t.dismissing`) buttons, which already hand-rolled the exact `disabled={busy} + {busy ? busyText : label}` pattern `AsyncButton` exists to replace. The "rethink"/initial-dismiss buttons stay plain (they only toggle local UI state, they are not themselves async). |
| `web/src/components/CapsuleWorkbench.tsx` | The one hand-rolled holdout in a file that otherwise already used `AsyncButton` everywhere else: `CapsuleBoundaryEditor`'s "保存边界设置" / "保存中…" save button. |
| `web/src/components/UnderstandingCorrection.tsx` | The actual async trigger for retiring a correction (`btn-retire-confirm`, `"确认退休"` / `"退休中…"`). The sibling "让这条退休" opener button (which only ever renders *before* the inline confirmation state, so it can never actually be seen mid-retire) was deliberately left alone — see "Left alone". |
| `web/src/components/PsychologySkillStudio.tsx` | The "开始这次反思" / `text.busy` (bilingual) skill-run button. |
| `web/src/components/RelationsView.tsx` | The relation-timeline panel's `busy ? <div className="network-empty">正在读取「{selected}」的时间线…</div> : ...` block-level loading message — this is exactly what `LoadingText` is for (a region/panel loading indicator, not a single button), so it was swapped for `<LoadingText busy className="network-empty">` with the identical copy. |
| `web/src/components/AuthGate.tsx` | The login/register submit button, which previously only disabled with zero busy feedback (`disabled={busy}`, static label). Now an `AsyncButton` with a mode-dependent `busyText` (`"正在登录"` / `"正在创建"`), following the "正在X" convention already established by every other `AsyncButton` call site in the codebase. This is the very first interactive control a new user sees, so it was worth fixing even though it needed a small (convention-consistent) copy addition rather than a pure mechanism swap. |
| `web/src/components/AuroraConversation.tsx` | The voice-input button, which previously hand-rolled a 3-way `transcribing ? "转写中…" : recording ? "● 停止录音" : "🎤 语音"` label with `disabled={transcribing \|\| !sessionReady}`. Converted to `AsyncButton` with `busy={transcribing}`, `busyText="转写中…"`, `disabled={!sessionReady}`, and `children={recording ? "● 停止录音" : "🎤 语音"}` — the non-busy branch still needs the recording/idle toggle, which `AsyncButton` supports since it only replaces `children` while `busy`. |
| `web/src/components/PortraitView.tsx` | The per-dimension calibration "告诉 Aurora" button, which previously only disabled (`disabled={busyDim === d.dim \|\| !draft.trim()}`) with **zero** busy feedback at all — a real instance of the "plain `disabled={busy}` button with no busy text" pattern the audit was asked to find. Converted to `AsyncButton` (busy=`busyDim === d.dim`, disabled=`!draft.trim()`), relying on the primitive's own built-in `"处理中"` fallback rather than inventing new component-specific copy, since only one calibration form is ever open at a time (no sibling-button ambiguity). |

### Left alone, with reasons (discoveries, not fixes)

Several more `disabled={busy}`-only buttons were found and deliberately **not** migrated, because in
each case one shared busy boolean disables **several sibling buttons that represent different
items** (candidates, proposals, versions, mode tabs, strategies, runs, rating options) with no
per-item id to key off of. Naively wrapping each sibling in its own `AsyncButton` with the same shared
`busy` flag would make *every* sibling button show busy text the instant *any one* of them is
clicked — a UX regression (misleading feedback on buttons the user didn't touch), not a consolidation.
Fixing this properly means giving the underlying state a per-item id (mirroring the `claimCandidateBusyId` /
`detailBusy` / `rollbackBusy` pattern already used elsewhere in `AuroraApp.tsx`), which is state-shape
work that belongs with the `AuroraApp.tsx` domain-hook decomposition, not a call-site swap:

- `web/src/components/AuroraSelfSpace.tsx` — propose/evaluate/activate/rollback buttons across
  different candidates/proposals/versions all share one `busy: boolean` prop, with no busy text at all.
- `web/src/components/MemoryStarfield.tsx` — the `时间`/`主题`/`人物` starfield-mode tab buttons share
  one `starfieldBusy: boolean`.
- `web/src/components/ResonanceNetwork.tsx` — the five matching-strategy switcher buttons share one
  `visitorBusy: boolean`.
- `web/src/components/PsychologySkillStudio.tsx` — each saved run's "撤回这次结果" button shares one
  `skillBusy: boolean` across the whole run list (no per-run id).
- `web/src/components/CapsuleWorkbench.tsx` — the five sandbox-feedback rating buttons share one
  `capsuleBusy: boolean`.

Also left alone, for other reasons:

- `web/src/components/LettersInbox.tsx` — the reply-to-letter button (`disabled={!replyDrafts[letter.id]?.trim()}`)
  has **no busy tracking passed in at all** (no prop exists for it), so clicking it while the reply
  network call is in flight gives zero feedback and does not guard against a double-submit. This is a
  real gap, but fixing it needs a new busy-id piece of state threaded from `AuroraApp.tsx`'s
  `replyWithLetter`, not just a call-site swap — flagged here as backlog rather than invented on the spot.
- `web/src/components/AuroraConversation.tsx`'s send button (`{activeTurnId ? "打断并发送" : "发送"}`) —
  intentionally a different, correct pattern: it reflects "there is an active turn you can interrupt"
  domain state, not a loading indicator for the send action itself. Left unchanged.
- Various "cancel"/"return to edit"/"rethink" buttons next to a migrated `AsyncButton` (e.g. `CapsuleWorkbench`'s
  "返回修改", `UnderstandingCorrection`'s "改为整体理解"/"返回修改", `ClaimCandidateReview`'s "再想想") —
  these stay plain `disabled={busy}` buttons since they perform a synchronous local-state toggle, not
  the async action itself; this already matches the precedent set by every previously-migrated component.

### Tests

Nine `.test.tsx` files needed updating alongside their components:

- `MemoryStarfield.test.tsx`, `ClaimCandidateReview.test.tsx`, `CapsuleWorkbench.test.tsx`: each had one
  assertion that queried a busy button by its *already-swapped* label (e.g. `"保存中…"`) synchronously,
  with no timer advance. `AsyncButton` withholds the busy label for the first second (the spec's "don't
  flash before 1s" rule), so a synchronous assert now checks `disabled` on the *original* label —
  exactly the convention every already-migrated component's tests (`AccountSettings.test.tsx`,
  `PeopleDiscovery.test.tsx`) already used before this checkpoint.
- `UnderstandingCorrection.test.tsx`: the pre-existing "shows a retiring state…" test turned out to
  exercise the *un-migrated* opener button, not the actual async trigger (a two-click interaction is
  needed to reach the confirm button while busy). Added a new characterization test for the confirm
  button first — ran it against the pre-migration hand-rolled code to confirm it passed with the old,
  undelayed text, *then* migrated the component and updated that one assertion to the post-migration
  (disabled-on-original-label) form. Both tests are green.
- `RelationsView.test.tsx`: added `vi.useFakeTimers()`/`vi.advanceTimersByTime(1000)` (matching
  `loading.test.tsx`'s own convention) since `LoadingText` now withholds its text for the first second.
- `PsychologySkillStudio.test.tsx`, `PortraitView.test.tsx`, `AuthGate.test.tsx`, `AuroraConversation.test.tsx`:
  no changes needed — none of their existing assertions queried a busy-swapped label synchronously.

### How this was verified

- `cd web && npx vitest run` — 143/143 passing (24 files; +1 new characterization test in
  `UnderstandingCorrection.test.tsx`), after every meaningful edit, not just at the end.
- `cd web && npm run build` — clean `tsc -b && vite build` after the full migration set.
- Live run (`dev` profile, H2 + Mock, `.\mvnw.cmd spring-boot:run`, server restarted after the rebuild)
  via `evidence/track-b/scripts/observe-b1-loading-audit.mjs` (run from a temporary copy under `web/`,
  per the by-now-standard workaround for Node's ESM resolver). The script intercepts
  `POST /api/v1/auth/register` and `POST /api/psychology/skills/*/runs` with an artificial ~1.8s delay
  (the local Mock/H2 backend otherwise responds too fast to observe the delayed-loading tiers) and
  confirms, against the real running app: the register button keeps its original `"创建账号"` label and
  is already disabled at <1s (no flash), then shows `"正在创建 ···"` at 1-3s; the skill-run button is
  disabled at <1s with its original label, then shows `"正在整理 ····"` at 1-3s. Screenshots:
  `evidence/track-b/screenshots/b1-loading-audit-01..05*.png`.

### Extending `loading.tsx` itself

Not needed this pass — every hand-rolled instance found (button busy+text, block-level busy text)
mapped cleanly onto the existing `AsyncButton`/`LoadingText` API. No gap was found in the primitive
itself.

### What is still open for B1

- `AuroraApp.tsx`'s domain-hook/store decomposition is still the largest remaining B1 item (unchanged
  by this checkpoint — this pass only touched leaf components' internal busy/error rendering, not
  `AuroraApp.tsx`'s own state shape).
- The "left alone" shared-busy-boolean buttons above are real, but smaller, follow-up items: once
  `AuroraApp.tsx`'s domain hooks introduce per-item busy ids for those flows (mirroring the
  `claimCandidateBusyId`/`detailBusy`/`rollbackBusy` pattern that already exists for other actions),
  those sibling buttons can adopt `AsyncButton` too.
- `LettersInbox.tsx`'s reply-button busy/double-submit gap (no busy tracking at all) is unclaimed
  backlog for whoever next touches the letters domain hook.

## Workstream B1 — fourth checkpoint: first `AuroraApp.tsx` domain-hook decomposition slice

Begins the spec's §5 ask to "decompose `AuroraApp.tsx` into application shell, route pages, domain
hooks/stores and small presentational components." `AuroraApp.tsx` was ~1270 lines with 60+
`useState` hooks and zero domain-hook extraction to date — this checkpoint is deliberately scoped to
**one** cohesive first slice (per the task brief) rather than the whole file: the **Aurora
conversation/session domain**.

| Artifact | What it is |
|---|---|
| `web/src/hooks/useAuroraSession.ts` | New hook. Owns: `sessionId`, `messages`, `draft`/`setDraft`, `mode`/`setMode`, `activeTurnId`, `runtimeSignal`, `wakeIntents`, `wakeBusy`, `returnWhen`/`setReturnWhen`, `notifications`, and the refs (`abortRef`, `activeTurnRef`, `bubbleKeyRef`, `eventIdsRef`, `lastEventIdRef`, `reconnectingRef`, `handleEventRef`). Exposes: `send`, `stop`, `scheduleReturn`/`respondToReturn`/`postponeReturn`/`cancelReturn` (WakeIntent negotiate), `resolveSession`/`replaceFromHistory`/`loadWakeIntents`/`loadNotifications` (session bootstrap pieces used by `AuroraApp.tsx`'s own `bootstrap()`), `refreshNotifications`/`resumeConversation` (used by the mobile-runtime resume effect), `openMobileWakeIntent`, and `resetSession` (used by `logout`/`deleteAccount`). The internal WakeIntent-arrival `subscribeProactive` effect (previously inline in `AuroraApp.tsx`) also moved in, since it only ever touches `notifications`/`wakeIntents`/status. `notifications` state moved in too: every one of its usages in `AuroraApp.tsx` was WakeIntent-arrival-only (the "AURORA RETURNED" card and `respondToReturn`), never a generic app-wide notification surface, so it belongs with WakeIntent negotiate, not as a separate cross-cutting concern. |
| `web/src/hooks/useAuroraSession.test.ts` | New: 14 Vitest cases (`renderHook` + `vi.mock("../api")` mirroring `AuthGate.test.tsx`'s mocking convention) written *before* wiring the hook into `AuroraApp.tsx`, since `AuroraApp.tsx` itself had zero prior test coverage of its own logic (only child components like `AuroraConversation.tsx` had prop-driven tests). Covers: initial state; `resolveSession` (fresh session / WakeIntent-return session / stale-abort via an `isStale` callback); `replaceFromHistory`; `loadWakeIntents`/`loadNotifications`; `send` + simulated `streamAurora` events (`turn.started` → `activeTurnId`/`runtimeSignal`, `bubble.started`/`token`/`bubble.completed` → message content, `turn.completed` → reset); `stop` (aborts, marks the live bubble partial, calls `api.stop`); `setMode`; and all four WakeIntent negotiate handlers. |
| `web/src/AuroraApp.tsx` | Calls `useAuroraSession({ authenticated, skillLocale, onSkillSuggestion: setSkillSuggestion, setStatus })` and consumes its return value everywhere the moved state/handlers used to live directly (`auroraSession.messages`, `auroraSession.send`, `auroraSession.wakeIntents`, etc.) — **zero JSX structure changes**, only identifier rewrites. `bootstrap()` was restructured but not behaviorally changed: it now calls `auroraSession.resolveSession(() => call !== bootstrapCallRef.current)` for the session/WakeIntent-return resolution (preserving the exact original "abort a stale/superseded bootstrap call before firing every other domain's fetch" race guard, via an `isStale` callback rather than losing it to a naive parallel restructuring), then runs the same one 23-way `Promise.all` of every other domain's initial fetch it always did (three of those 23 entries — `replaceFromHistory`, `loadWakeIntents`, `loadNotifications` — are now the hook's own small functions instead of inline `api.*` calls, but the concurrency shape is unchanged). The big mobile-runtime effect (online/offline resume, WakeIntent deep-link, push-token) now delegates to `auroraSession.resumeConversation()`/`refreshNotifications()`/`openMobileWakeIntent` instead of reaching into the (now hook-private) `activeTurnRef`/`replaceFromHistory`/`recover` directly. `logout`/`deleteAccount` call `auroraSession.resetSession()` instead of `setSessionId(null); setMessages([])` inline. `continueSkillWithAurora` (Skill domain, stays in `AuroraApp.tsx`) calls `auroraSession.setDraft(...)`. |
| `evidence/track-b/scripts/observe-b1-decompose-aurora.mjs` | New Playwright script: register → switch mode picker → send a message and observe a real mid-stream Aurora reply with the stop/interrupt control visible → click stop and confirm the exact original status copy → send a second message and let it settle to multiple message bubbles → fill in a WakeIntent return time and schedule it, confirming a real return-card with the exact original copy. |
| `evidence/track-b/screenshots/b1-decompose-01..05*.png`, `observation-log-b1-decompose-aurora.txt` | Screenshots and log from the above. |

### What did NOT move (deliberately out of scope for this slice)

Per the task brief, only the Aurora conversation/session domain moved. `AuroraApp.tsx` still holds
every other domain inline as plain `useState`: memory/starfield (`starfield`, `starfieldDetail`,
`memoryOperations`, correction targets/claims/candidates), capsule/resonance (`capsules`,
`genomeHistory`, `capsulePreview`, sandbox, persona chat, slow letters), connections/letters
(`connectionRequests`, `friends`, `people`, `relations`, letter threads), psychology skills, portrait,
account settings, and the five-space routing/nav state from the prior checkpoint. `AuroraApp.tsx`'s
own `bootstrap()` also still orchestrates loading *every* domain's initial data (not just Aurora's) —
that could not move into the new hook without also moving every other domain's state, so it stays in
`AuroraApp.tsx` and calls into the hook only for the Aurora-specific piece (see above).

### How this was verified

- `cd web && npx vitest run` — 163/163 passing across 26 files (143/24 checkpoint-start baseline + 14
  new `useAuroraSession.test.ts` cases run green in isolation *before* wiring the hook into
  `AuroraApp.tsx`, plus ~6 more contributed by a concurrent B5 PWA checkpoint's own
  `pwaManifest.test.ts` that landed in the same working tree mid-session via commit `856a243`).
- `cd web && npx tsc -b` — clean, zero errors, run after every meaningful edit (not just at the end).
- `cd web && npm run build` — clean; the resulting `src/main/resources/static/app/aurora/**` was
  byte-identical to what the concurrent B5 checkpoint's commit `856a243` had already committed,
  confirming the two independent checkpoints' work composes with zero interaction once both were
  finished (see discoveries in `docs/goal/tracks/track-b-status.yml`).
- Live run (`dev` profile, H2 + Mock provider, `.\mvnw.cmd spring-boot:run` under
  `JAVA_HOME=jdk-21.0.10`) via `evidence/track-b/scripts/observe-b1-decompose-aurora.mjs` (run from a
  temporary copy under `web/`, per the by-now-standard Node ESM-resolver workaround): mode picker
  toggles the `active` class correctly; a real streamed Aurora reply appears mid-turn with the
  stop/interrupt control (`停止回应`) visible; clicking stop mid-stream produces the byte-exact
  original status copy `已停在这里。直接继续说，Aurora 会带着已听见的部分重新理解。`; a second full
  turn settles to multiple message bubbles in the conversation; WakeIntent negotiate produces a real
  return-card with the exact original `因为还有话没有说完...` reason copy and the exact original
  `约好了。你随时可以改期或取消，不需要迁就 Aurora。` status text. A first live-verification attempt
  showed an apparently-failed stream with no reproducible pattern across repeated re-runs regardless
  of a Playwright service-worker setting — traced to the pre-existing, already-documented "403 then
  self-healing CSRF retry" behavior in `api.ts`'s `request()` wrapper being occasionally visible as
  console noise right after a brand-new registration, not a regression from this extraction (see
  `docs/goal/tracks/track-b-status.yml` discoveries for the full methodology note).

### What is still open for B1

- `AuroraApp.tsx` is down from ~1270 to ~900 lines but still holds every other domain's state inline
  — memory/starfield + correction, capsule/resonance, connections/letters, psychology skills,
  portrait, account settings. Suggested next slices (either order is reasonable): (a) memory/starfield
  + correction/claims (a natural pairing since `beginMemoryCorrection` already bridges them), or (b)
  the smaller, more self-contained relations/connections domain as an easier warm-up before the more
  entangled capsule/resonance domain (which shares `sessionId`/`skillLocale`/status with the
  just-extracted Aurora domain and with psychology skills).
- The shared-busy-across-siblings buttons flagged in the third checkpoint (`AuroraSelfSpace`,
  `MemoryStarfield`'s mode tabs, `ResonanceNetwork`'s strategy switcher, `PsychologySkillStudio`'s
  per-run revoke, `CapsuleWorkbench`'s sandbox ratings) remain unclaimed — fixing those needs a
  per-item busy id introduced by whichever domain hook covers that component next.
- No rendering-decomposition pass yet (splitting the five product-space JSX blocks into small
  presentational components) — the spec treats this as a separate later pass from the domain-hook
  extraction, and this checkpoint did not attempt it.

## Workstream B5 — first checkpoint: PWA offline app shell

Fixes B0 finding #6 (backlog item (6) from the original golden-journeys.md list): "no PWA offline
shell exists — a forced-offline reload produces a blank browser network-error page." This is the
first of several B5 slices (install-prompt UX, versioned-update flow, and Capacitor-specific checks
remain open — see `docs/goal/tracks/track-b-status.yml`'s B5 `remaining` field and handoff notes).

| Artifact | What it is |
|---|---|
| `web/vite.config.ts` | Adds the `VitePWA` plugin (`generateSW` strategy). `manifest` comes from `buildPwaManifest()`. `workbox.navigateFallback: "index.html"` serves the cached app shell for any offline navigation (matching `navigateFallbackDenylist: [/^\/api\//]` so `/api/**` is never treated as a navigation-fallback candidate), plus an explicit `runtimeCaching` `NetworkOnly` rule for `/api/**` as a second, belt-and-suspenders guarantee that no API response can ever land in a cache. |
| `web/src/pwaManifest.ts` + `web/src/pwaManifest.test.ts` | `buildPwaManifest()`: a small, pure, testable function returning the web app manifest object, per the task's own suggestion to make the manifest's shape unit-testable rather than an inline literal buried in `vite.config.ts`. 6 Vitest cases pin name/short_name/start_url/scope/display/colors/icons. Colors (`background_color: #211A18`, `theme_color: #C79A68`) are the existing `--surface-canvas`/`--accent-aurora` night tokens from `web/src/styles.css`, not invented. `start_url`/`scope` are `"."` (relative) so the manifest resolves correctly whether served at Spring's `/app/aurora/` or bundled at the Capacitor native origin, matching `vite.config.ts`'s existing `base: "./"` rationale. |
| `web/scripts/generate-pwa-icons.mjs` | One-time, re-runnable icon generator. No sharp/ImageMagick/any image-processing dependency exists anywhere in this repo, so this reuses the already-installed `playwright` devDependency's headless Chromium to draw the existing `ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png` onto a real `<canvas>` and resize it — no new artwork, no new dependency. |
| `web/public/icons/icon-192.png`, `icon-512.png`, `icon-512-maskable.png` | Generated by the script above. The maskable variant scales the source icon to 80% centered on a white background (the icon's own existing card color, not an invented one) so Android's adaptive-icon safe-zone mask never clips the logo. |
| `web/src/main.tsx` | Registers the service worker via `registerSW` from the `virtual:pwa-register` module vite-plugin-pwa generates, guarded by `"serviceWorker" in navigator`. `AuroraApp.tsx` was **not** touched (a parallel agent is mid-refactor on it this session) — see "How the offline fallback actually works" below for why that turned out not to be necessary. |
| `web/src/vite-env.d.ts` | Adds `/// <reference types="vite-plugin-pwa/client" />` so `virtual:pwa-register`'s types resolve. |
| `web/tsconfig.node.json` | Adds `src/pwaManifest.ts` to its file list (so `vite.config.ts`, which belongs to the "node" TS project, can import the same tested function `pwaManifest.test.ts` — part of the "app" TS project — exercises) and adds `"noEmit": true` (needed once a `src/` file was added to this project's list, otherwise `tsc -b` wrote stray `web/src/pwaManifest.js`/`.d.ts` next to the source that the repo's root `.gitignore`'s `web/*.js` pattern does not cover, since that pattern only matches direct children of `web/`, not `web/src/`). |
| `src/main/resources/static/app/aurora/manifest.webmanifest`, `sw.js`, `workbox-abeb32eb.js`, `assets/workbox-window.prod.es5.js`, `icons/*.png` | Built output. The pre-existing asset filenames (`assets/app.js`, `assets/base.js`, `assets/index.css`, `assets/native.js`, `assets/web*.js`) are unchanged — the PWA plugin's generated files are additive, written directly to the `outDir` root by a post-build `workbox-build` step, not through the custom `rollupOptions.output` naming rules, so there is no collision. |
| `evidence/track-b/scripts/observe-b1-pwa-offline.mjs` | New Playwright script, same pattern as `observe-b1-routes.mjs`: register fresh → let the SW install/precache online → inspect the Cache Storage API directly (`caches.keys()`/`cache.keys()`) to confirm what's cached → reload online once (no-regression check) → `context.setOffline(true)` → reload → inspect the rendered page + every `/api/` request's `response.fromServiceWorker()` flag → back online → reload (recovery check). |
| `evidence/track-b/screenshots/b1-pwa-offline-01..04*.png`, `observation-log-b1-pwa-offline.txt` | Screenshots + full findings log from the above. |

### How the offline fallback actually works (no new UI component needed)

Before writing any new "offline page," the existing bootstrap flow was read (`AuroraApp.tsx`'s
`bootstrap()` callback): a failed `api.createSession()`/etc. call already sets `bootstrapError` and
renders `web/src/loading.tsx`'s `ConnectError` component — a warm-theme card titled "没能连上你的内宇宙"
with the underlying error detail and a "重试" (retry) button. B0's actual bug was never a missing UI
state; it was that the browser could not even load the JS/CSS/HTML far enough to *reach* that state
while offline, so it fell back to its own blank native network-error page instead. Once
`vite-plugin-pwa`'s service worker precaches the static app shell and a `NavigationRoute` falls back to
the cached `index.html` for any offline navigation, the exact same `ConnectError` card renders — this
checkpoint did not need to touch `AuroraApp.tsx` at all, matching the task brief's instruction to avoid
that file while a parallel agent works on it.

One real, small gap this surfaced (not fixed this pass, see discoveries in the status YAML): the
`ConnectError` detail line shows the raw browser fetch-failure string verbatim ("Failed to fetch", in
English) rather than a translated "you're offline" message — left as B5/B1 follow-up backlog rather
than edited into `AuroraApp.tsx`/`api.ts` mid a concurrent refactor there.

### How this was verified

- `cd web && npx vitest run` — 149/149 passing in this checkpoint's own files (143 baseline + 6 new
  `pwaManifest.test.ts` cases). A concurrent B1 hook-extraction agent working in the same checkout
  independently added its own `useAuroraSession.ts`/`.test.ts`, so the whole-tree total is higher when
  run together — that additional count belongs to that other checkpoint, not this one.
- `cd web && npm run build` — verified clean twice in this session (`tsc -b && vite build`, output
  written to `src/main/resources/static/app/aurora/`, including the new `manifest.webmanifest`, `sw.js`,
  `workbox-*.js`, and `icons/`, alongside the byte-identical pre-existing `assets/*` files). A later
  build attempt in this same session failed, but exclusively with TypeScript errors inside
  `web/src/AuroraApp.tsx` (confirmed via `npx tsc -b` producing zero errors anywhere else) caused by the
  concurrent agent's own in-progress edit to that file — not a regression from this checkpoint, and left
  untouched per this checkpoint's explicit brief.
- Live run (`dev` profile, H2 + Mock, `.\mvnw.cmd spring-boot:run`, server restarted after the rebuild)
  via `evidence/track-b/scripts/observe-b1-pwa-offline.mjs`, run from a temporary copy under `web/`
  (the by-now-standard workaround for Node's ESM resolver): after one online visit + a ~1.5s settle, the
  Cache Storage API reports **14 precached entries and 0 of them are `/api/` URLs**; a plain online
  reload afterward renders identically to the first load (no regression); forcing the browser context
  offline and reloading produces **no navigation error** (the cached shell resolves), the page is **not
  blank** (body text length 30, not 0), and shows the exact branded `ConnectError` card described above
  ("没能连上你的内宇宙" / "Failed to fetch" / "重试"), rendered over the app's own starfield background —
  a night-themed, on-brand recovery state, not the browser's native error page. During that same offline
  reload, exactly one `/api/` request fires (the bootstrap CSRF call) and Playwright's
  `response.fromServiceWorker()` reports `false` for it — it fails on the network as expected, never
  served from any cache. Restoring the context to online and reloading again recovers to the identical
  body-text length as the very first authenticated load (398 chars both times).

### What is still open for B5

- Install-prompt UX: no `beforeinstallprompt` capture or in-app "install" affordance exists yet;
  installability currently relies entirely on the browser's own default UI.
- Versioned-update flow: `registerType: "autoUpdate"` silently reloads on a new deploy with no
  user-visible copy or confirmation moment (`onNeedRefresh`/`onOfflineReady` callbacks were left at
  their defaults) — risks interrupting an in-progress Aurora conversation.
- Capacitor-specific §9 checks (deep links, secure storage, network/background transitions,
  microphone/push-permission UX) are completely untouched — this slice is web-PWA only.
- Reconnect/resume idempotency for streams and queued actions ("show what is pending") is not
  addressed.
- The truly-never-visited-before offline case (service worker not yet installed, nothing cached) still
  shows the browser's native blank error page — only the "visited online once, then offline" case the
  task specified was verified/fixed.
- The `ConnectError` raw-English-error-string copy gap noted above.
