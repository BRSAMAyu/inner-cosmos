# Expert 5 — Synthesis Critic & Master Plan

> Independent final authority. Read all four expert reports (01-backend-data, 02-ai-prompt,
> 03-frontend-ux, 04-safety-ops), spot-checked every P0/P1 claim against the actual source
> (cited lines re-read), de-duplicated, resolved the contradictions, hunted for cross-cutting
> gaps the four slices missed, and decided holistically what this product must become.
> **Every P0/P1 finding below was re-verified in source.** A few items were re-rated; the
> reasoning is in "Contradictions Resolved."

---

## Verdict (honest: how close to "perfect"?)

**~55–60% of the way to "perfect."** Stronger than a typical prototype, weaker than a shippable
product — and the gap is almost entirely in *enforcement and last-mile wiring*, not in ideas or
effort.

**What is genuinely excellent (do not lose this):**
- **Safety-first ordering is real and tested.** `SafetyService.check` runs synchronously before
  any token on POST and SSE; crisis never streams as free-form consolation; the acute-crisis
  floor (`looksLikeGenuineCrisis`) is enforced symmetrically on the live-LLM, fallback, and
  exception paths and is regression-tested. For a mental-health-adjacent product this is the
  hardest part and it is done right.
- **Concurrency engineering in the right places.** `PersonaChatServiceImpl.tryReserveQuota`
  (conditional UPDATE→INSERT→DuplicateKey), `SessionCloser.runAfterGoodbye` (atomic
  `UPDATE ... WHERE ended_at IS NULL`), `EmotionInsightServiceImpl.insertWithRaceFallback`,
  and `CapsuleRegenerateListener` (`@TransactionalEventListener(AFTER_COMMIT) + @Async` with a
  documented rationale) are textbook patterns. They prove the team *can* do disciplined
  data integrity — which makes the places that don't all the more fixable.
- **Art-directed frontend.** A complete, internally-consistent dark theme, aggressive
  reduced-motion contract, real a11y primitives (skip-link, `:focus-visible`, focus-trapped
  modals, `aria-live`), and 5-entity `IC.esc()` applied even to streamed SSE. This is not
  templated work.
- **Thoughtful AI pipeline bones.** Disciplined `PromptBuilder` chokepoint with curation caps,
  injection-conscious `sanitize()`, robust `StructuredOutputParser` for real model pathologies,
  the IC-EMO two-timescale emotion design, and in-reply anti-repetition machinery. The vision
  is *understood*, not just copied.

**What keeps it from "perfect" — five honest truths:**

1. **The trust/privacy boundary is partly theatrical.** Real crisis *logic* + a placeholder auth
   filter that grants `ROLE_ADMIN` from a header + unauthenticated plaza exfiltration of full
   capsule internals + no PII redaction on the LLM-egress path + a crisis funnel that renders
   *zero* hotline numbers. The P0→P3 privacy *model* is real in the schema; it is **not
   enforced on the read/egress side** in several load-bearing places.
2. **Three signature promises are silent no-ops in production.** "Aurora re-learns who you are
   every 5 turns" (the hook discards its result), gravity time-decay (`daysSinceLastTouched`
   is always 0 — the starfield is frozen at creation-time), and "slow" letters (flat 3-minute
   delivery; `parallaxDistance` stored, never used). The code exists; the wiring does not.
3. **The self-understanding loop (the branch name) is not closed.** Corrections and the 5-turn
   reflection are prompt-only; the portrait only durably updates on goodbye/nightly. RUN-006's
   headline feature is a billed no-op mid-session.
4. **The core loop has real data-integrity holes.** `finish()` is non-transactional with a
   lock-free idempotency guard (can double-fire all 5 listeners); two parallel paths can each
   insert a MemoryCard for the same session with **no unique constraint** to stop them. The
   starfield — the product's signature artifact — can silently corrupt.
5. **There is no real front door.** Every core page silently auto-logs a brand-new visitor into
   the shared demo account, exposing demo's P1 memories/letters. For a privacy-first product
   this is both a clarity failure and a data-exposure smell.

**Bottom line:** the architecture is sound, the patterns are mostly right, the craft is visible.
What's missing is *enforcement* — schema constraints, ownership discipline, a closed
self-understanding loop, a real trust boundary, and wiring up the half-built signature features
that already have code but no effect. None of this requires rearchitecting the core loop.

---

## Confirmed P0/P1 (de-duplicated, verified, owner-domain tagged)

All items below were re-verified against source. Severity is the *synthesized* rating after
resolving overlaps and contradictions. Domain tags: **BE**=backend, **AI**=AI/prompt,
**FE**=frontend, **SEC**=security/privacy, **SAFE**=safety, **DATA**=data-integrity,
**OPS**=ops/reliability.

### P0 — fix before any production exposure

| ID | Domain | Title | Verified evidence | Owner report(s) |
|----|--------|-------|-------------------|-----------------|
| **M-001** | SEC | **P0 IDOR on the Aurora chat core** — client-supplied `sessionId` is read & written with no ownership check across message/message-rich/stream/greeting/settle. Loads a victim's P0 raw dialog into context AND corrupts their memory extraction. `verifyOwnership()` exists but only `DialogController` calls it. | `AuroraChatController.java:70-126` passes `request.sessionId` to `reply`/`replyRich`/`stream`/`settleSession`; `DialogServiceImpl.saveUserMessage:47-64` inserts by supplied sessionId/userId with no `session.userId==userId` check; contrast `DialogServiceImpl.verifyOwnership:118-126` (exists, unused here). | BE-004 / S4-002 |
| **M-002** | SEC/SAFE | **Crisis funnel renders ZERO hotline numbers.** The entire crisis path terminates at a phone-less page. `resources()` returns three sentences with no digits; `safety-harbor.html` only renders a `tel:` link when a regex matches a number, so it never does. **Safety-of-life P0.** | `SafetyServiceImpl.java:54-60` (three text strings, no numbers); `safety-harbor.html` tel-link gated on `/\d{3,4}[-\s]?\d{7,8}/`; `AuroraAgentServiceImpl.blockedReply` sends crisis users here. | S4-006 |
| **M-003** | SEC/SAFE | **Synchronous safety recheck can block for tens of seconds-to-minutes** before a person in distress sees anything. `recheckSync` → JSON-repair retry × FailoverLlmClient iterating all providers × 20–30s timeouts. No per-module deadline for SAFETY_REVIEW. | `SafetyServiceImpl.check:108-109` → `SafetyReviewService.recheckSync` → `StructuredAiService.call` (repair retry :77-90); provider timeouts 20–30s. | S4-007 |
| **M-004** | SEC | **Unauthenticated `GET /api/plaza/capsules` leaks full capsule internals** — permitAll endpoint returns entire `EchoCapsule` entities: `personaPrompt`, `ownerContextNote`, `styleProfileJson`, `contextPreviewJson`, `authorizedMemoryIds`. The P2 "authorized abstract only" layer is not enforced on read. | `SecurityConfig.java:37` permitAll; `PlazaController.capsules:21-24` returns `ApiResponse<List<EchoCapsule>>`; `CapsuleServiceImpl.plazaCapsules:187-191` selectList, no projection. | S4-003 |
| **M-005** | SEC | **PersonaChat feeds the visitor's full private P1 context into a stranger's capsule conversation.** `assemble(userId, null, message, true)` with `includeMemory=true` loads the visitor's top-8 memories, todos, portrait/relationship/baseline, and egresses them to the external LLM under the stranger's capsule prompt. | `PersonaChatServiceImpl.reply:171` `agentContextAssembler.assemble(userId, null, message, true)`; `AgentContextAssembler.assemble:142-176`. | S4-004 |
| **M-006** | SEC | **No PII redaction on the dialog→LLM path.** Raw P0 dialog, recent messages, memory excerpts, portrait, corrections, emotion labels are concatenated verbatim, egressed to MiniMax/DeepSeek/GLM, and stored plaintext in `tb_ai_interaction_log` indefinitely. The only masker is used solely on the capsule-preview branch. | `PromptBuilder.withUserInput:323-328` raw concat; `AiLogServiceImpl.recordDetailed:26-45` stores `requestPrompt`/`responseText` (schema :308-311); masker only in `DataMaskingServiceImpl.previewFromMemory`. | S4-005 / S4-025 |
| **M-007** | DATA | **`DialogFinishedEvent` fired from a non-transactional `finish()`; lock-free idempotency guard; 5 plain `@EventListener` listeners.** Two concurrent finishes can double-fire all 5 listeners; listeners can read half-committed state. | `DialogServiceImpl.finish:82-99` is NOT `@Transactional`; `if FINISHED return` (:90) is lock-free check-then-act; `MemoryExtractListener:17` plain `@EventListener` (contrast `CapsuleRegenerateListener:38` correct `@TransactionalEventListener(AFTER_COMMIT)`). | BE-001 |
| **M-008** | DATA | **Two parallel memory-settlement paths each insert a MemoryCard for the same session — `tb_memory_card` has NO unique constraint on `(user_id, source_session_id)`.** Duplicate cards pollute the starfield and gravity. | `MemoryServiceImpl.extractFromSession:79-108` inserts a card; `MemorySettlementServiceImpl.settleSession` inserts another; schema.sql:119-141 has only FK on source_session_id, no UNIQUE (whereas `tb_emotion_trace` correctly has `uk_emotion_trace_user_session` :176). | BE-002 |
| **M-009** | FE/SEC | **No real front door: every core page silently auto-logs visitors into the shared demo account** — any new visitor reads demo's P1 memories/letters and a real seeded password is broadcast from the browser. | `app.js:341-353 ensureDemoLogin()` → `guestLogin()` (`:355-357`) POSTs `/api/auth/login {demo/demo123}` on every non-login page; `login.html` pre-fills demo creds. | FE-01 / S4-019 |

