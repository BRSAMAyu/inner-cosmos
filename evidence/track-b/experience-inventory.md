# Track B — B0 Experience Inventory

> Status: DONE (first pass). Produced by live-running the product, not by re-reading old audits.
> Environment: `dev` profile, H2 (file-backed) + Mock AI provider, Windows PowerShell/Git-Bash, JDK 21.0.10,
> Node 22.20.0. Server started with `./mvnw.cmd spring-boot:run` (via Git-Bash wrapper) after a fresh
> `cd web && npm ci && npm run build`. Base URL `http://localhost:8080`.
> Branch: `codex/track-b-complete-experience`. Observation commit: working tree at the time of this pass
> (see `docs/goal/tracks/track-b-status.yml` for `base_sha`).
> Tooling: Playwright (`@playwright/test` already a devDependency of `web/`), driven by
> `evidence/track-b/scripts/observe-b0-journeys.mjs` (must be run with cwd=`web/` so the `playwright`
> package resolves). Screenshots and raw DOM text dumps are in `evidence/track-b/screenshots/`.

## 1. How this was produced

1. Registered a brand-new account (`b0observer_<timestamp>`) through the **only working registration
   entry point**, the legacy static page `src/main/resources/static/pages/register.html`
   (`/pages/register.html`), because the new React AppShell has no registration form at all (see 3.1).
2. Confirmed the session actually carries over to `/app/aurora/` after registration (it does — cookie
   session, no re-login needed). Screenshots: `01`–`04`.
3. Walked all five spaces (`?space=aurora|cosmos|resonance|letters|me`) at desktop (1440×900) and mobile
   (390×844, `isMobile`/`hasTouch`) viewports on a **zero-content account** to capture true first-run/empty
   states. Screenshots: `10-space-*-desktop.png`, `20-space-*-mobile.png`, plus raw `innerText` dumps
   (`10-space-*.text.txt`).
4. Sent one real message to Aurora (Mock provider) and captured the streaming/thinking/multi-message
   states over ~11s. Screenshots: `30`–`33`, text dump `33-aurora-conversation.text.txt`.
5. Forced two recovery conditions live: full network offline + reload (`40-offline-reload-desktop.png`),
   and expired/cleared-cookie deep link into `?space=cosmos` (`41-expired-auth-desktop.png`).
6. Cross-checked every visual finding against the actual source in `web/src/AuroraApp.tsx` (1292 lines)
   and `web/src/components/*` to confirm root cause rather than guessing from pixels alone.

All findings below are **live-verified** unless explicitly marked "source-reviewed, not re-run live" or
"not yet verified this pass."

## 2. Route / navigation model (as built, not as speced)

- There are **no real client routes**. Everything lives at the single path `/app/aurora/`; the active
  "space" is a `?space=` query-string value read once at mount by `initialProductSpace()`
  (`web/src/components/ProductShell.tsx:11`) and written back with `history.pushState` on tab click
  (`navigateSpace`, `web/src/AuroraApp.tsx:139`). There is no React Router, no per-space code-splitting,
  no deep-link-to-sub-resource (e.g. a specific star, capsule, or letter thread has no shareable URL).
- **All five spaces are mounted simultaneously in the DOM at all times** and toggled with the `hidden`
  attribute (`web/src/AuroraApp.tsx:1119,1180,1203,1228,1239,1251` — five `<div className="product-space"
  hidden={productSpace !== "..."}>` blocks). Switching tabs is instant (no network refetch, state is
  preserved), which is good for the "preserve context when moving between spaces" requirement in
  `对齐文档/11` §4.2 — but it means **every space's data loads on every bootstrap**, regardless of which
  tab the user lands on, and there is only one giant client bootstrap sequence for the whole product.
- The "aurora" space is **not contiguous in the JSX**: its hero/mode-picker/WakeIntent/Self-evolution block
  is written first (`:1119`–`:1178`), then cosmos/resonance/letters blocks are written, and only *after*
  those does a second `hidden={productSpace !== "aurora"}` block appear (`:1239`–`:1249`) containing the
  actual `AuroraConversation` composer. This DOM-order split has a real, observed consequence on mobile
  (see 3.6): the bottom nav and the conversation composer end up separated from the hero by unrelated
  cosmos/resonance/letters markup earlier in source order, which is fragile for future edits even though
  the `hidden` attribute currently keeps the render correct.
