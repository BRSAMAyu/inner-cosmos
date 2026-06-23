# Expert 1 — Backend Architecture & Data Layer

> Final synthesized report. Deduplicates and corrects the three sub-inspections
> (Controllers/API boundary, Services/Transactions/Events, Domain models & state
> machines), then adds findings the subagents missed. Every P0/P1 finding was
> re-verified by reading the source directly.

---

## Executive Summary

**What is strong.** The codebase is genuinely well-organized for a prototype of
this scope. There is a single auth/ownership idiom (`BaseController.currentUserId(session)`
/ `requireAdmin(session)`) reused across ~40 controllers; the State pattern for
letters is clean and centralized in `LetterStateRegistry`; `CapsuleRegenerateListener`
correctly uses `@TransactionalEventListener(AFTER_COMMIT) + @Async` and is a model
the rest of the event fan-out should copy; `PersonaChatServiceImpl.tryReserveQuota`
and `SessionCloser`'s atomic claim UPDATE are correctly engineered concurrent-quota
patterns; `LetterDeliveryJob` respects the state machine and retries with backoff;
and `NightlyMemorySettlementJob` paginates and isolates per-user failures. These are
not the patterns of a careless team.

**What is broken — the headline risks.**

1. **Two genuine data-integrity P0s in the core loop.** `DialogServiceImpl.finish()`
   publishes `DialogFinishedEvent` from a *non-transactional* method consumed by
   five `@Async @EventListener` (not `@TransactionalEventListener`) listeners that
   can read the session's messages *before the status UPDATE commits* — and `finish()`
   has a check-then-act idempotency guard with no locking, so two concurrent finishes
   double-fire all five listeners. Separately, two parallel memory-settlement paths
   (`MemoryServiceImpl.extractFromSession` + `MemorySettlementServiceImpl.settleSession`)
   can each insert a `MemoryCard` for the same session because there is **no unique
   constraint** on `tb_memory_card(user_id, source_session_id)`. Together these
   threaten the product's single most important artifact: the user's memory starfield.

2. **Auth is a placeholder that ships.** `JwtAuthenticationFilter` trusts an
   `X-User-Id` header with no signature and grants `ROLE_ADMIN` for any admin id.
   Every controller reads the *session* (not `SecurityContext`), which currently
   contains the blast radius — but the design intent ("replace with real JWT") is
   unfulfilled, and the moment anyone adds `@PreAuthorize` or method security the
   system is wide open.

3. **A cluster of IDOR / missing-ownership gaps** at the API boundary (Aurora
   message/stream accept another user's `sessionId`; capsule boundary is readable
   by anyone; session-model/mode/goodbye/safety ignore ownership) — all fixable with
   one shared `assertOwnsSession` helper.

4. **The "slow" in slow-letters and the "decay" in gravity are both dead code.**
   `parallaxDistance` is stored but never affects delivery (letters arrive in a
   flat 3 minutes), and every gravity call site passes `daysSinceLastTouched=0` —
   including the nightly job that exists *precisely* to apply staleness. The two
   signature qualities of the product (slow social + decaying memory) are nominal.

5. **No idempotency, no pagination caps, no DLQ, no distributed scheduler lock.**
   The system is correct only as a single-instance prototype; the documented
   MySQL-prod / horizontal-scale path is unsafe the moment a second replica starts.

**Bottom line:** the architecture is sound and the patterns are mostly right; what's
missing is *enforcement* — schema-level constraints, transactional/ownership
discipline, and wiring up the half-built features (gravity decay, letter flight,
idempotency) that already have code but no effect.

---

## Strengths

- **One auth/ownership idiom everywhere** (`BaseController.currentUserId` / `requireAdmin`) — easy to audit in a single place; this is why the ownership gaps are findable rather than hidden.
- **Correct concurrent-quota engineering.** `PersonaChatServiceImpl.tryReserveQuota` (conditional UPDATE → INSERT → DuplicateKey → UPDATE) and `compensateQuota` are genuinely well done; `SessionCloser.runAfterGoodbye` uses an atomic `UPDATE ... WHERE ended_at IS NULL` claim; `EmotionInsightServiceImpl.insertWithRaceFallback` recovers from `DuplicateKeyException` on the `(user_id, source_session_id)` unique constraint. These are the templates the rest of the codebase should follow.
- **`CapsuleRegenerateListener`** uses `@TransactionalEventListener(AFTER_COMMIT) + @Async` with a documented rationale — the correct pattern the other five `DialogFinishedEvent` listeners should adopt.
- **Safety-first ordering in the SSE stream:** `recheckSync` runs synchronously before any chat token is emitted; crisis is never streamed as free-form consolation (`AuroraAgentServiceImpl` ~329–351). Thoughtful and correct.
- **Clean State pattern for letters:** each `LetterState` is a single-responsibility `@Component`, `LetterStateRegistry.validate` is the single chokepoint. `LetterDeliveryJob.advanceWithRetry` re-reads fresh status and refuses illegal transitions.
- **Nightly settlement is paginated and isolates per-user failures** (`BATCH_SIZE=200`) — the IC-DATA-001 fix is real.
- **Session-fixation protection** in `AuthController.login` (invalidate + new session) is correct.
- **PersonaChat exposes an authoritative server-side quota endpoint**, not a client-trusted turn count.
- **Gravity formula is defensively clamped** (log(1+x), Math.max floors) — cannot produce NaN/Infinity/negatives, confirmed by `GravityServiceTest`.

---

## Findings

Severity legend: **P0** = data loss / security hole in the core loop, fix before any prod use. **P1** = serious correctness/security, fix next. **P2** = real bug, schedule. **P3** = polish/maintainability. **polish** = UX/refinement.

### P0