> **Note on M-010 (the `X-User-Id` header):** Re-rated to **P1**, not P0 — see Contradictions
> Resolved #1. It is a P0-grade *latent risk* but is not currently exploitable to reach
> controllers, because `BaseController.currentUserId` reads `HttpSession`, never
> `SecurityContext`, and no `@PreAuthorize`/method security exists (`grep` confirms zero
> `SecurityContext.getAuthentication` / `@PreAuthorize` in the controller layer).

### P1 — fix before broad user exposure

| ID | Domain | Title | Verified evidence | Owner report(s) |
|----|--------|-------|-------------------|-----------------|
| **M-010** | SEC | **`X-User-Id` header grants full Spring Security auth incl. `ROLE_ADMIN`** — documented auth mechanism trusts an unsigned header. Not currently reachable by controllers (session-based), but `actuator/health when_authorized` is header-satisfiable and the next `@PreAuthorize` makes it catastrophic. | `JwtAuthenticationFilter.java:38-57`; `BaseController.currentUserId:16-22` reads HttpSession only. | BE-003 / S4-001 |
| **M-011** | AI/DATA | **5-turn portrait reflection hook silently discards its result — portrait never learns mid-session.** RUN-006's headline feature is a billed no-op. | `AuroraAgentServiceImpl.java:283` calls `portraitReflection.reflectOnTurn(...)` and throws away the returned `PortraitDeltas`; only `SessionCloser:115-118` and `EmotionBaselineServiceImpl:195` persist deltas. | ai-001 |
| **M-012** | AI | **Mode-aware temperature is computed but never applied — every provider hardcodes 0.72.** `LlmRequest` has no temperature field; SLEEP_REVIEW (0.6) and DAILY_TALK (0.85) run identically. | `LlmRequest.java:6-20` has no temperature field; providers hardcode 0.72. | ai-002 |
| **M-013** | AI/SEC | **The real system boundary is sent as `role:user`; each provider injects its own 1-line English `role:system` persona.** Safety/anti-diagnosis/anti-injection rules sit in the user slot competing with user content. | `PromptBuilder.withSystemBoundary:38-55`; providers send it as role:user and add a minimal English role:system. | ai-003 |
| **M-014** | AI/DATA | **Gravity time-decay is dead code — `daysSinceLastTouched` is always 0 at every call site, including the nightly job that exists to apply staleness.** Starfield is frozen at creation-time ordering. | `GravityServiceImpl.java:16` `exp(-lambda*daysSince)`; every caller passes 0 incl. `NightlyMemorySettlementJob.recalculateGravity:119-121`. | BE-009 / ai-004 |
| **M-015** | AI/DATA | **Daily EmotionTimeline is never auto-aggregated — weekly-review/pattern/spectrum run on empty data.** `aggregateFromTraces` (the good, trace-based aggregator) has zero production callers; `aggregateForDate` only reachable via a manual admin endpoint. | `EmotionTimelineController.java:68` calls `aggregateForDate` (admin-only); `aggregateFromTraces` (:186) has no callers; weekly review defaults to '平静'/empty. | ai-005 |
| **M-016** | AI/SEC | **Proactive pushes to offline users are silently dropped but still logged as sent, burning the daily budget with zero delivery.** The mood-drop nudge fails the exact users it targets. | `ProactiveDeliveryChannel.push` returns silently when no emitter; callers `logEvent(..., sentAt=now)` unconditionally. | ai-006 |
| **M-017** | AI | **User corrections never durably reshape the portrait — the "这不太是我" loop is prompt-only and forgets.** Once corrections age out of the CORRECTION_MAX=5 window the wrong dim is re-derived. | `recordCorrection` writes `tb_user_correction`; portrait updates only via `reflectOnTurn`/goodbye, neither reads `UserCorrection`. | ai-007 |
| **M-018** | SEC | **CSRF globally disabled + cookie-session auth + `SameSite`/`Secure` unset** = cross-site state-change attacks incl. `DELETE /api/user/account`, capsule visibility, letter send, friend accept. | `SecurityConfig.java:31` csrf disabled; no `server.servlet.session.cookie.*` in yml; `WebMvcConfig:30-34` allowCredentials(true)+allowedHeaders("*"). | S4-008 |
| **M-019** | SEC | **No brute-force protection on `/api/auth/login`** — rate limiter explicitly skips `/api/auth/**`; no failed-attempt counter/lockout; combined with hardcoded demo/admin passwords. | `ApiRateLimitFilter.java:60-63` short-circuits `/api/auth/**`; `UserServiceImpl.login` single BCrypt compare. | S4-009 |
| **M-020** | SEC/SAFE | **Crisis detection has known false-negative blind spots** — lethal-means/scene planning (药都准备好了/天台上/烧炭/上吊), English (I want to kill myself/suicide), pinyin/homophones (紫砂/zs), emoji-only. | `CrisisKeywordRule` verbs-only, no means; `DistressSignalDetector` Chinese-only; `SafetyReviewService:190-192` acknowledges the gap. | S4-017 |
| **M-021** | DATA | **`DialogServiceImpl.increment` is `@Transactional` but private self-call → transaction inert; read-modify-write on session counters → lost updates.** | `DialogServiceImpl.saveUserMessage:62` calls `this.increment(...)`; `increment:128-136` annotated `@Transactional` but private (proxy AOP cannot intercept self-invocation); read-modify-write on `tb_dialog_session`. | BE-005 |
| **M-022** | DATA | **Letter state transitions have no optimistic locking / row guard → lost updates and state-machine violations under concurrent transitions.** | `SlowLetterServiceImpl.transition:62-91` selectById→updateById no `@Version`, no conditional UPDATE; concurrent `/read` + scheduler SENT→DELIVERED can both pass validate. | BE-006 |
| **M-023** | SEC | **Capsule boundary IDOR: `GET /api/capsule/{id}/boundary` returns any capsule's privacy config (allowTopics/blockedTopics/visibility) with no ownership.** Sibling `updateBoundary` correctly checks via `getOwnedCapsule`. | `CapsuleController.getBoundary:87-90` no userId; `CapsuleServiceImpl.getBoundary:399-404` by capsule_id only. | BE-010 / S4-020 |
| **M-024** | SEC | **Several session endpoints ignore ownership** — setSessionModel, switchMode, goodbye, safety check/inspect. A user can rebind another user's session to a costlier/no-fallback provider. | `AuroraChatController.setSessionModel:206-215` calls `currentUserId` only to ensure login; `AuroraModeController.switchMode`, `GoodbyeController`, `SafetyController`. | BE-013 / S4-016 |
| **M-025** | FE/SEC | **`IC.api()` is status-code blind and retries ALL methods including non-idempotent POSTs.** Sessions that expire mid-conversation strand the user; transient timeouts re-send create/letter/capsule/todo. | `app.js:6-26` `const json = await res.json()` unconditional, no `res.ok`/status check; default `retries=1` applies to POSTs; mutating helpers pass no retry arg. | FE-02 / FE-03 |
| **M-026** | FE/SEC | **thought-shredder "仅看一次 / DISPLAY_ONCE" silently persists raw vent and leaks it into history + gravity.** Privacy-trust violation in a mental-health feature. | `ThoughtShredderServiceImpl.java:91` sets TRANSIENT then inserts card; `:88` TRANSIENT cards still get gravity; `history():125-128` filters by memory_type only, no status filter. | FE-04 |
| **M-027** | FE | **Three reflection/transparency pages are orphaned (zero inbound links):** thought-shredder (core reflection tool), ai-log (stated product value), ai-dev-history. | `app.js:28-53` nav omits them; no inbound links in `static/`. | FE-05 |
| **M-028** | FE | **Three broken social journeys:** friends→letter `?to=` unread (always lands on no-receiver wall); reply-with-letter saves DRAFT while UI says "已封缄…抵达"; inbox envelope auto-fires defeating the touch-to-open affordance. | `social.html:653` `?to=`; `slow-letter.html:364` reads only `capsuleId`; `inbox.html:663-671` claims delivery but `SlowLetterServiceImpl:147` sets DRAFT; `inbox.html:491` auto-calls `openReadingCeremony()`. | FE-06 / FE-07 / FE-08 |
| **M-029** | FE | **Intended typography only loads on 1 of 35 pages.** The calligraphic identity (Noto Serif SC / Cormorant / LXGW WenKai) is fetched only by index.html. | Only `index.html:9-19` fetches the fonts; grep of `pages/*.html` returns index.html only. | FE-10 |
| **M-030** | DATA/BE | **`UserPreferenceController.setPreferredModel` selects profile by wrong key** (`selectById(userId)` instead of `eq("user_id", userId)`) — mutates the wrong row or nulls for real users. | `UserPreferenceController.java:45` `selectById(userId)`; UserProfile PK is its own id, userId is FK. | BE-022 / S4-015 |
| **M-031** | AI/SAFE | **Hard-boundary refusal is gated on an optional service being non-null** — disabling continuity silently disables identity-violation protection; and the proactive path bypasses SafetyService entirely. | `AuroraAgentServiceImpl.checkHardBoundaries:1349` refusal is inside `if (isBoundaryViolation && continuityService != null)`, continuityService is `@Autowired(required=false)`; proactive path (`AliveDecisionEngine.tick:62-100`) has no SafetyService.check. | ai-011 / ai-013 |
| **M-032** | SEC | **No password-change/reset endpoint; weak password floor (4 chars client / 8 server).** For an AI companion holding P0 inner dialog. | `grep` for changePassword/resetPassword = nothing; `register.html` `< 4`; `RegisterRequest` `@Size(8-128)`. | S4-033 / FE-16 |
| **M-033** | SEC | **Account deletion is hard delete with no re-auth, no soft-disable, no export-first** — with CSRF off, account destruction is one authenticated/forged call. | `UserController.deleteAccount:52-57`; `UserServiceImpl.deleteAccount:286-377` hard-deletes ~25 tables. | S4-031 |

