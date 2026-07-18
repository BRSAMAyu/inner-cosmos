# Track B — B0 Golden & Recovery Journey Definitions

> These journeys are the acceptance frame for B1–B7. Each step lists the **target behavior** (from
> `对齐文档/09` §4, `对齐文档/11`, and `docs/tracks/TRACK-B-COMPLETE-EXPERIENCE.md` §4), the **current
> observed behavior** (live-verified this pass, see `experience-inventory.md` for screenshots), and a
> **verification status** so later work doesn't have to re-derive what was actually tested vs. inferred
> from source reading.

Verification status legend:
- **LIVE** — driven through the running app this pass and screenshotted/logged.
- **SOURCE** — confirmed by reading the relevant component/controller source, not re-run live this pass.
- **UNVERIFIED** — target behavior only; not exercised or confirmed this pass, flagged for B1+ to test.

---

## J1. Golden first experience

Target (per `对齐文档/09` J1 and `TRACK-B` §4.1): enter safely → tell Aurora something meaningful → see
living multi-message behavior → finish → see a proposed, correctable memory/portrait insight → understand
what happens next. Should complete in under ~10 minutes with no developer explanation needed.

| Step | Target behavior | Current behavior | Status |
|---|---|---|---|
| 1. Discover the product | User arrives at the documented URL (`/app/aurora/`) and can either log in or register from the same surface | `/app/aurora/` shows **only** a login form; there is no register link, no "new here?" affordance. The actual registration form lives at an unlinked legacy URL, `/pages/register.html`. A user following `CLAUDE.md`'s own instructions hits a dead end unless they already know the second URL. **This is the #1 blocker to a golden first experience** — it fails before the journey even starts. | LIVE |
| 2. Create an account | Minimal friction, value-first, no long questionnaire | The legacy register form itself is short (username/nickname/password/confirm) and reasonably fast; theme is warm/on-brand. Acceptable once discovered. | LIVE |
| 3. Land in the product, authenticated | Session carries over from registration with no forced re-login | Confirmed: redirects to `/app/aurora/`, cookie session valid, no login prompt. | LIVE |
| 4. Say something meaningful | Composer is immediately reachable and inviting | On desktop, composer is visible after one scroll past hero/returns/self-card. On **mobile**, composer and nav are both pushed below WakeIntent + Self/Emergence content — not immediately reachable. | LIVE |
| 5. See living, multi-message behavior | Multiple paced messages, visible "thinking"/"composing" state, interrupt/stop available | **Confirmed working**: "正在回应" status, stop/interrupt controls mid-stream, two distinct naturally-sequenced Aurora messages rather than one block. This is a genuine strength already in place. | LIVE |
| 6. Finish and see a proposed, correctable insight | After the conversation, a memory/claim candidate appears for the user to confirm/edit/reject | Not exercised this pass with the Mock provider in a single short exchange — `ClaimCandidateReview` component exists and is wired into the Cosmos space, but whether a single message is enough to produce a claim candidate (vs. requiring more turns) was not observed live. | UNVERIFIED — retest with a longer conversation before B1 sign-off |
| 7. Understand what happens next | Clear next step (e.g., "see this in your starfield", "set a return time") without developer narration | Aurora space has a WakeIntent negotiate control and links toward Cosmos, but nothing in the just-finished conversation explicitly points the user toward "go see your first star" — the connection between "you just talked" and "here's your starfield" is not made explicit in-product. | LIVE (gap observed, no auto-navigation/prompt after first exchange) |

**Net assessment**: steps 4–6 (the AI experience itself) are in noticeably better shape than the doc-20
baseline worried about; the actual blocking failure is structural/discoverability (step 1) and mobile
layout (step 4), not AI quality. B1 should prioritize (a) a single unified entry surface with both
login and register, and (b) mobile-first reordering of the Aurora space so the composer is reachable
without scrolling past WakeIntent/Self content.

---

## J2. Longitudinal return via WakeIntent

Target (per `对齐文档/09` J2, `TRACK-B` §4.2): receive/enter through a WakeIntent → Aurora recalls the
right context → user interrupts/corrects → timeline remains coherent → change appears in Inner Cosmos.