- `AuroraApp.tsx` is 1292 lines and owns essentially all client state (60+ `useState` hooks) and all API
  orchestration for every space — this matches `对齐文档/20` §4.3's description exactly; it is not
  exaggerated. `web/src/components/*` are presentational-only; there is no domain-hook or store layer.

## 3. Entry, auth and identity

### 3.1 Registration is on a different, disconnected surface than the product it leads into

- `/app/aurora/`'s login screen (`Login` component, `web/src/AuroraApp.tsx:1268`) renders **only a
  username/password form** with a "登录" (Log in) button. There is **no "create an account" link, no
  register form, and no visual connection** to `/pages/register.html` anywhere in the AppShell.
- `/pages/register.html` is a **legacy static HTML page** (part of the pre-AppShell `/pages/*` system
  called out in `CLAUDE.md` as V0.1/superseded), not a React route. It happens to already use a warm,
  stardust-textured light theme (screenshot `01-register-page-desktop.png`) that is visually *close* to
  the design language but is a **separate implementation** from the AppShell's own login screen (which
  renders in a dark theme at the same wall-clock time — see 3.2).
- A brand-new user who lands on `/app/aurora/` (the URL given in `CLAUDE.md`'s own "run it locally"
  instructions) hits a dead-end login form with no way to discover that account creation lives at a
  different, unlinked path. This is the single biggest concrete gap in the "golden first experience" —
  see `golden-journeys.md` J1.