(P1 continues: the BE error-envelope/P1-grade correctness items BE-007/008/011/015/016/017/018/019,
ai-008/014/023, and S4-010/011/012/014/022 are real and verified but condensed above to keep the
master list at decision-grade size; they are carried into the full Master List table later.)

---

## Contradictions Resolved

1. **X-User-Id header: P0 (Expert 4) vs "can't reach controllers today" (Expert 1).**
   **Resolution: P1, with a P0-grade latent-risk flag.** Verified truth: `BaseController.currentUserId`
   (`:16-22`) reads `HttpSession.getAttribute`, **never** `SecurityContext`; `grep` confirms zero
   `SecurityContext.getAuthentication()` or `@PreAuthorize` in the controller layer. So a header-only
   request has no session and is rejected at every controller today — Expert 1 is correct about
   *current* exploitability. BUT it is the *documented* auth mechanism, `actuator/health`
   `when_authorized` IS header-satisfiable (Expert 4 correct), and the very next `@PreAuthorize` or
   SecurityContext read makes it catastrophic. Rating **P1 (latent)** with the strongest "fix before
   any further security work" ordering. Both experts are right about different facets.

2. **Gravity decay: "dead code, every caller passes 0" (Experts 1 & 2) — no contradiction, both
   found it independently.** Verified: every `calculateGravity(` call site passes `0` for
   `daysSinceLastTouched`, including `NightlyMemorySettlementJob.recalculateGravity:121` — the job
   that exists to apply staleness. **Confirmed P1, merged to M-014.**

3. **Demo auto-login: Expert 3 frames it as UX/data-exposure (P1); Expert 4 frames it as security
   (broadcasts a real password).** Both correct, different lenses. **Merged to M-009 (P0)** because
   the compound effect (every visitor silently impersonates demo + reads P1 + a real password is
   shipped in JS) crosses into the privacy-perimeter category the product is built on.

4. **`finish()` event ordering: Expert 1 flags "listeners read uncommitted state."** Nuance
   verified: `finish()` is **not** `@Transactional`, so the event is published with no transaction
   to commit — the "uncommitted read" risk is *moot for finish() itself*. The **real** risks are
   (a) the lock-free double-fire (genuinely P0) and (b) the same 5 listeners run for the
   `MemoryExtractListener`-triggered path where the *publisher* IS transactional (`extractFromSession`
   is `@Transactional:78`), so the AFTER_COMMIT gap is real there. Recommendation stands (switch to
   `@TransactionalEventListener(AFTER_COMMIT)` + conditional UPDATE); **kept as M-007 P0** but the
   evidence is sharpened.