| Step | Target behavior | Current behavior | Status |
|---|---|---|---|
| 1. Negotiate a return time | User can propose "when's good" and Aurora schedules a WakeIntent | UI exists and is live-verified reachable: "什么时候合适" input + "和 Aurora 约好" button in the Aurora space, pre-filled with "明天早上 8:30". Not actually submitted/tested this pass (would create real scheduled state worth cleaning up before demo). | LIVE (UI only) / UNVERIFIED (submission + backend scheduling) |
| 2. Aurora returns at the right time with context | Arrival banner explains why ("你昨天希望我今晚问问结果"), with 正合适/晚一点/不再提醒这类事 actions | Source-reviewed: the `return-arrival` block (`web/src/AuroraApp.tsx:1164-1171`) renders exactly this pattern when a `WAKE_INTENT` notification exists, including a link "回到当时没说完的地方" (`?wakeIntent=<id>`). Not exercised live this pass — requires either waiting real time or manipulating the scheduler/clock, which needs backend cooperation (Track A territory or a dedicated fixture). | SOURCE only |
| 3. Deep link resumes full context | Clicking the arrival link/notification restores the original conversation without re-asking background | The `?wakeIntent=<id>` link exists in source but there is no dedicated route — it's another query param on the same single path. Whether the app actually reads and acts on `wakeIntent=` param was not confirmed in source beyond the link's existence; not exercised live. | UNVERIFIED |
| 4. User interrupts/corrects the returning message | Same interrupt/stop mechanics as J1 step 5 apply | Not exercised in the context of a WakeIntent return specifically (only exercised for a fresh message in J1). Given the composer/stop mechanism is shared, likely fine, but not proven for this exact path. | UNVERIFIED |
| 5. Timeline stays coherent, change reflected in Inner Cosmos | The follow-up conversation and any resulting memory changes show up correctly in the starfield/timeline | Not exercised — requires the above steps first. | UNVERIFIED |

**Net assessment**: J2 is the least-verified journey this pass because it requires either real wall-clock
waiting or backend time control that a frontend-only observation pass can't safely fabricate without
risking bad demo data. **This is the top priority for the next B-track session**: build (or request from
Track A via `track-b-integration-requests.yml`) a way to fast-forward or directly trigger a WakeIntent
arrival in `dev`/`local-complete` so this journey can be driven and screenshotted end-to-end.

---

## J3. Resonance: Genome → Capsule → match → conversation → slow letter

Target (per `对齐文档/09` J5/J6, `TRACK-B` §4.3): review what can be used → compile/preview a Capsule →
discover an explainable match → converse → send/receive a slow letter → retain control over boundaries
and withdrawal.

| Step | Target behavior | Current behavior | Status |
|---|---|---|---|
| 1. Review what can be used | Consent-scope picker showing which memories/claims are eligible | `CapsuleWorkbench`'s "先确认像不像你，再让别人遇见" section is reachable and, on this account, correctly shows "还没有可选的当前记忆" (no memories yet, since this was a zero-content account) — an honest empty state. Not tested with actual memory content this pass. | LIVE (empty state only) |
| 2. Compile/preview a Capsule | Coverage report, sandbox test-chat before publishing | Reachable in the same panel; not exercised end-to-end (needs source memories first — depends on J1 actually producing claims/memories, which is itself UNVERIFIED per above). | UNVERIFIED |
| 3. Discover an explainable match | Strategy-based discovery (Mirror/Complement/Growth Edge/Serendipity/Contextual) with visible reasoning | **Discovery-blocking finding**: on this fresh account, both `PlazaDirectory` and `ResonanceNetwork` are already populated with 11 fully-written sample/seed personas (洛哥, 苏格拉底, 庄周, etc.), grouped exactly by the five target strategies (相似共鸣/有意义的互补/成长边界/温和偶遇/阶段同行). The *mechanism* for explainable multi-strategy discovery is visibly present and reasonably well-copy'd. Whether these are meant to be permanent system-provided "practice" capsules (reasonable) or leaked seed/dev data (a bug) is **not yet determined** and should be a first question for B1/B2, since it changes what the "discover a match" step of this journey should even show a first-time user. | LIVE (mechanism present; provenance of the 11 personas unresolved) |
| 4. Converse with a matched capsule | Same conversational quality bar as Aurora itself, clear "this is an authorized AI capsule, not a real person online" framing | Not exercised live this pass (would require picking one of the 11 personas and running a sandbox/visitor chat) — reachable via "开始对话"/"进入有限但自然的对话" buttons, not clicked. | UNVERIFIED |
| 5. Send/receive a slow letter | Compose with intent/pacing/boundary controls, flight-state visualization, recipient preview/accept/decline | `LettersInbox` reachable in Connections space; empty states are good ("此刻没有已经抵达的慢信"). Compose/send flow not exercised this pass. | UNVERIFIED |
| 6. Retain control / withdrawal | Boundary editor, pause/archive capsule, revoke data-use grant | `CapsuleWorkbench` has boundary-save controls and Me space has "共鸣与连接 → 管理授权"; not exercised (no capsule exists yet on this account to withdraw). | UNVERIFIED |

**Net assessment**: the discovery/matching *presentation* layer (step 3) is more built-out than
`对齐文档/20`'s "匹配仍主要是主题族/词法特征" critique implies purely from a UI standpoint — the strategy
grouping and per-card reasoning text are already there. The **unresolved question of what those 11
personas actually are** (system content vs. leaked seed data) should be answered before B2 designs the
"first visit to Resonance" empty/first-run state, because the two answers lead to opposite UI treatments
(showcase them proudly vs. hide them until the user has their own capsule).