- Positive: once registration succeeds, `register.html`'s script (`doRegister()`) redirects to
  `/app/aurora/` and **the session cookie carries over correctly** — no forced second login. Confirmed
  live (`observation-log.txt` line 15: "No login form found on /app/aurora/ ... session carried over
  automatically").

### 3.2 Theme inconsistency between the two entry surfaces

- At identical wall-clock time (~17:17 local), `/pages/register.html` rendered in a **warm light/day**
  theme (`01-register-page-desktop.png`) while `/app/aurora/` rendered in a **dark/night** theme
  (`04-aurora-shell-initial-desktop.png`, `10-space-*-desktop.png`). Both are visually on-brand
  (warm palette, no cold-blue), but they disagree on what time-of-day theme currently applies, so a user
  moving from one screen to the other sees an unexplained flip from cream/gold to charcoal/plum. This is
  a concrete instance of the "single design system, not two implementations" gap.

### 3.3 Expired / cleared session — no deep-link preservation, no explanation

- Live test: cleared all cookies, then navigated directly to `/app/aurora/?space=cosmos`.
- Result (`41-expired-auth-desktop.png`): a bare login form, indistinguishable from a first-time visit.
  No "your session expired, log back in to return to 内宇宙" messaging, no memory of `?space=cosmos`, and
  no link back to registration for someone who forgets they never had an account.
- Root cause confirmed in source: `web/src/AuroraApp.tsx:203-214` — on a 401 during bootstrap,
  `setAuthenticated(false)` and `setStatus("请先登录")` are set, but the very next render
  (`if (!authenticated) return <Login .../>`) **replaces the whole `<main>`**, so that `status` string is
  never actually shown to the user. The intended-target space is not stored anywhere before the redirect
  to the login view, so post-login the user always lands back on the default `aurora` space regardless of
  what deep link they followed. This directly contradicts `对齐文档/11` §4.2 ("深链可进入…认证后返回原目标").

### 3.4 Offline reload — no PWA offline shell at all

- Live test: loaded `/app/aurora/?space=aurora` successfully, then forced the browser context fully
  offline (`context.setOffline(true)`) and reloaded.
- Result (`40-offline-reload-desktop.png`): a **completely blank white page** — Chromium's own
  `ERR_INTERNET_DISCONNECTED` network-error page, because there is no service worker, no cached app
  shell, and no web manifest wired up. This directly confirms `对齐文档/20` §4.6's claim
  ("尚未形成可验证的 service worker / 离线缓存 / 安装升级策略") with a live repro, not just an assertion.
  This is the clearest, highest-priority item for workstream B5.

## 4. Space-by-space inventory

### 4.1 Today / Aurora (`?space=aurora`)

Desktop (`10-space-aurora-desktop.png`) stacks, top to bottom, on first load with zero history:
hero + tagline, 5 conversation "mode" pills (倾诉/整理/追问/行动/关系), a "回来约定" (WakeIntent) panel
with an empty-state line and a working negotiate-a-time control, a "AURORA, BECOMING" (Self/Emergence)
card showing `v1` and static copy, then — separately in DOM order (see §2) — the actual conversation
composer with a placeholder ("此刻，你想从哪里说起？") and voice/send controls.

- **Positive, live-verified**: the conversation experience itself is good. Sending a message showed a
  real "正在回应" status badge with an expandable detail line ("理解与表达双核协作 · 关系动作 · 保持连续
  · 把下一步选择权交还用户" — visible on hover/settled in `33-aurora-response-settled-desktop.png`), a
  "停止回应" / "打断并发送" pair of controls while streaming (`31-aurora-thinking-state-desktop.png`), and
  **two distinct, naturally-paced Aurora messages** rather than one wall of text
  (`33-aurora-response-settled-desktop.png`: "我听见这件事对你不是轻轻掠过的那种影响…" followed by a
  second message "我们先不急着把它解释成你哪里做得不好…"). This is a genuine, already-working instance of
  the "multi-message, interruptible" mandate in `对齐文档/09`/`11` and should be preserved, not rebuilt,
  in B1/B2.
- **Gap**: a first-time user must scroll past the WakeIntent panel and the Self/Emergence card before
  reaching the box where they actually talk to Aurora — on mobile this pushes the composer below the fold
  entirely on initial load (see 4.6).
- **Discovery (needs Track A follow-up, not fixed here)**: every full navigation to any `?space=` value
  logs a console `403 POST /api/dialog/session/create` (confirmed with a dedicated network-tap script,
  `observe-403.mjs`, not kept — reproducible via the kept `observe-b0-journeys.mjs`). The conversation
  still works, so this looks like a redundant "ensure session" bootstrap call firing on every full page
  load (all 5 spaces re-bootstrap on every navigation per §2) that 403s once a session already exists for
  the day, rather than a real functional break. It is silent to the user but is exactly the kind of
  duplicated request/status handling `对齐文档/19`/`TRACK-B` calls out as needing a single async model.

### 4.2 Inner Cosmos (`?space=cosmos`)

Desktop (`10-space-cosmos-desktop.png`) stacks three largely independent tools in one scroll:
1. `UnderstandingCorrection` ("如果这不太是你") — before/after correction preview inputs.
2. `MemoryStarfield` ("你的记忆不是档案柜") — time/topic/people mode toggle, a legend explaining
   size/brightness/edge/connection/distance encodings, and the canvas itself.
3. `PsychologySkillStudio` ("PSYCHOLOGY SKILLS") — 3 skill cards plus a **fully expanded** reflection form
   (occasion/feeling/need fields, retention radio, consent checkbox) sitting open by default under the
   skill list, not behind a "start" click.

- **Gap — empty starfield has no guidance**: with 0 memories, the canvas renders as a bare dotted grid
  with "0 颗当前记忆" and the visual-encoding legend, but **no first-run copy or call-to-action** telling
  the user "talk to Aurora and your first star will appear here." Contrast with 4.4's Relations panel,
  which has exactly this kind of empty-state copy — the inconsistency shows the pattern exists elsewhere
  in the codebase but wasn't applied here.
- **Gap — Skill Studio over-discloses by default**: the detailed intake form for "把此刻说得更清楚" is
  visible immediately, fully expanded, on the page a new user reaches by clicking "内宇宙" — before they
  have any memories or portrait data. This reads as capability-listing rather than "结果先行，细节渐进
  展开" (`对齐文档/11` §7.3, §9.2 progressive-disclosure directive).
- Everything else in this space (correction preview copy, skill card copy, risk/consent disclosure text)
  is honest and reasonably well-written — not a content-quality problem, a **density/sequencing** one.

### 4.3 Resonance (`?space=resonance`)

Desktop (`10-space-resonance-desktop.png`, 2912 chars of text — the densest space by far) stacks:
`CapsuleWorkbench` (genome creation wizard: "先确认像不像你，再让别人遇见"), `PlazaDirectory` (a public
directory of **11 pre-existing capsule personas** — 洛哥, 苏格拉底, 庄周, 深夜电台, 安静图书管理员,
关系边界师, 热烈画家, 海边修衣匠, 存在主义猎人, 睡前守灯人, 林澈的回声分身 — each with a name, one-line
persona description, topic tags, and a "开始对话" button), and `ResonanceNetwork` (a second, near-duplicate
rendering of the same 11 personas grouped by matching strategy: 相似共鸣/有意义的互补/成长边界/温和偶遇/
阶段同行).

- **Discovery, not yet resolved**: on a **zero-activity brand-new account**, the Resonance space is
  immediately full of 11 fully-written personas available for "开始对话" with zero explanation of what
  they are (system-provided sample capsules for exploration? seeded demo content that shouldn't exist in
  a clean account? other users' real published capsules?). Whichever it is, showing a dense, fully
  populated directory before the user has authorized or created anything of their own works against the
  "J5 create-and-verify-your-own-capsule" journey and blurs the "golden first experience" story. This
  needs a product decision (flag in `track-b-integration-requests.yml` if it turns out to require backend
  changes) before B1/B2 can design the right first-run state for this space.
- **Content density**: `PlazaDirectory` and `ResonanceNetwork` both render the *same* 11 capsules with
  largely overlapping cards (once by topic-tag directory, once by strategy grouping) plus a long row of
  topic-filter pills (~40 individual pill buttons visible in `10-space-resonance-desktop.png`). On mobile
  (`20-space-resonance-mobile.png`) these pills wrap into roughly ten rows before any capsule card is
  visible — a clear "information architecture, not one page" case per `对齐文档/20` §4.3.

### 4.4 Connections / Letters (`?space=letters`)

Desktop (`10-space-letters-desktop.png`) stacks `PeopleDiscovery` ("主动认识人，但不催促任何关系"),
`RelationsView` ("关系的温度，慢慢看清"), and `LettersInbox` (慢信 tabs: 收到的/寄出的/草稿/往来, plus
connection-request buckets).

- **Positive — good empty-state copy**: `RelationsView`'s empty state reads "还没有从对话里浮现的关系。
  多和 Aurora 聊聊你在意的人，这里会慢慢亮起来。" and `LettersInbox`'s empty buckets say "此刻没有已经
  抵达的慢信。" / "没有新的连接邀请" / "还没有建立真人连接。" — these are exactly the kind of intentional,
  reassuring first-run copy the starfield (4.2) is missing. Good pattern to replicate, not invent new.
- **Data-hygiene discovery — real, user-visible**: the `PeopleDiscovery` list on this fresh account shows
  12 "discoverable people," and several are unmistakably **QA/test artifacts, not real seed personas**:
  `CSRF Runtime` (×3, distinct suffixed usernames), `CSRF Check`, `Header`, `Smoke` (`@handoff_21128`),
  and — critically — **two of my own prior throwaway B0-observer test accounts** (`Bo Observer
  @b0observer_1784366503236` and `@b0observer_1784366587046`) already appear as people a brand-new real
  user could "想认识". This means **every registered account, including automated/test ones, becomes
  globally discoverable by default with no filtering**. In a real demo or local-complete walkthrough this
  would show meaningless bot usernames in a "people who might understand you" list — a concrete trust/
  data-hygiene problem worth flagging for B6 (reproducible demo data) and worth a backend integration
  request (exclude test/service accounts from discovery, or scope discovery more deliberately) rather than
  a frontend-only fix.

### 4.5 Me (`?space=me`)

Desktop (`10-space-me-desktop.png`) stacks `MeSpace` (登录与设备 / 主动回来 / 理解与记忆 / 共鸣与连接
counters + an appearance toggle 跟随时间/白昼/夜色), `PortraitView` ("Aurora 眼中的你" — correctly shows
an honest empty state: "Aurora 还没有形成对你的理解。多和它聊聊，这里会慢慢长出你的轮廓。"), and
`AccountSettings` (导出数据 / 修改密码 / 删除账户).

- Note: per `对齐文档/09` §5's target IA, Portrait ("我最近发生了什么变化") is supposed to live under
  **Inner Cosmos**, but it is currently rendered inside **Me** instead (`web/src/AuroraApp.tsx:1257`).
  This is a real IA mismatch between the target spec and the implementation, not a documentation error —
  worth a deliberate decision in B1 (move it, or update the spec's home for Portrait).
- The appearance control (跟随时间/白昼/夜色) is the one place a user can explicitly override the
  time-of-day theme — useful context for the 3.2 theme-inconsistency finding: users have no way to know
  *why* the theme differs between `/pages/register.html` and `/app/aurora/` because that toggle only
  exists inside the already-authenticated app.

### 4.6 Mobile-specific finding: bottom navigation is not fixed/anchored

- Live-verified on `20-space-aurora-mobile.png` (390×844): the five-space tab bar (今天/内宇宙/共鸣/连接/
  我的) renders **inline in normal document flow**, appearing only after the hero, mode pills, WakeIntent
  panel, and Self/Emergence card have already scrolled by — it is not a fixed/sticky bottom bar as
  `对齐文档/11` §4.1 specifies ("移动端用不超过五项的底部导航"). A first-time mobile user must scroll past
  four unrelated blocks before either reaching primary navigation or the actual message composer (which
  is further below the nav in this DOM-order — see §2's "aurora space is not contiguous" note). This is a
  direct, screenshot-confirmed instance of the doc-20 "旅程像能力陈列" (journey reads as a capability
  showcase) diagnosis, specifically on the highest-value first-run surface.

## 5. Shared primitives already worth keeping

- `web/src/loading.tsx` already implements the exact 3-tier loading rule from `对齐文档/11` §14.3 (never
  show anything under 1s, text-only 1–3s, text + fading dots after 3s, capped at text under
  `prefers-reduced-motion`) plus a shared `ConnectError` (message + retry) and `AsyncButton` (busy-state
  button). This is a real, working start on the "single async/task/error model" `对齐文档/19`/B1 calls
  for — the gap is **consistent adoption** across every component, not invention from scratch. Worth
  auditing in B1 which components still hand-roll their own busy/error UI instead of using these.
- Design tokens are visibly warm/dusk-appropriate everywhere observed — no cold-blue console aesthetic,
  no generic SaaS card grid — matching the "禁止的视觉方向" constraints in `对齐文档/11` §1.2.

## 6. Screenshot manifest

All files under `evidence/track-b/screenshots/`:

| File | What it shows |
|---|---|
| `01-register-page-desktop.png` | Legacy `/pages/register.html`, empty form, light/day theme |
| `02-register-filled-desktop.png` | Register form filled |
| `03-register-result-desktop.png`, `03b-register-after-redirect-desktop.png` | Post-submit, mid-redirect to `/app/aurora/` |
| `04-aurora-shell-initial-desktop.png` | First authenticated view of the AppShell, dark theme, zero data |
| `07-aurora-login-mobile.png` | Mobile login form (fresh browser context, no shared cookies) |
| `10-space-{aurora,cosmos,resonance,letters,me}-desktop.png` (+ matching `.text.txt`) | Each space, desktop, zero-content account |
| `20-space-{aurora,cosmos,resonance,letters,me}-mobile.png` | Each space, 390×844 mobile |
| `30-aurora-composer-filled-desktop.png` | Composer with a real message typed |
| `31-aurora-thinking-state-desktop.png` | Mid-stream: "正在回应", stop/interrupt controls, message arriving mid-token |
| `32-aurora-mid-response-desktop.png`, `33-aurora-response-settled-desktop.png` (+ `.text.txt`) | Settled two-message multi-message reply |
| `40-offline-reload-desktop.png` | Forced-offline reload: blank browser network-error page, no PWA shell |
| `41-expired-auth-desktop.png` | Cleared cookies + deep link to `?space=cosmos`: bare login, no messaging, no return-to-target |
| `observation-log.txt` | Full console/step log from the automated walk |

## 7. Reproduction

```powershell
cd web
npm ci
npm run build
cd ..
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"   # adjust to your install
.\mvnw.cmd spring-boot:run
# in a second shell, once http://localhost:8080/actuator/health is reachable:
cd web
node "..\evidence\track-b\scripts\observe-b0-journeys.mjs"
```

The script must run with `cwd=web/` because it imports the `playwright` package that is only resolvable
from `web/node_modules` (Playwright browsers were already installed in this environment's cache; run
`npx playwright install chromium` first on a machine that doesn't have them cached).