5. **`tb_memory_card` unique constraint: Expert 1 says none exists. Verified:** schema.sql:119-141
   has only an FK on `source_session_id`, no UNIQUE. (Contrast `tb_emotion_trace` which correctly has
   `uk_emotion_trace_user_session`.) **Confirmed M-008 P0.**

6. **"Secure" vs "insecure" persona-chat ownership:** Expert 4 says ownership is correct on
   "highest-traffic letter/persona-chat/dialog paths"; Expert 1 lists persona/session-mode/goodbye
   IDORs. **No contradiction** — letter *access* checks sender||receiver (correct), but
   *session-mode/goodbye/safety/setSessionModel* do not. Re-tagged: M-024 (session-mode family) is
   distinct from the correct letter-ownership.

7. **EmotionTimeline double-LLM (Experts 1 & 2 both found it):** `EmotionTraceListener` and
   `MemoryServiceImpl.createStructuredAssets` both call `analyze()`+`writeTrace` per finish.
   **Confirmed, merged to BE-011/ai-023 in the master list.** The bigger adjacent gap (M-015:
   timeline never auto-aggregates at all) supersedes the cost concern.

8. **FE-09 (admin A/B shape handling):** Expert 3 self-corrected mid-report (it's a silent "暂无"
   lie, not a crash). Verified the guard `(r.data||[]).length` on a truthy object. **Downgraded to
   P3** in the master list (not P1) — annoying, not load-bearing.

9. **Auth filter "trusted" vs "theatrical":** Expert 1 calls auth a "placeholder that ships";
   Expert 4 calls the perimeter "effectively theatrical." These are the same finding from two
   angles. The synthesized truth: **the session path works; the documented/secondary path
   (X-User-Id) and the read-side enforcement (plaza, boundary, persona-chat visitor context) do
   not.** Captured across M-004/005/010/023.

---

## Cross-Cutting Gaps the Four Missed

These are end-to-end journeys or systemic gaps that no single slice-owned expert could see
fully. Each was confirmed by reading across layers.

1. **The "this landed for me" resonance loop is open in one direction only (whole-product gap).**
   RUN-003 shipped capsule resonance *surfacing* (matchScore + reason chips), but there is no
   recipient-side "这封回声真的接住了我" signal that flows back to the sender or into the capsule's
   echoEnergy. Expert 3 noted `reportLetter` gives no closure; Expert 4 noted mute/block/report lack
   closure. The deeper gap: **resonance is measured only by matching, never by receipt.** A
   closed "landed-for-me" micro-signal (a single warm affordance, weight in echoEnergy, a
   count-on-the-capsule) would make the slow-social promise *felt*, and none of the four named it
   as a product feature. *(Cross-cutting: AI + FE + DATA.)*

2. **No unified "Aurora outgoing message" pipeline — every Aurora-originated surface routes
   differently.** Expert 2 caught the proactive bypass (ai-013); Expert 1 caught the error-shape
   divergence; neither traced it to the root: chat reply, proactive, greeting, mode-ack, capsule
   chat, persona chat, and slow-letter guard each apply *different subsets* of {safety check,
   anti-repetition, boundary sanitize, persona consistency, PII redaction}. The cross-cutting gap:
   **there is no single chokepoint where "an Aurora message leaves the system" is enforced.** This
   is why M-031 (hard-boundary gated on optional service) and M-016 (offline push logged as sent)
   even exist — they're symptoms of the same missing pipeline. Building one `OutgoingAuroraMessage`
   gateway retires ~6 findings structurally.

3. **The signature visualizations are decoupled from the signature data (starfield + emotion
   spectrum + portrait).** Expert 1 flagged frozen gravity; Expert 2 flagged fictional emotion
   spectrum; Expert 3 flagged starfield a11y. The cross-cutting truth: **the three signature
   visualizations all render from signals that are stale, frozen, or empty in production**, and
   none of the four unified them as "the product's three windows on the self are all showing
   creation-time fiction." Fixing M-014/M-015/M-017 together (gravity ages, timeline aggregates,
   portrait learns mid-session) is what makes *all three* visualizations go live simultaneously —
   this is the single highest-leverage "make it real" cluster.

4. **No "session lifecycle" is observed consistently across the fan-out (BE + AI + DATA).** Expert
   1 noted goodbye closure is advisory (BE-027); Expert 2 noted the turn-counter/goodbye maps are
   in-memory and cross-session (ai-020/BE-049). The cross-cutting gap: **"finished vs goodbyed vs
   settled vs ended" is implicit**, downstream pipelines key off `sessionId` blind, and a new
   message can be saved against a FINISHED session. A coherent `SessionStarted/Finished(atomic)/
   Ended(goodbye)/Settled` event family — none of the four proposed as a unit — would let every
   listener choose behavior from real lifecycle. This is the structural fix beneath M-007/M-021.

5. **The dev/prod safety surface is not separated — `MockLlmClient` and `/mock-transcribe` and
   `X-User-Id` and `ensureDemoLogin` all run in every profile.** Expert 4 flagged `/mock-transcribe`
   and demo-login; Expert 2 flagged the dead 11-strategy hierarchy. The cross-cutting gap: **there
   is no `@Profile` boundary between "demo/dev scaffolding" and "prod".** A single
   `prod-readiness` profile pass (gate every mock/dev path behind `@Profile('!prod')`, randomize
   seed passwords, force no-fallback in prod) closes M-009/M-010 and the `/mock-transcribe` OOM
   vector at once.

6. **Untested critical paths that static analysis misses (runtime-only):**
   - **No test asserts `resources()` contains a phone number** — the exact contract
     `safety-harbor.html` depends on (S4-039). The crisis funnel can ship phone-less forever.
   - **No negative MockMvc ownership test** anywhere — user A touching user B's session/capsule/
     letter is untested, which is *why* M-001/M-023/M-024 exist (BE-034).
   - **No test asserts gravity *decreases* as daysSince grows** (M-014) — the decay has been dead
     since shipped because no test pins it.
   - **No test asserts a DISPLAY_ONCE shred is absent from history** (M-026) — the privacy promise
     is unverified.
   - **No recheck-latency budget test** (M-003) — a person in distress can wait minutes and CI is
     green.
   These are the "runtime-only bugs static analysis misses" the brief asked for: each is a
   load-bearing contract with zero test pinning it.

7. **The privacy promise is documented (P0→P3) but never *shown* to the user (whole-product gap).**
   Expert 4 proposed a privacy cockpit (S4-042); Expert 3 proposed a privacy-moment in onboarding.
   The cross-cutting gap neither fully closed: **the user has no per-turn "what Aurora knew this
   turn" view and no granular consent**, so the product's core differentiator (your inner cosmos is
   *yours*, layered) is asserted in docs but invisible in UX. This is the trust artifact that turns
   a clever prototype into the product the vision describes.

8. **Capsule "window-shopping" + session resume are missing end-to-end (AI + FE + BE).** The plaza
   modal shows intro/tags/boundary, but the only way to hear a capsule's voice is to start a full
   chat (consuming a daily round); persona-chat sessions are orphaned in sessionStorage with no
   resume surface (FE-18). None of the four united this as: **the social/discovery journey has no
   zero-cost audition and no "my ongoing echoes" home.** This caps the "slow-social" promise.

---

## Path to Perfection

Grouped into (A) Must-Fix, (B) Should-Add, (C) Wow/Differentiate. Items reference master IDs.

### (A) Must-Fix — correctness, security, safety, data integrity