| ID | Severity | Category | Title | Evidence (file:line) | Recommendation | Effort |
|----|----------|----------|-------|----------------------|----------------|--------|
| BE-001 | P0 | data-integrity | `DialogFinishedEvent` fired from a non-transactional `finish()`; 5 `@Async @EventListener` listeners can read uncommitted state and the event can double-fire | `DialogServiceImpl.finish()` is **not** `@Transactional` (DialogServiceImpl.java:81-99); idempotency guard `if FINISHED return` (line 90) is a lock-free check-then-act; listeners are plain `@EventListener` (MemoryExtractListener, EmotionTraceListener, TodoExtractListener, GravityRecalculateListener, CapsuleSuggestionListener) — contrast CapsuleRegenerateListener.java:36-39 which correctly uses `@TransactionalEventListener(AFTER_COMMIT)` | (1) Make `finish()` `@Transactional(rollbackFor=Exception.class)` and flip status with a conditional `UPDATE ... WHERE id=? AND status<>'FINISHED'`; publish the event only if `rowsAffected==1` (fixes both atomicity and double-fire). (2) Switch all five listeners to `@TransactionalEventListener(phase=AFTER_COMMIT)` so they read committed state. | M |
| BE-002 | P0 | data-integrity | Two parallel memory-settlement paths can each create a `MemoryCard` for the same session — duplicate cards, fragments, todos, polluting the starfield & gravity | `MemoryServiceImpl.extractFromSession` (line ~77-108) inserts a MemoryCard for `sourceSessionId`; `MemorySettlementServiceImpl.settleSession` (line ~73-118) inserts another for the same session; `tb_memory_card` has **no unique constraint** on `(user_id, source_session_id)` (schema.sql:119-141 only indexes user_id). Neither path checks for an existing card. | Unify on one authoritative settlement path, OR add a guard/upsert in both: query by `(user_id, source_session_id)` and reuse. **Add a DB unique index** on `tb_memory_card(user_id, source_session_id)` and `INSERT ... ON DUPLICATE KEY UPDATE`. Pick one method as authoritative. | M |

### P1

| ID | Severity | Category | Title | Evidence (file:line) | Recommendation | Effort |
|----|----------|----------|-------|----------------------|----------------|--------|
| BE-003 | P1 | security | Authentication is a placeholder: `X-User-Id` header is trusted as identity with no signature and grants full `ROLE_ADMIN` | `JwtAuthenticationFilter.java:38-57` parses `X-User-Id`, loads the User, and sets a fully authenticated Spring Security `Authentication` (with `ROLE_ADMIN` for admin ids) with **no token, no signature check**; class comment line 18-22 admits "replace with actual JWT validation." **Exploit-surface correction:** all controllers read the *session* via `BaseController.currentUserId` (BaseController.java:16-22), so a header-only request has no session and is rejected — today the spoof cannot reach controllers. But `SecurityConfig` trusts this filter, it is the documented auth mechanism, and any future `@PreAuthorize`/method-security or SecurityContext read makes it instantly exploitable. | Implement real JWT (jjwt is already a dependency): `AuthController` issues a signed JWT; the filter validates signature + expiry. Gate or delete the `X-User-Id` path behind `@Profile('!prod')` until then. Ensure the filter can never grant admin purely from a header. | M |
| BE-004 | P1 | security | Cross-user message injection: Aurora endpoints never verify dialog-session ownership before persisting / streaming | `AuroraChatController.message` (70-82), `messageRich` (84-87), `stream` (102-111) call `reply`/`replyRich`/`stream` with the caller's `userId` and `request.sessionId`; `AuroraAgentServiceImpl.replyRich` → `dialogService.saveUserMessage` (DialogServiceImpl.java:47-64) inserts a message with the supplied `sessionId`/`userId` and **never verifies `session.userId == userId`**. `DialogServiceImpl.finish` (87-89) and `verifyOwnership` (117-126) *do* check ownership — the helper exists but is unused here. Net effect: a logged-in user can POST `/api/aurora/message` with another user's `sessionId` and inject messages, pollute their memory extraction, and fire `DialogFinishedEvent` under the victim. | Call `dialogService.verifyOwnership(userId, sessionId)` (it already exists) at the top of `saveUserMessage` and at the entry of `reply`/`replyRich`/`stream`, before any persistence or LLM call. | S |
| BE-005 | P1 | data-integrity | `DialogServiceImpl.increment` is `@Transactional` but invoked via private self-call → transaction silently inert; read-modify-write on session counters → lost updates | `saveUserMessage` (DialogServiceImpl.java:46-64) calls `this.increment(...)` (line 62); `increment` (128-136) is annotated `@Transactional` but is a **private method on the same bean** — Spring proxy AOP cannot intercept self-invocation, so the annotation is dead. It does `selectById` + `updateById` (read-modify-write) on `tb_dialog_session` with no version/optimistic lock; two concurrent replies in one session overwrite each other's `messageCount`/`tokenEstimate`. | Replace read-modify-write with an atomic `UPDATE tb_dialog_session SET message_count=message_count+1, token_estimate=token_estimate+? WHERE id=?`. Remove the misleading `@Transactional` on the private method (or extract to a separate bean). | S |
| BE-006 | P1 | data-integrity | Letter state transitions have no optimistic locking / row guard → lost updates and state-machine violations under concurrent transitions | `SlowLetterServiceImpl.transition` (62-91) is `@Transactional` but does `selectById` then `updateById` with no `@Version` (BaseEntity.java:7-12 has only id/createdAt/updatedAt) and no conditional UPDATE; it validates against the in-memory snapshot (line 74) then writes (line 81) unconditionally. Concurrent `/read` + scheduler `SENT→DELIVERED`, or a double-click `/reply`, can both pass `validate` and diverge. `LetterDeliveryJob.advanceWithRetry` re-reads fresh status (good) but the API path does not. | Add `@Version` to `SlowLetter` (MyBatis-Plus optimistic locking) OR issue a conditional `UPDATE ... SET status=? WHERE id=? AND status=?` and treat 0 rows as a concurrent-modification error. Re-validate against the stored status, not the snapshot. | M |
| BE-007 | P1 | architecture | `CapsuleSyncService` PENDING-dedup is a non-atomic check-then-insert with no DB unique constraint backing it | `onPortraitOrRelationshipChanged` (CapsuleSyncService.java:113-128) does `findByUserCapsuleAndStatus(PENDING)` and inserts if null; under the concurrent triggers this event exists to absorb (portrait deltas + memory extract + nightly baseline), two threads can both see null and both insert. `tb_capsule_sync_queue` (schema.sql:738-753) has only non-unique indexes. The "anti-storm" comment (line 112) is not enforced at the DB. | Add a unique constraint on `(user_id, capsule_id, status)` (PENDING) via a generated column or partial-index trick, and use `INSERT ... ON DUPLICATE KEY UPDATE`; or fold the check-then-insert into one atomic upsert. | L |
| BE-008 | P1 | consistency | Three+ incompatible error response shapes break the API contract | `ApiResponse.ok` returns `{success,code,message,data}` (ApiResponse.java:9-16); `GlobalExceptionHandler.errorMap` (61-68) returns `{error,message,status,timestamp}`; `ApiRateLimitFilter` (104) returns `{error,message,retry_after}`; `AuroraSelfController` returns hand-built `{error}`/`{status}` bodies (lines 36-179). The only `ApiResponse.fail` call in the codebase is CapsuleController.java:61. Frontend must handle ≥3 error shapes. | Standardize on one envelope: `GlobalExceptionHandler` emits `ApiResponse.fail(code,message)` with the correct HTTP status; route the 429 through it; convert `AuroraSelfController` to `ApiResponse`. | S |
| BE-009 | P1 | bug | Gravity time-decay term is dead code: every call site (incl. the nightly settlement that should apply it) passes `daysSinceLastTouched=0` | `GravityServiceImpl.calculateGravity` (line 9-17) implements `exp(-lambda * daysSinceLastTouched)` decay, but **every caller passes literal `0`**: MemoryServiceImpl ~97 & ~353, MemorySettlementServiceImpl ~114 & ~560, ThoughtShredderServiceImpl ~88, MockDataInitializer ~405, and most damningly `NightlyMemorySettlementJob.recalculateGravity` (line 119-121) — the job that exists to re-stale old cards. `GravityServiceTest` documents the intended decay (365-day-old item <10% gravity) but no production path exercises it. Gravity drives starfield order, capsule suggestions (>1.1), and Aurora context selection. An unresolved 2-year-old card ranks identically to one touched today. | Compute `days = ChronoUnit.DAYS.between(card.lastTouchedAt.toLocalDate(), LocalDate.now())` in `recalculateGravity` and `adjustImportance`; pass the real value. Add a unit test asserting an old card's gravity drops after a nightly run. Consider a decay floor so important-but-old cards aren't fully buried. | M |
| BE-010 | P1 | security | IDOR: capsule boundary readable by any authenticated user | `GET /api/capsule/{id}/boundary` (CapsuleController ~87-90) calls `capsuleService.getBoundary(id)` with **no userId**; `CapsuleService.getBoundary(Long capsuleId)` (interface ~29) and impl (~400-404) query by `capsule_id` with no ownership filter — while sibling `updateBoundary` (~92-97 → impl 407-412) correctly verifies ownership via `getOwnedCapsule`. Any authenticated user can enumerate capsule ids and read every other user's privacy config (allowTopics/blockedTopics/visibility). | Change `getBoundary` to `getBoundary(userId, capsuleId)` and gate on `getOwnedCapsule` (or a plaza-visibility check) before returning; pass `currentUserId(session)` from the controller. | S |