---

## Recovery journeys

| Journey | Target behavior | Current behavior | Status |
|---|---|---|---|
| **Offline** | App shell remains usable (or shows a clear, branded offline state); pending actions queue and show as pending | Forcing the browser fully offline then reloading produces a **blank white browser network-error page** — no service worker, no cached shell, no offline messaging at all. Confirms `对齐文档/20` §4.6 with a live repro. | LIVE |
| **Provider (AI) unavailable** | Aurora conversation degrades gracefully — a visible "recoverable_error" state (per `对齐文档/11` §5.2 table), retry without duplicating the user's message | Not exercised this pass (`dev` uses the Mock provider, which doesn't fail on demand without code changes). `ConnectError`/`AsyncButton` primitives in `web/src/loading.tsx` suggest a retry pattern exists at the bootstrap level, but the in-conversation provider-failure path specifically was not driven live. | UNVERIFIED — needs a way to force a Mock-provider failure or a real-provider timeout in a controlled test |
| **Expired auth** | Deep link is preserved; user is told why they're seeing the login screen; can return to their original target after re-auth | Live-verified gap: clearing cookies and hitting `?space=cosmos` directly produces a **bare, unexplained login form** with the target space silently dropped; user always lands back on default `aurora` space after re-login. No register link either — a user who forgot they have no account is stuck. | LIVE |
| **Denied permission** (push/microphone on native/mobile) | Clear explanation of what was denied and how to recover, without blocking the rest of the product | Source-reviewed only: `requestMobilePush`/`requestMobileMicrophone` handlers exist (`web/src/AuroraApp.tsx` mobile-state code) with status strings like "当前浏览器不使用系统推送；回来约定仍会在应用内出现。" for the web fallback case. Not exercised live (would need a native Capacitor shell or permission-denial simulation, not just a desktop browser). | SOURCE only — real device/emulator test still needed (B5 territory) |
| **Partial stream** (disconnect mid-response, reconnect) | Reconnect resumes from the correct event sequence, no duplicate or lost messages | Not exercised this pass. The SSE reconnect/resume machinery (`replayTurnEvents`, `eventIdsRef`, `lastEventIdRef` in `AuroraApp.tsx`) is visibly designed for this, but was not driven live (would require killing the connection mid-stream, e.g. via CDP network throttling/abort while a real streamed response is in flight). | UNVERIFIED — good candidate for a targeted Playwright test in B1/B2 |
| **Conflicting correction** | When a user's correction conflicts with existing understanding, the impact preview surfaces the conflict clearly before committing | `UnderstandingCorrection` component and its "预览会改变什么" flow exist and were seen in the empty-account state (before/after textareas, disabled preview button with no data yet). Conflict-specific behavior (two claims disagreeing) not exercised — needs an account with actual claim history. | UNVERIFIED |
| **Withdrawn data** | Revoking a Capsule/data-use grant or forgetting a memory propagates visibly (counts change, capsule invalidated, no ghost content) | `AccountSettings`'s "删除账户"/"导出数据" and Me space's authorization-management entry points exist and are reachable; end-to-end withdrawal propagation was not exercised (needs prior data to withdraw). This is squarely the `对齐文档/20` §4.4 gap (derived-data forget propagation) and is more a Track A data-plane question than a B-track UI one, though the UI *entry points* for it are already present and reachable. | UNVERIFIED |

---

## What this unblocks for B1

1. **Highest-leverage, lowest-risk fix**: unify the entry surface so `/app/aurora/` offers both login and
   register (or redirect intelligently), closing the single biggest J1 blocker without touching AI/backend
   code.
2. **Mobile Aurora-space reordering**: move the composer and bottom nav above (or make the nav genuinely
   fixed/sticky, independent of) the WakeIntent/Self-Emergence content so the first-run mobile experience
   doesn't require scrolling past capability displays before saying anything to Aurora.
3. **A decision, not just a fix, on the 11 Resonance personas**: confirm with product intent (or Track A)
   whether they are permanent sample content or leaked seed/dev data, then design the Resonance first-run
   state accordingly.
4. **A dev-safe way to fast-forward WakeIntent arrival** so J2 can actually be driven end-to-end without
   waiting real hours — flag as a `track-b-integration-requests.yml` item if it needs backend support.
5. **A People-Discovery filter for test/service accounts** before any local-complete demo is trusted —
   otherwise every demo will show `CSRF Runtime`/`Smoke`/duplicate observer accounts to a live audience.
6. **A real offline app-shell** (B5) — currently the offline path is a blank browser error page, the worst
   possible state for a demo or real user with a flaky connection.