- **Close the IDOR/ownership perimeter structurally (M-001, M-023, M-024, S4-034/035).**
  Introduce one `assertOwnsSession(userId, sessionId)` helper + a reusable ownership guard
  (aspect or `@PreAuthorize`), and route every `{id}` path-variable through it. Ship negative
  MockMvc tests proving user A can't touch user B's session/capsule/letter/belief. *This single
  structural change retires ~8 findings.*
- **Wire real crisis hotline numbers + a hard 3–4s deadline on the safety recheck (M-002, M-003,
  M-020).** Replace `resources()` with region-aware numbers (110/120/12320/010-82951332/400-161-9995/
  988 + international); add a test asserting a phone exists; give SAFETY_REVIEW a
  `CompletableFuture.orTimeout(4s)` with deterministic fallback; add means-planning/English/
  homophone coverage. *Cheapest, highest-impact cluster in the whole report.*
- **Make `finish()` atomic and one-card-per-session (M-007, M-008).** Conditional
  `UPDATE ... WHERE id=? AND status<>'FINISHED'`, publish only if rowsAffected==1; switch the 5
  listeners to `@TransactionalEventListener(AFTER_COMMIT)`; add a UNIQUE on
  `tb_memory_card(user_id, source_session_id)` + upsert. *Protects the starfield — the signature.*
- **Fix the auth trust boundary (M-010, M-018, M-019, M-032).** Remove/gate `X-User-Id`; implement
  signed JWT (jjwt is already a dep); re-enable CSRF + harden the cookie (`Secure`, `SameSite=strict`,
  `HttpOnly`); add a per-username/IP login bucket; add change-password/reset. *Foundation everything
  else rests on.*
- **Project public-safe VOs and redact at egress (M-004, M-005, M-006).** Plaza list returns a
  projection (pseudonym/intro/tags/echoEnergy) — never `personaPrompt`/`ownerContextNote`/`style*`;
  build a minimal visitor context for PersonaChat; add ONE mandatory PII-redaction pass at the
  LLM-egress chokepoint. *Makes the P0→P3 promise real against third-party egress.*
- **Stop the demo auto-login; build a real front door (M-009).** Profile-gate auto-demo-login to
  dev only; make index the public landing; route "进入内宇宙" to login unless a real session
  exists; stop pre-filling demo creds.
- **Make `IC.api` resilient (M-025).** Status-aware branching (401→login redirect), method-aware
  retry (GET only), standard error shape. *Every page's resilience hinges on this.*
- **Make DISPLAY_ONCE truthful (M-026).** Exclude TRANSIENT from history/gravity or don't persist
  raw text. Add the test.
- **Concurrency correctness (M-021, M-022, BE-007).** Atomic counter UPDATE; `@Version`/conditional
  UPDATE on letters; DB unique on capsule-sync PENDING dedup.

### (B) Should-Add — missing core features & journeys

- **Close the self-understanding loop (M-011, M-017).** Persist 5-turn reflection deltas; route
  corrections through `applyDeltas` with a `source='user_correction'` badge so they durably
  reshape the portrait and `reflectOnTurn` treats them as hard priors. *This is the literal
  meaning of the branch name.*
- **Make the three signature signals live (M-014, M-015).** Wire gravity time-decay into the
  nightly job (real `daysSince`); auto-aggregate EmotionTimeline from traces on the
  DialogFinished/nightly path; re-touch bumps gravity. *Makes starfield + spectrum + portrait go
  live at once.*
- **Make "slow" social actually slow + tangible (BE-044, M-028).** Wire `parallaxDistance` into
  `estimated_arrival_at`; implement `SENT→FLYING→DELIVERED` (the FLYING state + column already
  exist); fix the three broken journeys (friends→letter `?to=`, reply-with-letter DRAFT-lies,
  inbox envelope auto-fire).
- **Wire mode temperature through `LlmRequest` (M-012) and send the boundary as `role:system`
  (M-013).** *Foundation for every downstream AI quality claim.*