### P2

| ID | Severity | Category | Title | Evidence (file:line) | Recommendation | Effort |
|----|----------|----------|-------|----------------------|----------------|--------|
| BE-011 | P2 | data-integrity | Duplicate LLM cost: `EmotionTraceListener` and `MemoryServiceImpl.createStructuredAssets` both run a full emotion-analysis LLM call + `writeTrace` for the same session on every finish | EmotionTraceListener.onDialogFinished (47-48) and MemoryServiceImpl.createStructuredAssets (~215-216) both call `emotionInsightService.analyze()` then `writeTrace` for the same `(userId, sessionId)`. `writeTrace` upserts (only one row survives, with race-fallback), but the LLM call doubles token spend per finish. | Run `analyze()` exactly once per finish: let `createStructuredAssets` be the sole writer and make `EmotionTraceListener` a no-op/fallback, or vice-versa. | M |
| BE-012 | P2 | consistency | `TodoExtractListener` creates duplicate, unlinked todos independently of the memory-card pipeline | TodoExtractListener (38-56) keyword-matches every user message and inserts TodoItems with **no `source_memory_card_id`**, no dedup; `MemoryServiceImpl.createStructuredAssets` (~218-227) and `MemorySettlementServiceImpl.settleSession` (~152-173) insert richer, linked todos. A single session can yield todos from both paths; re-finish duplicates them. | Delete `TodoExtractListener` (the memory path is richer and linked) OR gate it to fire only when no card will be created. Make todos idempotent per `(memory_card_id, taskName)`. | S |
| BE-013 | P2 | bug | `AuroraChatController.setSessionModel` / `AuroraModeController.switchMode` / Goodbye / Safety ignore session ownership | PUT `/api/aurora/session/{id}/model` (AuroraChatController 206-215) calls `currentUserId` only to ensure login, then `modelRouter.setSessionPreference(sessionId, provider)` with no ownership check — a user can rebind another user's session to a more expensive / no-fallback provider. Same gap: `AuroraModeController.switchMode` (44-58), `GoodbyeController` (26-33), `SafetyController.check/inspect` (26-35). | Introduce a shared `assertOwnsSession(userId, sessionId)` helper (in `BaseController` or a `SessionOwnershipService`) and call it in all four places plus the message/stream paths (BE-004). | M |
| BE-014 | P2 | scalability | No distributed-lock guard on `@Scheduled` jobs — unsafe the moment the app scales to >1 instance | `LetterDeliveryJob` (fixedRate 60s), `CapsuleSyncRetryJob` (fixedDelay 60s), `AuroraProactiveJob` (5min), `NightlyMemorySettlementJob` (cron 0 0 2 * * ?) all rely on single-instance `@Scheduled`; no `@SchedulerLock`/ShedLock; docker-compose ships one replica but the design treats prod as scalable. Horizontal scaling → letters delivered N×, sync retried N×, proactive ticks N×, nightly settlement per-user N×. | Add ShedLock (JdbcTemplate/Redis) to all four jobs before any horizontal scaling; document the single-replica contract; or make each job idempotent under concurrent execution (LetterDeliveryJob's re-check pattern is the template). | L |
| BE-015 | P2 | maintainability | The single shared `taskExecutor`/`aiExecutor` (4/8/100, AbortPolicy) is the only async pool for LLM-heavy listeners AND SSE streaming — saturation silently drops memory/emotion work | ThreadPoolConfig.java:13: core 4, max 8, queue 100, AbortPolicy. This pool runs the 5 `DialogFinished` listeners (some do LLM calls), `SessionCloser`, `CapsuleRegenerateListener`, SSE drips, and the `streamStage` TTL sweep. Under modest concurrent load the queue fills and `RejectedExecutionException` silently loses memory extraction / emotion traces for some users. | Split into named executors: listeners IO pool, a separate bounded pool for blocking LLM calls, SSE executor. Use `CallerRunsPolicy`/larger queue for critical memory/emotion listeners so they're never rejected. | M |
| BE-016 | P2 | bug | `SessionCloser.runAfterGoodbye` is `@Async` with no executor qualifier and `.join()`s a CompletableFuture on the pool thread — classic pool deadlock risk | SessionCloser.java:70 (no qualifier → default pool), line 109 `summarySvc.summarize(...).join()` blocks a pool thread on another `@Async` future targeting the same pool. If the pool is saturated, all 8 threads block on `.join()` waiting for tasks that can never be scheduled. | Don't `.join()` across `@Async` boundaries on the same pool. Make `summarize` synchronous within the closer's transaction, or route one side to a different executor; add a timeout guard. | S |
| BE-017 | P2 | architecture | Long-running LLM calls held inside `@Transactional` boundaries, tying up HikariCP connections | `CapsuleSyncService.decide` (154, `@Transactional`) calls `regenerateOne` (200) → `regenerator.regenerate` (LLM) + `notify` inside the tx. `SlowLetterServiceImpl.replyWithLetter` (122-161, `@Transactional`) calls `guardAgent.allow(letterBody)` (LLM) at line 131 *before* any insert. A slow/timeout LLM call holds a DB connection for the whole window. | Move LLM calls out of the transaction: persist the decision in-tx, commit, then run `regenerateOne`/`guardAgent` in a short separate tx. Standardize transactionality of `regenerateOne` across callers. | M |
| BE-018 | P2 | data-integrity | `SlowLetterServiceImpl.replyWithLetter` thread resolution is a check-then-insert with no unique constraint → duplicate threads | `resolveThread` (~169-200) find-or-create with no unique constraint on `(participant_a, participant_b, capsule_id)` (schema.sql:502-513 has only a non-unique `idx_thread_participants`). Two concurrent first-replies between the same pair create two `LetterThread` rows. | Add a unique constraint on `tb_letter_thread(participant_a, participant_b, capsule_id)` with normalized `a<b` ordering; catch `DuplicateKeyException` and re-select, mirroring `insertWithRaceFallback`. | M |
| BE-019 | P2 | data-integrity | `MemoryServiceImpl.updateImportance` / `archiveCard` / `acceptDailyRecord` / `editDailyRecord` lack `@Transactional` and do unlocked read-modify-write | updateImportance (~345-355), archiveCard (~357-366), acceptDailyRecord (~376-384), editDailyRecord (~394-405): `selectById` → `updateById` with no tx, no optimistic lock → lost gravity/importance under concurrency. `updateImportance` also fails to publish `CapsuleSyncTriggerEvent` (so importance changes don't propose a sync). | Add `@Transactional(rollbackFor=Exception.class)` to all mutating MemoryService methods; use atomic UPDATE or optimistic locking for importance edits; publish `CapsuleSyncTriggerEvent` after importance changes. | M |
| BE-020 | P2 | bug | `AuroraProactiveJob` swallows all exceptions silently and marks failed timers as fired (permanently dropped) | run() catch (line 51) is empty "Log and continue" with **no log call**; private-timer loop (63-75) same empty catch, and on exception sets `t.firedAt=now` + `updateById` (72-73) so a persistently-failing timer is marked fired and never retried — a user's `alive_internal` content is silently dropped after one failure. | Log the exception in both catch blocks; mark `firedAt` only on successful push, so the next tick retries (with a bounded attempt count). | S |
| BE-021 | P2 | missing-feature | No idempotency support on mutating endpoints — client retries double-execute the core loop | No controller accepts/validates an `Idempotency-Key`. Expensive side-effecting ops — `POST /api/aurora/message`, `/message-rich`, `/stream-stage`, `/api/letters/draft`, `/api/capsule/create-from-memory`, `/api/daily-record/weekly/generate`, `/api/aurora/settle` — double-execute on retry: duplicate memory cards, letters, settlements. `SessionCloser` has an atomic guard for the scheduler (IC-DATA-002) but the HTTP surface has none. | Accept `Idempotency-Key` on write endpoints; store `(key, userId, endpoint, response)` in a short-TTL table/cache; return the cached response on replay. Prioritize `/message`, `/settle`, `/letters/draft`, `/capsule/create-from-memory`. | M |
| BE-022 | P2 | bug | `UserPreferenceController.setPreferredModel` selects profile by wrong key (`selectById(userId)` instead of by `user_id`) | PUT `/api/user/preferred-model` (~42-53) does `userProfileMapper.selectById(userId)`. UserProfile's PK is its own id; `userId` is a FK. `UserController.profile` (~30-32) and `AuroraChatController.emotionAwarenessEnabled` (169-171) correctly query `eq("user_id", userId)`. `selectById(userId)` only works accidentally when `profile.id == user.id` (seeded demo/admin); for a real user it returns null → "user not found" or updates the wrong row. | Query by `user_id` (`QueryWrapper.eq("user_id", userId)`). Add a test where `profile.id != user.id`. | S |
| BE-023 | P2 | bug | Rate limiter exempts all GETs and misses the streaming POST path | `ApiRateLimitFilter` doFilter (69-72): GETs are unlimited → `GET /api/dialog/session/{id}/messages`, `/api/memory/starfield`, `/api/capsule/{id}/boundary`, `/api/aurora/self/*` (expensive LLM/DB reads) are unbounded. `isAuroraLlm` (65-68) matches `/api/aurora/chat` which **doesn't exist** (real paths: `/message`, `/message-rich`, `/stream`, `/stream-stage`, `/greeting`); `/stream-stage` (a POST that stages context) falls through to the generic 60/min bucket. | Rate-limit GETs (separate read buckets). Fix `isAuroraLlm` prefixes to the real paths. Return the 429 in the unified envelope (BE-008). | S |
| BE-024 | P2 | bug | `AsrController` loads the entire audio upload into a heap `byte[]` (OOM vector) and `/mock-transcribe` echoes the client hint with no dev-only guard | POST `/transcribe` (32-42): `file.getBytes()` fully materializes the upload; concurrent uploads → OOM, bypassing multipart streaming. `/mock-transcribe` (25-30) transcribes `hint.getBytes()` — echoes client text as "ASR output" with no profile guard, so a misconfigured prod silently returns attacker-controlled text. | Stream the multipart to a temp file (`file.transferTo`) and feed an `InputStream`; gate `/mock-transcribe` behind `@Profile('!prod')`; add an `audio/*` content-type allow-list. | S |
| BE-025 | P2 | bug | `AuroraProactiveController.dismiss` is a stub that silently does nothing — defeats the proactive-greeting UX and quiet-hours | POST `/api/aurora/proactive/{id}/dismiss` (53-58): the `{id}` is captured but unused; body returns `ApiResponse.ok(null)`; comment admits "we'd persist dismissed_at... For now we just acknowledge." Aurora re-greets after every `/check` regardless of dismissal. | Persist a per-user `dismissed_at`/`greeting_cooldown_until`; have `/check` honor it. Remove or repurpose the misleading `{id}`. | S |
| BE-026 | P2 | data-integrity | Letter status stored as free `VARCHAR(32)` with zero DB-level or enum-level validation | schema.sql:276 `status VARCHAR(32)` no CHECK; SlowLetter.java:14 plain String; `LetterStateRegistry.validate` only checks source/target in the map, never asserts target is a *known* state; `MockDataInitializer.insertLetter` (~506-524) sets status from an unvalidated String. A typo ("ARICHIVED", trailing space) persists and throws on the *next* transition. Invariant enforced lazily, never at write time. | Add a CHECK constraint (status IN (...)) to schema.sql and/or validate target against `states.keySet()` before writing; centralize status strings in a `LetterStatus` enum. | S |
| BE-027 | P2 | missing-feature | No `SessionEndedEvent` / session-state observability — goodbye closure is advisory, not enforced | `SessionCloser` ends the session and silences proactive 8h, but `DialogServiceImpl.create`/`saveUserMessage`/`AuroraAgentServiceImpl.produceReply` never check `session.status`; a new message can be saved against an ENDED session, and the `@Async` pipelines key off `sessionId` with no awareness of lifecycle. | Add a status check in `saveUserMessage`/`produceReply` (reject or auto-open a new session when FINISHED/ENDED). Emit a `SessionEndedEvent` distinct from `DialogFinishedEvent` so downstream pipelines can skip ended sessions. | M |

### P3

| ID | Severity | Category | Title | Evidence (file:line) | Recommendation | Effort |
|----|----------|----------|-------|----------------------|----------------|--------|
| BE-028 | P3 | consistency | Non-admin read endpoints expose prompt templates, A/B stats, and metrics | `PromptVersionController` GETs `/versions` (41-44), `/variant` (59-62), `/performance` (75-78), `/low-performing` (80-83) have no `requireAdmin`; SecurityConfig marks `/api/**` authenticated, so any regular user reads all prompt-template contents (incl. system prompts), variant definitions, success/latency metrics. Write-side (`/create`,`/rollback`,`/toggle`,`/record-metrics`) correctly gates. | Add `requireAdmin(session)` to the four reads (pass `HttpSession`); expose only *active* content to non-admins via the existing `/active` endpoint. | S |
| BE-029 | P3 | consistency | Duplicate `@RequestMapping("/api/aurora")` across two controllers + incoherent grouping | AuroraChatController.java:38 and AuroraMemoryController.java:13 both map `/api/aurora`; disjoint sub-paths work today but a future collision is easy. The `/api/aurora/*` surface is split across 6 controllers and AuroraChatController owns `/modes`, `/mood`, `/rhythm-check`, `/greeting`, `/settle` — a grab-bag mixing conversational/orchestration/memory/analytics. | Consolidate sub-paths under clear controllers (chat-only, move `/mood`→EmotionTimeline, `/modes`→AuroraMode, `/rhythm-check`+`/settle`→dedicated). Document prefix ownership. | S |
| BE-030 | P3 | bug | `AuroraSelfController` leaks raw exception messages and bypasses `ApiResponse`; dead null-guards | Lines 150/163/176 catch generic `Exception` and return `Map.of("error", e.getMessage())` — can expose DB driver text/SQL/paths, contradicting GlobalExceptionHandler's intent. Lines 46/68/94/115 `if (userId==null||userId<=0) return 401` are dead (BaseController throws first). | Throw `BusinessException` and let `GlobalExceptionHandler` format uniformly; remove dead guards; return `ApiResponse<SelfStatementVO>`. | M |
| BE-031 | P3 | consistency | Validation is shallow; no input size limits; many `Map<String,Object>` bodies | `ChatRequest` has `@NotNull`/`@NotBlank` but no `@Size` (multi-MB POST accepted). `CapsuleCreateRequest` has **no** annotations; `LetterCreateRequest.letterBody` `@NotBlank` only. `CapsuleController.create`/`previewFromMemory`/`updateContext`/`boundary` take `@RequestBody` with no `@Valid` and untyped Maps → `ClassCastException` deep in the service instead of a clean 400. | Add `@Size` caps to all free-text DTO fields; add `@Valid` everywhere; replace Map bodies with typed validated DTOs; configure a global max request body size. | M |
| BE-032 | P3 | consistency | `ApiResponse` lacks error factory / typed builder; uses public mutable fields; no pagination wrapper at controller layer | `ApiResponse` has public fields + only `ok`/`fail`; controllers do `ApiResponse.<Void>ok(null)` to acknowledge mutations (verbose; can't distinguish empty-success from not-found). `PageResult` exists in common/ but is unused by controllers — starfield/messages/inbox can grow unbounded. | Add `ApiResponse.empty()`/`accepted()` factories and a paginated variant; enforce a max-limit + cursor on list endpoints (`/api/memory/starfield`, `/api/letters/inbox`, `/api/dialog/.../messages`); add timestamp+requestId to the envelope. | S |
| BE-033 | P3 | bug | `GlobalExceptionHandler` swallows multiple validation errors and maps all business errors to 400 | `handleValidation` (28-32) returns only `getAllErrors().get(0)` — one error per round-trip. `handleBusiness` (23-26) **always** returns 400 even for `UNAUTHORIZED`/`NOT_FOUND` — breaks client auth-retry (AuroraSelfController's manual 401s are a workaround). | Map `BusinessException.code` to correct status (UNAUTHORIZED→401, FORBIDDEN→403, NOT_FOUND→404, SAFETY_BLOCKED→403/422); return all validation errors as a field→message map. | S |
| BE-034 | P3 | missing-feature | No API versioning and no centralized ownership-enforcement annotation | Every controller hard-codes `currentUserId`/`requireAdmin`; no `@PreAuthorize`, no method security, no `@OwnerOnly` interceptor — which is *why* the ownership gaps (BE-004/010/013) exist. No controller-level test that user A can't touch user B's resources. | Adopt Spring Security method security or a custom `@OwnerOnly` interceptor pairing a path resourceId with a service owner lookup; add negative MockMvc tests for cross-user access. | M |
| BE-035 | P3 | bug | `BeliefController.recalculate` and a few writes skip ownership | POST `/api/belief/{beliefId}/recalculate` (52-56) passes no `userId`; `beliefExtractService.recalculateStrength(beliefId)` operates on any user's belief. Idempotent/read-model, but lets one user trigger expensive recomputation of another's beliefs and sets the precedent that ownership is optional. (Other BeliefController reads correctly scope by `currentUserId`.) | Pass `userId`; have `recalculateStrength` verify the belief belongs to the caller (or restrict to admin). | S |
| BE-036 | P3 | bug | `DialogServiceImpl.finish` does not persist status atomically with idempotency re-check (subsumed by BE-001 but tracked separately for the guard) | finish() (81-99) not `@Transactional`; `if FINISHED return` (90) lock-free check-then-act → two concurrent finishes both publish. | Folded into BE-001's conditional-UPDATE fix (publish only if `rowsAffected==1`). | S |
| BE-037 | P3 | maintainability | `CapsuleSyncService.retryFailed(Long queueId)` is public and unguarded despite the "no authz needed" comment | retryFailed (274-278) comment claims the sweep selects only user-owned rows, but `CapsuleSyncRetryJob.retryFailedSyncs` (34-46) selects by status/nextRetryAt/attemptCount — **not by user**; the `retryFailed(Long)` overload is public and trusts the caller. Any future controller/misroute invoking it directly regenerates any user's capsule with no authz. | Make `retryFailed(Long queueId)` package-private; force all external entry through the userId-bearing overload. | S |
| BE-038 | P3 | consistency | Letter scheduler-driven `SENT→DELIVERED` bypasses the `LetterStatusLog` audit trail the API path writes | `SlowLetterServiceImpl.transition` (83-89) writes a LetterStatusLog; `LetterDeliveryJob.advanceWithRetry` (73-75) sets `fresh.status="DELIVERED"` via `updateById` with **no log insert** — the most common transition leaves no audit. `transition` logs a fixed `reason="API transition"` regardless of endpoint. | Route the scheduler through a shared transition helper (system operator + `reason="SCHEDULED_DELIVERY"`); pass the operation name as reason. | S |
| BE-039 | P3 | bug | State graph has asymmetric terminal/recovery semantics, a missing draft-edit transition, and an unreachable `FLYING` state | DRAFT has no self-edit path (no update method in SlowLetterServiceImpl — a flawed draft can only be SENT or ARCHIVED). `SentState.next()` includes FLYING (FlyingState.java:8) but **no controller and no scheduler produces FLYING**; the scheduler jumps SENT→DELIVERED directly (LetterDeliveryJob.java:73) — FLYING is dead. (Parallax field exists; see BE-044.) | Add a draft-edit transition; either implement FLYING (SENT→FLYING when arrival imminent, FLYING→DELIVERED when due) or remove it + SentState's edge to it. | S |
| BE-040 | P3 | consistency | `replyWithLetter` creates a reply without validating the original letter's state — replies possible from DRAFT/SENT/BLOCKED/ARCHIVED originals | replyWithLetter (123-161) checks only that the caller is the receiver (128-130) and passes the guard (131-133); never consults `stateRegistry` or `original.status`. Threading tests seed originals as `READ` (implying reply is a READ-state privilege) but prod lets a receiver reply to DELIVERED/DECLINED/BLOCKED/DRAFT/SENT. Contrast `transition(...,"REPLIED")` which routes through `validate`. | Gate `replyWithLetter` on `original.status` ∈ allow-set (at least READ) via `stateRegistry`; align the /reply and /reply-with-letter preconditions. | S |
| BE-041 | P3 | consistency | Timestamp side-effects in `transition` are brittle/incomplete — flying/declined/blocked have no timestamp; sentAt mis-set | transition (77-80) stamps sent/delivered/read/repliedAt only on those exact targets; FLYING has no field; DECLINED/BLOCKED have no entity timestamp (only LetterStatusLog.created_at, which the scheduler bypasses — BE-038); MockDataInitializer.insertLetter (~517) sets sentAt for all seeded letters incl. DELIVERED/READ. | Add declinedAt/blockedAt/archivedAt (or a single stateChangedAt); standardize sentAt semantics; consider deriving all timestamps from LetterStatusLog. | S |
| BE-042 | P3 | maintainability | `LetterState.next()` returns a new `Set` per call; graph built piecemeal with no startup self-validation | DraftState…ArchivedState each return a freshly constructed `Set.of(...)`; `LetterStateRegistry` (14-18) builds a code→state map but never verifies targets are registered, graph is connected, no orphans. FLYING (BE-039) is exactly such an orphan and nothing flags it. No parameterized test for the full transition matrix. | Hold each `next()` in a `static final` constant; add a registry self-test (all targets known; reachable from DRAFT; no dead states) at startup/as a test; add a parameterized `(from,to)` matrix test. | S |
| BE-043 | P3 | missing-feature | `LetterStateException` thrown but legal next-states never exposed to clients — UI can't render valid actions | transition (74) throws on illegal moves; LetterController (29-62) returns a generic error with no legal-next info; no endpoint to query "what can I do next?" — frontend must hardcode the matrix (drift risk). | Expose `GET /api/letters/{id}/transitions` (or include `nextStates` in the letter VO) backed by `LetterStateRegistry`. | S |
| BE-044 | P3 | ux | "Slow" letters aren't slow: `parallaxDistance` is stored but never used; delivery is a flat 3 minutes | SlowLetterServiceImpl sets `plusMinutes(3)` for draft reply and original; `parallaxDistance` (SlowLetter.java:15) stored but never factored into `estimated_arrival_at`; LetterDeliveryJob delivers any SENT with past arrival immediately. The core "slow social" promise is nominal. | Wire `parallaxDistance` into `estimated_arrival_at` on DRAFT→SENT (arrival = sentAt + parallaxDistance days). Decide intent: if letters should take days, the 3-minute arrival breaks the vision. At minimum make parallaxDistance mean something. | L |
| BE-045 | P3 | missing-feature | No retry/dead-letter queue for memory/emotion/capsule async failures — transient DB error loses a session's memory permanently | CapsuleSync caps at MAX_ATTEMPTS=3 then stays FAILED forever (CapsuleSyncService 286-289); LetterDeliveryJob leaves undeliverable letters in SENT; AuroraProactiveJob silently drops timers; `@Async` listeners (e.g. EmotionTraceListener 49-51) log but never retry/surface. A user whose memory extraction fails has no way to know or re-trigger. | Introduce a unified dead-letter/outbox table for async fan-out (memory-extract, emotion-trace, capsule-sync) with a retry-sweep job, user-visible failure indicators, and a "re-process this session" control. | M |
| BE-046 | P3 | consistency | `PersonaChatServiceImpl` persists `session.dailyLimit` but `reply()` re-derives from the capsule each turn — stored value is misleading dead data | submit() (111-113) persists `dailyLimit` from `capsule.conversationLimitPerDay`; reply() (145, 245-251) ignores it and re-derives via `resolveDailyLimit(capsule)`. If the owner lowers the limit between creation and a later reply, the stored value is stale and unused. | Either drop `session.dailyLimit` (always derive at reply time) or honor it as the frozen-at-creation limit. Don't persist a value the enforcement path ignores. | S |
| BE-047 | P3 | consistency | `AuroraAgentServiceImpl.streamStage` TTL sweep blocks an aiExecutor thread for 60s per stage; unbounded map growth | stageStreamContext (~414-424) puts token→request and schedules removal via `aiExecutor.execute(()->{Thread.sleep(60000); remove})` — one pool thread per stage for 60s (feeds BE-015), blocks shutdown ≤60s. No upper bound on `streamStage` if a client spams `/stream-stage`. | Replace sleep-removal with `ScheduledExecutorService.schedule` or a Caffeine `expireAfterWrite` cache (bounded); cap `streamStage` size. | S |
| BE-048 | P3 | maintainability | Gravity weights are hardcoded magic numbers with no config, no normalization, unbounded inputs → scale-dependent inversion | GravityServiceImpl (10-14): alpha/beta/gamma/delta/lambda hardcoded; `recurrenceCount`/`triggerCount` are unbounded counts, not normalized 0-1, so `beta*recurrence` with recurrence=100 contributes 25, dominating clamped intensity (max 4). With importance~default, the trigger term can exceed the intensity term, inverting the intended weighting. | Externalize weights to `@ConfigurationProperties`; normalize/log-scale recurrence/trigger before weighting; property-test that intensity stays dominant across input ranges. | S |
| BE-049 | P3 | consistency | `AuroraAgentServiceImpl` turn counter is an in-JVM `ConcurrentHashMap` with a racy check-then-act reset | produceReply (277-279): `turnCounter.merge(userId,1,sum)` then `if (n%5==0) turnCounter.put(userId,0)` — shared across the JVM; two concurrent turns for the same user (overlapping SSE+POST, or proactive greeting + reply) both merge on the same key; both can hit `n%5==0` and fire `portraitReflection`, or one thread's `put(0)` erases another's increment. Same for `goodbyeConfirmCount` (88, 299-304). | Persist the turn counter per session (atomic increment like PersonaChat quota) or scope per `sessionId`; gate portrait reflection on a durable per-session count; remove the racing `put(0)`. | M |
| BE-050 | P3 | consistency | `PersonaChatServiceImpl.reply` ai-unavailable heuristic conflates "remote unavailable" with "blank-but-real reply" → user message deleted, upstream tokens burned uncompensated | The ai-unavailable detection (~212-214) treats `reply == null || reply.isBlank()` as unavailable and compensates + `deleteById(userMessage.id)` (~223). A genuinely-successful LLM call returning all-whitespace is compensated + deleted — the user's message vanishes and they aren't charged for a turn that consumed upstream tokens. | Distinguish `REMOTE_UNAVAILABLE` (compensate) from a blank-but-real reply (keep the charge, show a gentle "let me try again"); compensate only on explicit `riskFlags REMOTE_UNAVAILABLE`. | S |

### polish

| ID | Severity | Category | Title | Evidence (file:line) | Recommendation | Effort |
|----|----------|----------|-------|----------------------|----------------|--------|
| BE-051 | polish | ux | SSE endpoints emit no heartbeat; Aurora stream uses GET for sensitive text and hard-coded `Thread.sleep` | `/api/aurora/stream` (102-111) and `/api/proactive/stream` (ProactiveSseController 23-27) send no `: ping` comments → long idle proactive streams killed by proxy/nginx 60s read timeout. Aurora stream hard-codes `Thread.sleep(220)` between segments (AuroraAgentServiceImpl ~387), burning an executor thread. `/stream` takes `message` + `sessionId` as GET query params (103-106) → user text in URL/logs. | Add periodic SSE heartbeat comments on long-lived streams; move the stream message body to the staged-token POST (`/stream-stage` already exists); replace `Thread.sleep` with a smaller/configurable inter-segment delay or remove it. | S |

---

## Vision Gaps — what this area SHOULD become

The architecture is mostly *present*; the gaps are about making the product's
signature promises **real** and making the system **safe enough to scale**.

1. **Make the memory starfield a single, durable, two-timescale signal.** Today a
   session can produce 0–2 cards via competing paths, gravity never decays, and
   there is no DLQ. The vision: one MemoryCard per session (unique constraint +
   upsert), gravity that *visibly* decays nightly (`daysSinceLastTouched` wired in,
   starfield dimming/glow reflecting staleness), and a dead-letter/outbox so a user
   can see and re-trigger a failed extraction. This is the product's heart and it is
   half-built.

2. **Make "slow social" actually slow and tangible.** Wire `parallaxDistance` into
   `estimated_arrival_at` and implement the two-stage flight `SENT → FLYING →
   DELIVERED` (the FLYING state and parallax column already exist). A parallax
   "in transit" animation driven by real delivery distance is a wow feature that is
   90% scaffolded and 0% wired.

3. **Real authentication and a declarative ownership layer.** Replace the
   `X-User-Id` placeholder with signed JWTs issued by `AuthController`, and add a
   reusable `@OwnerOnly` interceptor (or `@PreAuthorize`) so every `{id}` read/write
   is owner-checked *by default* — closing the entire IDOR cluster (BE-004/010/013/035)
   structurally instead of one endpoint at a time. Ship negative MockMvc tests proving
   user A can't touch user B's dialogs/capsules/letters/todos/beliefs/memory.

4. **One event discipline for all post-dialog fan-out.** Every listener should be
   `@TransactionalEventListener(AFTER_COMMIT) + @Async` on a *named, purpose-built*
   pool (memory/emotion on a never-reject pool; LLM-blocking on a separate bounded
   pool; SSE on its own). Never `.join()` an `@Async` future on a pool thread. This
   single change retires BE-001/011/015/016 and makes the system observably correct
   under load.

5. **A coherent session-state machine with events.** `SessionStarted` /
   `SessionFinished` (atomic) / `SessionEnded` (goodbye) / `SessionSettled` — so
   downstream pipelines choose behavior from real lifecycle instead of keying off
   `sessionId` blind. Today "finished vs goodbyed vs settled" is implicit and
   nothing consistently observes it.

6. **Idempotency + pagination + distributed locking as platform primitives.**
   `Idempotency-Key` on all mutating Aurora/letter/capsule/settlement endpoints; a
   max-limit + cursor on every list endpoint (starfield, inbox, messages); ShedLock
   on every `@Scheduled` job before horizontal scaling. These are the difference
   between a prototype and a product.

7. **A complete, single letter-lifecycle audit trail + a `LetterStatus` enum.**
   Every status change (including scheduler delivery) writes a `LetterStatusLog`
   row with a real reason + operator (system vs sender vs receiver), enabling a
   letter-lifecycle timeline view. Replace ~40 scattered status string literals
   with an enum + DB CHECK constraint so the invariant survives direct SQL.

8. **A unified API contract.** One success envelope (`ApiResponse`) and one error
   envelope used by `GlobalExceptionHandler`, the rate-limiter 429, and every
   controller — with HTTP status that reflects the error (401/403/404/422 vs
   universal 400). Typed, validated DTOs everywhere (no `Map<String,Object>`
   bodies) and global request-size limits to protect the DB and LLM budget.

9. **An API design guide + a per-controller contract smoke test.** Capture: ownership
   checks mandatory, `@Valid` mandatory, list endpoints must paginate+cap, SSE must
   heartbeat, the error envelope — so the next 10 controllers don't reintroduce
   these gaps. A MockMvc auth+ownership+happy-path smoke test per controller so the
   boundary regressions found here can't silently return.

10. **Make Aurora's self-understanding loop observable and safe under concurrency.**
    The turn-counter / goodbye-confirm maps (BE-049) should be persisted per session
    (atomic), the `streamStage` cache should be bounded and scheduled (not
    sleep-swept), and the proactive/dismiss UX (BE-020/025) should actually persist
    cooldown so quiet-hours work. RUN-006 (Aurora self-understanding) depends on
    these being real, not stubbed.

---

## Top Priorities (the must-do items for perfection)

1. **BE-001 + BE-002 (the core loop's integrity):** make `finish()` atomic +
   conditional-UPDATE, switch the 5 listeners to `@TransactionalEventListener(AFTER_COMMIT)`,
   and enforce one-card-per-session with a unique constraint. Without this the
   starfield — the product's signature — silently corrupts.
2. **BE-003 + BE-004 + BE-010 + BE-013 (security perimeter):** real JWT auth, a
   shared `assertOwnsSession`/ownership layer, and the IDOR fixes. The current auth
   is a placeholder that ships.
3. **BE-005 + BE-006 + BE-007 (concurrency correctness):** atomic counter UPDATE,
   `@Version`/conditional UPDATE on letters, and a DB-backed unique constraint on
   the capsule-sync PENDING dedup. These are the lost-update / double-insert hotspots.
4. **BE-009 + BE-044 (make the signature features real):** wire gravity time-decay
   into the nightly job and wire `parallaxDistance` into letter delivery + the
   FLYING stage. The two qualities that define the product are currently dead code.
5. **BE-008 + BE-031 + BE-033 (one API contract):** a single error envelope with
   correct HTTP statuses, `@Valid` + size caps everywhere, all validation errors at
   once. This unblocks every frontend and every future endpoint.
6. **BE-015 + BE-016 + BE-014 (scale readiness):** split the async pools and stop
   cross-pool `.join()`; add ShedLock before any second replica. Correctness today
   silently degrades under modest load.