- **Unified outgoing-message + reply-routing (Cross-cutting #2).** One `OutgoingAuroraMessage`
  gateway (safety/anti-rep/boundary/PII-redact/persona-consistency) for chat/proactive/greeting/
  mode-ack/capsule/persona; one strategy registry so mode actually changes sampling.
- **Session lifecycle events (Cross-cutting #4).** `SessionStarted/Finished(atomic)/Ended/
  Settled` so downstream pipelines choose behavior from real lifecycle.
- **Orphan-page wiring + live notification badges (M-027, FE-21).** thought-shredder/ai-log into
  nav; unified connection home with live unread badges (the RUN-006 notification dead-angle).
- **App-wide typography + one modal component + adopted design tokens (M-029, FE-29/49/53).**
  Load fonts via `mountShell`; route all overlays through `IC.showModal`; cache-bust CSS; adopt the
  dead `--fs-*`/`--ring` tokens. *The foundation work that unblocks consistent polish on all 35 pages.*
- **Safety observability + session-rhythm guardian (S4-026/028).** Wire the dead
  `SessionDurationLimiter`/`TokenBudgetLimiter`/`ResourceRedirectService` (the "no emotional
  dependency" boundary exists only in the prompt today); micrometer counters + a moderator triage
  queue for HIGH/CRISIS.
- **Encryption-at-rest + retention hygiene + a privacy cockpit (S4-012/013/042).** Column/DB
  encryption for intimate text; a retention job that drops raw utterance after N days; granular
  consent toggles + a per-turn "what Aurora knew" view.

### (C) Wow / Differentiate — delight + vision realization

- **A closed "this landed for me" resonance signal (Cross-cutting #1).** A single warm affordance
  on a received echo that flows back into echoEnergy and the capsule's count. Makes slow-social
  *felt*, not just matched. This is the single most on-brand "wow" available.
- **A parallax "in transit" flight animation driven by real delivery distance (BE-044 + FE).** The
  FLYING state and parallax column already exist; an honest "your letter is crossing a small
  cosmic distance" visualization is 90% scaffolded. Signature wow moment.
- **Editable self-understanding UX (ai-037).** A "我的更正 / Aurora 对我的理解" page where the user
  reviews, amends, and retires past corrections — closing the "这不太是我" loop visibly.
  Provenance badges ("you told me this" vs "I noticed this") make Aurora's model auditable.
- **A real agent loop with explicit ability/tool calls (ai vision).** Observe portrait+state →
  decide intent → optionally pull a specific memory / check the emotion timeline → draft →
  self-critique against anti-repetition → emit. Makes the "how Aurora decides" story honest and
  materially raises reply quality.
- **A first-run onboarding journey with a privacy moment (FE-01 vision).** Landing → sign-up →
  empty personal cosmos → guided 3-step tour ("说一句今天最真实的话" → "看它变成一颗星" → "决定
  要不要分享") → a privacy moment teaching the actual P0→P3 promise. The differentiator, taught.
- **A capsule "audition" utterance + "my ongoing echoes" resume home (Cross-cutting #8).**
  Zero-cost pinned first utterance so browsers can hear a voice without spending quota; a resume
  surface for ongoing persona-chat sessions.
- **Proactive safety routing into the harbor (FE vision #6).** Detect rising distress from
  emotion-timeline/shredder and gently surface `safety-harbor.html`; the harbor is currently
  one-way (user must find it).
- **Replayable golden reply-tests (ai-002 vision).** `LlmRequest` carries temperature/topP/seed +
  a deterministic mock pin → voice/safety/robustness regressions caught end-to-end, including
  adversarial golden cases (injection, crisis, mid-JSON truncation).

---

## Unified Prioritized Master List

Condensed to decision-grade severity. Full per-item detail lives in the four source reports;
duplicates are merged with cross-references. Effort: S (<2h) / M (half-day–1d) / L (1–2d+).

| ID | Group | Severity | Title | Evidence | Recommendation | Effort | Domain |
|----|-------|----------|-------|----------|----------------|--------|--------|
| M-001 | A | P0 | Aurora-chat P0 IDOR (read+write victim session) | AuroraChatController:70-126; DialogServiceImpl.saveUserMessage:47-64 | assertOwnsSession in saveUserMessage/reply/stream; negative MockMvc test | M | SEC |
| M-002 | A | P0 | Crisis funnel renders zero hotline numbers | SafetyServiceImpl:54-60; safety-harbor.html tel-regex | Real region-aware numbers + tel-regex test | S | SAFE |
| M-003 | A | P0 | Sync safety recheck can block minutes | SafetyServiceImpl.check:108-109 → recheck → repair retry × failover × 30s | orTimeout(4s) + deterministic fallback; disable repair for SAFETY_REVIEW | M | SAFE/OPS |
| M-004 | A | P0 | Plaza leaks full capsule internals (unauth) | SecurityConfig:37 permitAll; PlazaController:21-24; CapsuleServiceImpl:187-191 | Public-safe VO projection; never serialize personaPrompt/style*/context*/authorizedMemoryIds | M | SEC |
| M-005 | A | P0 | PersonaChat egresses visitor's full P1 context | PersonaChatServiceImpl.reply:171 assemble(..,true) | Minimal visitor context; includeMemory=false for PERSONA_CHAT | M | SEC |
| M-006 | A | P0 | No PII redaction on dialog→LLM path; raw prompt stored | PromptBuilder.withUserInput:323-328; AiLogServiceImpl:26-45 | One redaction pass at LlmClient chokepoint; retention job | M | SEC |
| M-007 | A | P0 | finish() non-tx + lock-free guard; 5 plain listeners | DialogServiceImpl:82-99; MemoryExtractListener:17 | Conditional UPDATE + publish-on-rowsAffected; TransactionalEventListener(AFTER_COMMIT) | M | DATA |
| M-008 | A | P0 | Two paths insert MemoryCard per session; no UNIQUE | MemoryServiceImpl:79-108; MemorySettlementServiceImpl; schema.sql:119-141 | UNIQUE(user_id,source_session_id) + upsert; one authoritative path | M | DATA |
| M-009 | A | P0 | Silent demo auto-login exposes demo's P1 + ships password | app.js:341-357; login.html prefill | Profile-gate demo; real front door; explicit "游客体验" button | M | FE/SEC |
| M-010 | A | P1 | X-User-Id header grants ROLE_ADMIN (latent) | JwtAuthenticationFilter:38-57; BaseController reads HttpSession | Remove/gate header; signed JWT | M | SEC |
| M-011 | B | P1 | 5-turn portrait reflection discards result (RUN-006 no-op) | AuroraAgentServiceImpl:283 | applyDeltas on the 5-turn path; unit test | M | AI |
| M-012 | B | P1 | Mode temperature never applied (all 0.72) | LlmRequest:6-20; providers hardcode | Add temperature to LlmRequest; thread through providers | M | AI |
| M-013 | B | P1 | System boundary sent as role:user; provider injects own | PromptBuilder:38-55; GlmLlmClient/OpenAi | Emit (system,user) pair; boundary as role:system | M | AI |
| M-014 | B | P1 | Gravity time-decay dead (daysSince always 0) | GravityServiceImpl:16; all callers pass 0 incl nightly | Real daysSince in nightly; re-touch bumps; decay-floor test | M | DATA |
| M-015 | B | P1 | EmotionTimeline never auto-aggregates (fictional spectrum) | EmotionTimelineController:68 admin-only; aggregateFromTraces:186 no callers | aggregateFromTraces on DialogFinished/nightly | M | AI/DATA |
| M-016 | B | P1 | Offline proactive push logged as sent, budget burned | ProactiveDeliveryChannel.push; callers logEvent(sentAt=now) | DeliveryResult; persist PENDING sent_at=null; retry sweep; exclude from budget | M | AI/SEC |
| M-017 | B | P1 | Corrections never durably reshape portrait | recordCorrection writes tb_user_correction; portrait ignores it | applyDeltas with source='user_correction'; hard priors | M | AI |
| M-018 | A | P1 | CSRF off + cookie auth + SameSite/Secure unset | SecurityConfig:31; WebMvcConfig:30-34 | CookieCsrfTokenRepository + X-XSRF-TOKEN; cookie hardening; CORS tighten | M | SEC |
| M-019 | A | P1 | No brute-force protection on login | ApiRateLimitFilter:60-63 skips /api/auth/** | per-username/IP bucket4j; lockout | M | SEC |
| M-020 | A | P1 | Crisis false-negatives: means/English/homophone | CrisisKeywordRule verbs-only; DistressSignalDetector zh-only | means/scene rule + English tier + homophone slang | M | SAFE |
| M-021 | A | P1 | increment() private self-call, tx inert, lost updates | DialogServiceImpl:62,128-136 | Atomic UPDATE counter; remove dead @Transactional | S | DATA |
| M-022 | A | P1 | Letter transitions no optimistic lock | SlowLetterServiceImpl.transition:62-91 | @Version or conditional UPDATE WHERE status=? | M | DATA |
| M-023 | A | P1 | Capsule boundary IDOR | CapsuleController:87-90; CapsuleServiceImpl:399-404 | getBoundary(userId,id) via getOwnedCapsule | S | SEC |
| M-024 | A | P1 | Session-mode/goodbye/safety/setSessionModel ignore ownership | AuroraChatController:206-215; Mode/Goodbye/Safety controllers | assertOwnsSession in all four | M | SEC |
| M-025 | A | P1 | IC.api status-blind + retries POSTs | app.js:6-26 | Status branch + 401 redirect + GET-only retry | M | FE |
| M-026 | A | P1 | DISPLAY_ONCE persists raw, leaks to history/gravity | ThoughtShredderServiceImpl:88,91,125-128 | Exclude TRANSIENT from history/gravity, or don't persist raw; test | M | FE/SEC |
| M-027 | B | P1 | Three orphaned reflection/transparency pages | app.js:28-53 nav; no inbound links | Add to nav; link ai-log from settings/admin | S | FE |
| M-028 | B | P1 | Three broken social journeys | social.html:653; slow-letter:364; inbox:663-671,491 | Support ?to=; chain letterSend after reply-draft; remove auto-ceremony | M | FE |
| M-029 | B | P1 | Typography loads on 1/35 pages | index.html:9-19 only | Load fonts via mountShell; font-display:swap | M | FE |
| M-030 | A | P1 | setPreferredModel wrong key (selectById) | UserPreferenceController:45 | eq("user_id",userId); test profile.id!=user.id | S | BE |
| M-031 | A | P1 | Hard-boundary refusal gated on optional svc; proactive bypasses safety | AuroraAgentServiceImpl:1349 (continuityService!=null); AliveDecisionEngine.tick:62-100 | Refuse unconditionally; route proactive through OutgoingAuroraMessage | M | AI/SAFE |
| M-032 | A | P1 | No password change/reset; weak floor | grep resetPassword=∅; register<4; RegisterRequest @Size(8-128) | change-password + forgot/reset; minimal complexity | M | SEC |
| M-033 | A | P1 | Account delete: no re-auth/soft-disable/export-first | UserController:52-57; UserServiceImpl:286-377 | Re-auth + soft-disable + export-first + CSRF | M | SEC |
| M-034 | A | P2 | CapsuleSync PENDING dedup non-atomic, no UNIQUE | CapsuleSyncService:113-128; schema 738-753 | UNIQUE(user_id,capsule_id,status PENDING) + ON DUPLICATE KEY | L | DATA |
| M-035 | A | P2 | Single shared async pool saturates; cross-pool .join() deadlock | ThreadPoolConfig:13; SessionCloser:70,109 | Split named executors; never .join() @Async on same pool | M | OPS |
| M-036 | A | P2 | LLM calls inside @Transactional hold DB connections | CapsuleSyncService.decide:154; SlowLetterServiceImpl.replyWithLetter:122-161 | Move LLM out of tx; persist decision, commit, then call | M | BE |
| M-037 | A | P2 | Duplicate emotion LLM call per finish | EmotionTraceListener + MemoryServiceImpl.createStructuredAssets | analyze() once per finish; one owner | M | AI/BE |
| M-038 | A | P2 | SlowLetter replyWithLetter no state-validation + thread dup | SlowLetterServiceImpl:123-200; schema 502-513 | Gate on original.status; UNIQUE(a,b,capsule) | M | DATA |
| M-039 | A | P2 | Letter status free VARCHAR, no CHECK; FLYING dead state | schema:276; LetterDeliveryJob:73 jumps SENT→DELIVERED | LetterStatus enum + CHECK; implement or remove FLYING | S | DATA |
| M-040 | A | P2 | No idempotency on mutating endpoints; no pagination caps | no Idempotency-Key; list endpoints unbounded | Idempotency-Key on writes; max-limit+cursor on lists | M | BE |
| M-041 | A | P2 | Rate limiter exempts GETs, misses /stream-stage, wrong prefix | ApiRateLimitFilter:65-72 | Rate-limit GETs; fix isAuroraLlm prefixes; 429 in envelope | S | BE |
| M-042 | A | P2 | ASR loads full upload to byte[]; /mock-transcribe no guard | AsrController:32-42,25-30 | Stream multipart to temp file; @Profile('!prod') mock | S | BE/SEC |
| M-043 | A | P2 | AuroraProactiveJob swallows exceptions, marks failed timers fired | AuroraProactiveJob:51,63-75 | Log; mark fired only on success; bounded retry | S | OPS |
| M-044 | A | P2 | 3+ incompatible error envelopes; all business errors→400 | ApiResponse; GlobalExceptionHandler:23-32; ApiRateLimitFilter:104 | One envelope; map codes to 401/403/404/422 | S | BE |
| M-045 | A | P2 | GoodbyeTriggerDetector.lastStrength shared singleton race | GoodbyeTriggerDetector:36/40/43; GoodbyeOrchestrator:64 | Return strength in Detection record; remove field | M | AI |
| M-046 | A | P2 | StructuredOutputParser can't recover truncated JSON | RESPONSE_MAX_TOKENS=1600; findMatchingClose=-1 | Truncation-tolerant repair; metric on truncation-fallback | M | AI |
| M-047 | A | P2 | EmotionTimeline intensityAverage mixed 0-1 vs 0-10 | aggregateForDate prompt 0-1; aggregateFromTraces 0-10 | One scale (0-10); normalize at write sites | S | AI |
| M-048 | A | P2 | Two weekly-review impls, V2 fields computed-not-stored | WeeklyReviewV2Service.save:205-232 | Deprecate V1; persist V2 fields; version column | S | AI |
| M-049 | A | P2 | turnCounter/goodbyeConfirm in-memory, unbounded, cross-session | AuroraAgentServiceImpl:88-89,277-304 | Caffeine TTL or per-session DB; key by (user,session) | M | AI |
| M-050 | A | P2 | Prompt sanitize denylist shallow; user input not fenced/sanitized | PromptBuilder:323-328,388-390 | Role separation (M-013) + fencing + broadened denylist + output validator | M | AI/SEC |
| M-051 | A | P2 | 11-strategy AgentReplyStrategy hierarchy is dead code | no List<AgentReplyStrategy> injection; AuroraCompanionStrategy:23 null userId | Delete, or wire one strategy registry | L | AI |
| M-052 | A | P2 | Prompt-versioning subsystem fully built, nothing reads it | PromptVersion DB+admin UI+rollback+metrics orphan | Resolve segments from PromptVersionService with code seed/fallback | M | AI |
| M-053 | A | P2 | Portrait dim vocabulary mismatch UI vs reflection LLM | portrait.html DIM_LABELS vs PortraitReflectionService:30-33 | Single-source dim enum (Java const + JS map or API) | S | AI/FE |
| M-054 | A | P2 | No distributed lock on @Scheduled jobs (>1 instance unsafe) | LetterDelivery/CapsuleSyncRetry/AuroraProactive/Nightly jobs | ShedLock before horizontal scaling; or idempotent jobs | L | OPS |
| M-055 | A | P2 | Three divergent safety keyword lists (drift) | CrisisKeywordRule/AbuseKeywordRule/LetterSafetyFilterImpl | ONE versioned admin-editable vocabulary all surfaces route through | M | SAFE |
| M-056 | A | P2 | SafetyEvent.messageId never set; HIGH events write-only | SafetyServiceImpl.record; SafetyReviewService.recordRecheck | Thread messageId; micrometer counters + moderator queue | M | SAFE/OPS |
| M-057 | A | P2 | Admin sensitive reads not audit-logged; ai-log latest() unscoped | AdminService.capsules; AiLogServiceImpl.latest:71-74 | AdminActionLog on reads; always scope by caller | M | SEC |
| M-058 | A | P2 | No encryption at rest; H2 blank password | schema ~80 plaintext TEXT cols; application.yml:27 | AES-GCM columns or MySQL TDE; real DB password + externalized key | L | SEC |
| M-059 | A | P2 | Geolocation auto-collected, persisted, egressed (zoom=18) | time-system.js:57-76; GeocodingService:56-107 | In-app opt-in; coarse city zoom; store city not street; clear button | M | SEC |
| M-060 | B | P2 | Starfield keyboard-inaccessible; drawer not focus-trap; not responsive | memory-starfield.html:411-441,292 | Enter/Space on stars; dialog pattern; responsive placement | M | FE/A11Y |
| M-061 | B | P2 | Dashboard serial await chain, no skeleton; loadAiHealth no-op | dashboard.html:357,593-598 | Parallel allSettled; skeletons; wire/delete health chip | M | FE |
| M-062 | B | P2 | safety-harbor weakest a11y of reflection set | safety-harbor.html:225-247,197 | role=list; aria-live; labelled breathing; landmark | M | FE/A11Y |
| M-063 | B | P2 | --fs-*/--ring tokens dead; 3 color-scheme; 3 cursor blocks; triplicate modals | app.css:89-98,110,3787; motion.js dead | Adopt tokens; one modal; dedupe CSS | M | FE |
| M-064 | B | P2 | capsule-chat "结束对话" never closes; no Shift+Enter/streaming | capsule-chat.html:417,202; PersonaChatController no close | close endpoint or honest rename; textarea+Shift+Enter+skeleton | M | FE |
| M-065 | B | P2 | Registration weak validation; no success state | register.html:333-337; login.html hard-redirect | ≥8 mixed + meter; prefill login; shared auth CSS | S | FE |
| M-066 | B | P2 | daily-record prev/next non-functional; todo tabs identical | daily-record.html:259/268; todo.html:112 | Align recordIndex; real week window | S | FE |
| M-067 | C | P2 | "This landed for me" closed resonance signal (none today) | resonance is match-only | Warm affordance → echoEnergy + capsule count | M | FE/AI/DATA |
| M-068 | C | P2 | Parallax flight animation (FLYING state exists, unused) | schema parallax; LetterDeliveryJob jumps | SENT→FLYING→DELIVERED + in-transit viz | L | FE/BE |
| M-069 | C | P2 | Editable self-understanding UX + provenance badges | no corrections list/edit endpoint | GET/PATCH/DELETE /api/aurora/corrections/{id}; "我的更正" page | M | AI/FE |
| M-070 | C | P2 | First-run onboarding + privacy moment | index CTAs bypass sign-in | 3-step tour; teach P0→P3 | M | FE |
| M-071 | C | P2 | Real agent loop (observe→decide→tool→draft→critique→emit) | single structured call today | Optional memory/emotion tool calls; self-critique | L | AI |
| M-072 | C | P2 | Capsule audition utterance + ongoing-echoes resume home | plaza modal only; sessions orphaned | Zero-cost pinned utterance; resume surface | M | FE/BE |
| M-073 | C | P2 | Replayable golden reply-tests (temp/seed pin) | no seed/deterministic mock | LlmRequest seed; adversarial golden suite | M | AI |
| M-074 | A | P3 | Non-admin reads expose prompt templates/A-B/metrics | PromptVersionController GETs no requireAdmin | requireAdmin on 4 reads | S | BE |
| M-075 | A | P3 | Validation shallow; Map bodies; no @Size/@Valid | ChatRequest/CapsuleCreateRequest/LetterCreateRequest | @Size caps + @Valid + typed DTOs + max body size | M | BE |
| M-076 | A | P3 | GlobalExceptionHandler swallows multi-errors, all→400 | handleValidation:28-32; handleBusiness:23-26 | Field→message map; map codes to status | S | BE |
| M-077 | A | P3 | Letter scheduler SENT→DELIVERED bypasses audit log | LetterDeliveryJob:73; transition logs API only | Shared transition helper + reason | S | DATA |
| M-078 | A | P3 | belief recalculate / reportLetter no ownership | BeliefController:52-56; SlowLetterServiceImpl.reportLetter | userId + ownership; rate-limit report | S | SEC |
| M-079 | A | P3 | PromptTemplateRegistry unused; MemoryExtraction dims unused | PromptTemplateRegistry; MemoryExtractAgent.extract | Wire or delete | S | AI |
| M-080 | B | P3 | ai-dev-history stale counts; themes/timeline load all cards | ai-dev-history.html:125; themes.html:105 | Drive from manifest; lazy-load; server limit | S | FE |
| M-081 | B | P3 | Toast region not aria-live; --faint contrast fails; native confirm in aurora-chat | app.js:205-212; app.css:45; aurora-chat.html:1045 | role=status aria-live; darken --faint; use showModal | S | FE/A11Y |
| M-082 | B | P3 | Loading mask comment 6s but 3.5s; stuck masks unrecoverable | app.js:296,312 | Align; retry affordance | S | FE |
| M-083 | C | P3 | crisis copy half-width punctuation; resources ASCII | SafetyServiceImpl:23-25,54-60 | Full-width 。，; copy review | S | SAFE/UX |

---

## Recommended Execution Order

Sequenced into phases. Each phase is independently shippable and mostly ordered by
(safety-of-life → trust boundary → core-loop integrity → make-signature-real → polish/wow).
Effort is rough team-days.

### Phase 0 — Safety-of-life & cheapest wins (do first, ~2–3 days)
**M-002** (hotline numbers + test), **M-003** (safety recheck deadline), **M-020** (crisis
false-negative coverage). *A person in distress must never wait minutes or land on a phone-less
page. This is the cheapest, highest-impact cluster in the entire report.*

### Phase 1 — Trust boundary & privacy enforcement (~4–5 days)
**M-001** (Aurora IDOR), **M-004** (plaza projection), **M-005** (visitor context), **M-006**
(PII redaction at egress), **M-009** (kill demo auto-login / real front door), **M-010** (remove
X-User-Id / signed JWT), **M-018** (CSRF + cookie), **M-019** (login brute-force). *Makes the
P0→P3 privacy promise real and closes the worst disclosure holes.*

### Phase 2 — Core-loop data integrity (~3–4 days)
**M-007** (atomic finish + AFTER_COMMIT listeners), **M-008** (one-card-per-session UNIQUE),
**M-021** (atomic counter), **M-022** (letter optimistic lock), **M-025** (resilient IC.api),
**M-026** (DISPLAY_ONCE truthful), **M-030** (preferred-model key). *Protects the starfield and
the conversation record — the product's signature artifacts.*

### Phase 3 — Make the signature features real (the "wow" core, ~4–5 days)
**M-011** (persist 5-turn reflection), **M-017** (corrections durably reshape portrait),
**M-014** (gravity ages), **M-015** (timeline auto-aggregates), **M-012** (mode temperature),
**M-013** (boundary as role:system), **M-016** (offline proactive PENDING + retry). *This is the
cluster that makes starfield + spectrum + portrait + Aurora's voice all go live simultaneously —
the single highest-leverage "make it real" work, and the literal point of RUN-006.*

### Phase 4 — Close the journeys & unify the pipeline (~4–5 days)
**M-028** (three broken social journeys), **M-027** (orphan pages into nav), **M-029**
(typography app-wide), **M-031** (unconditional refusal + unified outgoing-message pipeline,
cross-cutting #2), **M-024** (session-mode ownership), **M-064** (capsule-chat honesty +
Shift+Enter), **M-067** (closed "landed-for-me" signal). *Realizes the "slow-social" promise and
makes Aurora consistent across every surface.*

### Phase 5 — Hardening, observability, scale-readiness (~4–5 days)
**M-032/033** (password lifecycle + safe account deletion), **M-031/055/056** (one safety
vocabulary + observability + moderator queue), **M-035/036/054** (split pools, LLM-out-of-tx,
ShedLock), **M-040/041/044** (idempotency + rate-limit GETs + one error envelope), **M-058/059**
(encryption-at-rest + geo consent), dead-code cleanup (**M-051/052/079**), the missing tests
(cross-cutting #6). *The difference between a prototype and a product.*

### Phase 6 — Delight & vision realization (ongoing, after stability)
**M-068** (parallax flight animation), **M-069** (editable self-understanding + provenance),
**M-070** (onboarding + privacy moment), **M-071** (real agent loop), **M-072** (capsule audition
+ resume home), **M-073** (replayable golden tests), **M-060/061/062/081** (a11y + skeleton +
tokens). *Turns a correct, safe product into the "AI self-resonance & slow-social" experience the
vision describes.*

---

### Final word

This is a product whose *intent* and *craft* are genuinely excellent and whose *enforcement* and
*last-mile wiring* are not yet production-grade. Roughly: **safety logic 9/10, safety last-mile
3/10; architecture 8/10, data-integrity enforcement 5/10; AI pipeline design 8/10, signature-feature
wiring 4/10; frontend surface 9/10, frontend foundations 5/10; privacy model 9/10, privacy
enforcement 4/10.** The path to "perfect" is not a rewrite — it is Phase 0–3 (safety +
trust-boundary + core-loop integrity + make-the-signature-real), after which this becomes exactly
the thoughtful, warm, boundary-respecting self-resonance product the vision describes.
