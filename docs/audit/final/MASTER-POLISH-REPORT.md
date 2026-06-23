# Inner Cosmos — Master Polish & Improvement Report (RUN-FINAL)

> **The ultimate report.** Synthesizes the 5-expert deep audit (`01`–`05`), the vision brief (`00`), and real-world runtime testing (`runtime-findings.md`) into one complete, precise, detailed guide for the final implementation term. Every item below is evidence-backed (file:line) and prioritized for action.
>
> **Produced:** 2026-06-23 · **Method:** 5 independent experts (2+2+1, 21 agents) + Synthesis Critic + live app boot & API exercise · **Verdict:** the project is **~55–60% of the way to "perfect"** — excellent intent and craft, missing *enforcement and last-mile wiring*. None of this requires rearchitecting the core loop.

---

## 0. How to read this report

- **§1 Verdict** — where the project stands, what's genuinely excellent (don't lose it), the five honest truths blocking "perfect."
- **§2 Runtime-confirmed findings** — bugs proven by booting & curling the live app (not just code-reading). Start here.
- **§3 The Drawbacks** — every problem, grouped P0→P3, with evidence. (Full 83-row table lives in `05-critic-synthesis.md`; this report condenses to decision-grade and adds runtime items.)
- **§4 Cross-cutting systemic gaps** — the 8 root causes beneath the symptoms.
- **§5 What to Improve & How** — the Path to Perfection: (A) Must-Fix, (B) Should-Add, (C) Wow.
- **§6 Vision-completion plan** — what to add so the product fully realizes its vision.
- **§7 Execution roadmap** — the 7-phase implementation order for Phase 3.
- **§8 Non-regression & verification** — green floor + browser-test plan.

Item IDs `M-0xx` reference the unified master list in `05-critic-synthesis.md` (which cites the per-expert reports `01`–`04` for full detail). `RT-0x` = runtime-confirmed.

---

## 1. Verdict

**~55–60% to "perfect."** Stronger than a typical prototype, weaker than a shippable product — and the gap is almost entirely in *enforcement and last-mile wiring*, not in ideas or effort.

**Genuinely excellent — do not lose this:**
- **Safety-first ordering is real and tested.** `SafetyService.check` runs synchronously before any token (POST + SSE); crisis never streams as free-form consolation; the acute-crisis floor (`looksLikeGenuineCrisis`) is enforced symmetrically on live-LLM, fallback, and exception paths and is regression-tested. For a mental-health-adjacent product, this is the hardest part and it's done right.
- **Concurrency engineering in the right places.** `PersonaChatServiceImpl.tryReserveQuota` (conditional UPDATE→INSERT→DuplicateKey), `SessionCloser.runAfterGoodbye` (atomic `UPDATE … WHERE ended_at IS NULL`), `EmotionInsightServiceImpl.insertWithRaceFallback`, `CapsuleRegenerateListener` (`@TransactionalEventListener(AFTER_COMMIT)+@Async`). Proves the team *can* do disciplined data integrity — which makes the places that don't all the more fixable.
- **Art-directed frontend.** Complete internally-consistent dark theme, aggressive reduced-motion contract, real a11y primitives (skip-link, `:focus-visible`, focus-trapped modals, `aria-live`), `IC.esc()` applied even to streamed SSE. Not templated work.
- **Thoughtful AI pipeline bones.** Disciplined `PromptBuilder` chokepoint with curation caps, injection-conscious `sanitize()`, robust `StructuredOutputParser`, the IC-EMO two-timescale emotion design, in-reply anti-repetition. The vision is *understood*, not copied.
- **Feature surface is large and real.** ~110 API endpoints, 35 pages, 10 well-written seed personas, 4 LLM providers, A/B-test infra, prompt-versioning subsystem — far beyond a skeleton.

**Five honest truths keeping it from "perfect":**
1. **The trust/privacy boundary is partly theatrical.** Real crisis *logic* + a placeholder auth filter that grants `ROLE_ADMIN` from a header + unauthenticated plaza exfiltration of full capsule internals + no PII redaction on the LLM-egress path + a crisis funnel that renders *zero* hotline numbers. The P0→P3 privacy *model* is real in the schema; it is **not enforced on the read/egress side** in several load-bearing places.
2. **Three signature promises are silent no-ops in production.** "Aurora re-learns who you are every 5 turns" (the hook discards its result → **M-011**), gravity time-decay (`daysSinceLastTouched` always 0 → starfield frozen → **M-014**), and "slow" letters (flat 3-min delivery; `parallaxDistance` stored, never used → **M-068**). The code exists; the wiring does not.
3. **The self-understanding loop (the branch name) is not closed.** Corrections and the 5-turn reflection are prompt-only; the portrait only durably updates on goodbye/nightly. RUN-006's headline feature is a billed no-op mid-session (**M-011/M-017**).
4. **The core loop has real data-integrity holes.** `finish()` is non-transactional with a lock-free idempotency guard (can double-fire all 5 listeners → **M-007**); two parallel paths can each insert a MemoryCard for the same session with **no unique constraint** (**M-008**). The starfield — the signature artifact — can silently corrupt.
5. **There is no real front door, and the out-of-the-box demo is broken.** Every core page silently auto-logs a brand-new visitor into the shared `demo` account (**M-009**), AND the shipped LLM default (`prod`/`minimax`/no-fallback + invalid keys) makes the core chat return **HTTP 500** for any new user (**RT-01**). For a privacy-first product this is both a clarity failure and a data-exposure + "it doesn't work" failure.

**Scoring (critic):** safety logic 9/10 · safety last-mile 3/10 · architecture 8/10 · data-integrity enforcement 5/10 · AI pipeline design 8/10 · signature-feature wiring 4/10 · frontend surface 9/10 · frontend foundations 5/10 · privacy model 9/10 · privacy enforcement 4/10.

---

## 2. Runtime-confirmed findings (booted the live app + exercised the API)

These were proven by running the app (Java 21 / Spring Boot 3.3.6, H2 file DB, profile `dev`) and making real HTTP calls — the layer static analysis can miss.

| ID | Sev | Finding | Evidence | Fix |
|----|-----|---------|----------|-----|
| **RT-01** | **P0** | **Core chat `/api/aurora/message` → HTTP 500 out-of-the-box.** Shipped config defaults break the no-API-key demo promise. | `application.yml:99` `llm.mode: ${LLM_MODE:prod}` (default **prod**, not dev), `:100` `provider: ${LLM_PROVIDER:minimax}` (not mock). Prod force-disables fallback; all keys invalid (401 at boot: MiniMax/GLM/DeepSeek). Reproduced: `login`→200, `session/create`→200, **`/api/aurora/message`→500** `INTERNAL_ERROR`. Greeting degrades gracefully (non-LLM fallback); chat does not. | Default `mode: ${LLM_MODE:dev}` + `provider: ${LLM_PROVIDER:mock}`; reconcile README/AGENTS ("默认 Mock / 无 Key 可演示"); give chat a graceful fallback like greeting has. **Re-run the message call after the fix to confirm 200.** |
| **RT-02** | P2 | Spring Security emits a generated default password in every profile → `UserDetailsServiceAutoConfiguration` defaults active alongside the custom JWT filter. | boot log: "Using generated security password … must be updated before production". | Define the security persona explicitly / suppress the autoconfig; verify nothing relies on the inMemory user. |
| **RT-03** | P2 | Schema migrations silently swallow "bad SQL grammar" on several `ALTER TABLE ADD COLUMN` (H2 MODE=MySQL). | boot log: `SchemaM0M6/M2/M4Initializer` ALTERs fail, logged DEBUG + skipped. | Validate migrations don't mask real failures; prefer additive DDL that H2+MySQL both accept. |
| **RT-04** | P2 | Doc drift: README/AGENTS say "H2 In-Memory" + "18 pages"; actual is H2 *file* DB + 35 pages. | `application.yml:25` `jdbc:h2:file:…`; `ls static/pages` = 35. | Update docs to match reality. |
| **RT-05** | — | **Auth works correctly in browser conditions.** (Earlier "403 on 19 endpoints" was a **curl artifact** — not saving the post-migration `JSESSIONID`. With `-b`+`-c` on every call, all core endpoints return 200.) | Controlled sweep: 10/10 core GET endpoints 200 with proper cookie tracking. | None — record the `-b`+`-c` test discipline for future runtime tests. Session-fixation migration is expected/benign. |

> **RT-01 is the single most immediately user-facing defect:** a brand-new user who opens the app and chats with Aurora gets a 500. It must be in the first fix batch alongside the safety-of-life items.

---

## 3. The Drawbacks (condensed master list)

Full per-item evidence + recommendation in `05-critic-synthesis.md` (83 items, M-001→M-083). Grouped here by severity. Domain tags: SEC/SAFE/DATA/BE/AI/FE/OPS.

### P0 — fix before any production exposure (9 items)

| ID | Domain | Drawback | How to fix (summary) |
|----|--------|----------|----------------------|
| **M-001** | SEC | **Aurora-chat P0 IDOR** — client-supplied `sessionId` read/written with no ownership check (message/message-rich/stream/greeting/settle). Loads a victim's P0 raw dialog + corrupts their memory. | One `assertOwnsSession(userId,sessionId)` guard in `saveUserMessage`/`reply`/`stream`; negative MockMvc tests. |
| **M-002** | SAFE | **Crisis funnel renders ZERO hotline numbers.** `resources()` = 3 text strings, no digits; `safety-harbor.html` tel-link gated on a number regex that never matches. **Safety-of-life.** | Region-aware numbers (110/120/12320/010-82951332/400-161-9995/988 + intl) + a test asserting a phone exists. |
| **M-003** | SAFE/OPS | Sync safety recheck can block **tens of seconds–minutes** before a person in distress sees anything (JSON-repair retry × failover × 20–30s timeouts). | `CompletableFuture.orTimeout(4s)` for SAFETY_REVIEW + deterministic fallback; disable repair on safety path. |
| **M-004** | SEC | Unauth `GET /api/plaza/capsules` leaks full capsule internals (`personaPrompt`, `ownerContextNote`, `style*`, `context*`, `authorizedMemoryIds`). P2 "authorized abstract only" not enforced on read. | Public-safe VO projection; never serialize internals. |
| **M-005** | SEC | PersonaChat egresses the **visitor's full private P1 context** (top-8 memories, todos, portrait, baseline) into a stranger's capsule conversation. | Minimal visitor context; `includeMemory=false` for PERSONA_CHAT. |
| **M-006** | SEC | **No PII redaction on the dialog→LLM path.** Raw P0 dialog, memory, portrait, corrections concatenated verbatim, egressed to providers, stored plaintext in `tb_ai_interaction_log` indefinitely. | One mandatory redaction pass at the LlmClient chokepoint + retention job. |
| **M-007** | DATA | `finish()` non-transactional + lock-free idempotency guard; 5 plain `@EventListener` listeners → concurrent finishes double-fire all 5 + read half-committed state. | Conditional `UPDATE … WHERE status<>'FINISHED'`, publish only if rowsAffected==1; switch to `@TransactionalEventListener(AFTER_COMMIT)`. |
| **M-008** | DATA | Two parallel memory-settlement paths each insert a MemoryCard for the same session — **no UNIQUE** on `tb_memory_card(user_id, source_session_id)`. Duplicates pollute starfield + gravity. | Add UNIQUE + upsert; one authoritative settlement path. |
| **M-009** | FE/SEC | **No real front door** — every core page silently auto-logs visitors into shared `demo` account (`ensureDemoLogin`), exposing demo's P1 memories + shipping a real password in JS. | Profile-gate demo to dev; real front door; explicit "游客体验" button. |

> Plus **RT-01** (chat 500) belongs in this batch — it's the workability P0.

### P1 — fix before broad user exposure (24 items, condensed)

- **Auth/trust:** M-010 (`X-User-Id` header grants `ROLE_ADMIN`, latent), M-018 (CSRF off + cookie auth + SameSite/Secure unset), M-019 (no login brute-force protection), M-023 (capsule boundary IDOR), M-024 (session-mode/goodbye/safety/setSessionModel ignore ownership), M-032 (no password change/reset; weak floor), M-033 (account delete = hard delete, no re-auth/soft-disable/export-first).
- **AI signature no-ops:** M-011 (5-turn reflection discards result — **RUN-006 no-op**), M-012 (mode temperature computed but never applied), M-013 (system boundary sent as `role:user`), M-014 (gravity time-decay dead), M-015 (EmotionTimeline never auto-aggregates → fictional spectrum), M-016 (offline proactive push logged as sent, budget burned), M-017 (corrections never durably reshape portrait — "这不太是我" forgets), M-031 (hard-boundary refusal gated on optional service; proactive bypasses safety).
- **Data integrity:** M-021 (`increment()` private self-call → tx inert, lost updates), M-022 (letter transitions no optimistic lock), M-030 (`setPreferredModel` wrong key `selectById`).
- **Frontend:** M-025 (`IC.api` status-blind + retries non-idempotent POSTs), M-026 (DISPLAY_ONCE persists raw vent, leaks to history/gravity), M-027 (3 orphaned pages: thought-shredder/ai-log/ai-dev-history), M-028 (3 broken social journeys), M-029 (typography loads on 1/35 pages).

### P2 — hardening & should-add (41 items, M-034→M-073)
Includes: M-034 (capsule-sync dedup non-atomic), M-035 (shared async pool saturates + cross-pool `.join()` deadlock), M-036 (LLM calls inside `@Transactional` hold DB connections), M-037 (duplicate emotion LLM call/finish), M-038/039 (letter reply/state issues; FLYING dead state), M-040 (no idempotency/pagination caps), M-041 (rate-limiter gaps), M-042 (ASR loads full upload to `byte[]`; `/mock-transcribe` unguarded), M-043 (proactive job swallows exceptions), M-044 (3+ incompatible error envelopes), M-045/046/047/048/049 (goodbye race, JSON-truncation repair, emotion-scale mix, weekly-review V2, in-memory turn counters), M-050 (prompt sanitize shallow), M-051 (dead 11-strategy hierarchy), M-052 (prompt-versioning built but unused), M-053 (portrait dim vocabulary mismatch), M-054 (no ShedLock on jobs), M-055 (3 divergent safety keyword lists), M-056/057 (safety/admin observability gaps), M-058 (no encryption-at-rest; H2 blank password), M-059 (geolocation auto-collected+persisted+egressed), M-060/061/062/063/064/065/066 (frontend a11y/skeleton/tokens/capsule-chat/registration/daily-record), and the **Group C wow** items M-067→M-073.

### P3 — polish (10 items, M-074→M-083)
Admin-read auth, shallow validation, error-envelope mapping, scheduler audit-log gaps, dead registries, stale counts, toast a11y, loading-mask timing, crisis punctuation/copy.

---

## 4. Cross-cutting systemic gaps (the root causes)

These end-to-end/root issues generated many of the symptoms above. Fixing them retires whole clusters.

1. **The "this landed for me" resonance loop is open in one direction only.** Resonance is measured by *matching*, never by *receipt*. A closed "landed-for-me" micro-signal flowing back into `echoEnergy` would make slow-social *felt*.
2. **No unified "Aurora outgoing message" pipeline.** Chat/proactive/greeting/mode-ack/capsule/persona/letter-guard each apply *different subsets* of {safety, anti-repetition, boundary sanitize, PII redaction, persona consistency}. One `OutgoingAuroraMessage` gateway retires ~6 findings (incl. M-016, M-031).
3. **The three signature visualizations render from stale/frozen/empty signals.** Starfield (frozen gravity), emotion spectrum (fictional), portrait (stale mid-session). Fixing M-011/M-014/M-015/M-017 together makes all three go live — the single highest-leverage "make it real" cluster.
4. **No coherent session lifecycle across the fan-out.** "finished vs goodbyed vs settled vs ended" is implicit; a new message can be saved against a FINISHED session. A `SessionStarted/Finished(atomic)/Ended/Settled` event family is the structural fix beneath M-007/M-021.
5. **No dev/prod profile boundary.** `MockLlmClient`, `/mock-transcribe`, `X-User-Id`, `ensureDemoLogin` run in every profile. One `prod-readiness` pass (gate dev paths behind `@Profile('!prod')`, randomize seed passwords, force no-fallback in prod) closes M-009/M-010/RT-01 + the `/mock-transcribe` OOM vector at once.
6. **Untested critical paths (runtime-only).** No test asserts: `resources()` has a phone; user A can't touch user B's session/capsule/letter (negative ownership tests); gravity *decreases* with time; DISPLAY_ONCE shred is absent from history; safety-recheck latency budget. Each is a load-bearing contract with zero test pinning it.
7. **The privacy promise is documented (P0→P3) but never *shown* to the user.** No per-turn "what Aurora knew this turn" view, no granular consent, no privacy cockpit. The core differentiator is asserted in docs but invisible in UX.
8. **Capsule "window-shopping" + session resume missing end-to-end.** The only way to hear a capsule's voice is to start a full chat (consuming quota); persona-chat sessions are orphaned. No zero-cost audition, no "my ongoing echoes" home.

---

## 5. What to Improve & How — Path to Perfection

### (A) Must-Fix — correctness, security, safety, data integrity
- **Close the IDOR/ownership perimeter structurally (M-001/M-023/M-024).** One `assertOwnsSession` helper + reusable ownership guard; route every `{id}` path-variable through it; ship negative MockMvc tests. *Retires ~8 findings.*
- **Real crisis hotline numbers + hard 3–4s deadline + broader detection (M-002/M-003/M-020).** Region-aware numbers, phone-existence test, `orTimeout(4s)`, means-planning/English/homophone coverage. *Cheapest, highest-impact cluster.*
- **Atomic `finish()` + one-card-per-session (M-007/M-008).** Conditional UPDATE, AFTER_COMMIT listeners, UNIQUE + upsert. *Protects the starfield — the signature.*
- **Fix the auth trust boundary (M-010/M-018/M-019/M-032).** Remove/gate `X-User-Id`; signed JWT (jjwt already a dep); CSRF + hardened cookie; login brute-force bucket; password change/reset. *Foundation.*
- **Project public-safe VOs + redact at egress (M-004/M-005/M-006).** Plaza projection, minimal visitor context, one PII-redaction pass at the LLM chokepoint. *Makes P0→P3 real against third-party egress.*
- **Stop demo auto-login; build a real front door (M-009).** Profile-gate demo; index as public landing; route to login unless a real session exists.
- **Fix the shipped LLM default (RT-01).** `mode=dev`/`provider=mock` default so the no-key demo works; graceful chat fallback; reconcile docs.
- **Resilient `IC.api` (M-025).** Status-aware (401→login redirect), GET-only retry, standard error shape.
- **Truthful DISPLAY_ONCE (M-026).** Exclude TRANSIENT from history/gravity or don't persist raw; test.
- **Concurrency correctness (M-021/M-022).** Atomic counter UPDATE; `@Version`/conditional UPDATE on letters.

### (B) Should-Add — missing core features & journeys
- **Close the self-understanding loop (M-011/M-017).** Persist 5-turn reflection deltas; route corrections through `applyDeltas` with `source='user_correction'` hard-prior badge. *The literal meaning of the branch name.*
- **Make the three signature signals live (M-014/M-015).** Wire gravity decay into the nightly job (real `daysSince`); auto-aggregate EmotionTimeline from traces; re-touch bumps gravity.
- **Make "slow" social actually slow + tangible (M-068/M-028).** Wire `parallaxDistance` into `estimated_arrival_at`; implement `SENT→FLYING→DELIVERED` (FLYING state + column already exist); fix the 3 broken journeys.
- **Wire mode temperature (M-012) + send boundary as `role:system` (M-013).** Foundation for all AI-quality claims.
- **Unified outgoing-message + reply-routing (Cross-cutting #2).** One gateway + one strategy registry so mode actually changes sampling.
- **Session lifecycle events (Cross-cutting #4).** `SessionStarted/Finished(atomic)/Ended/Settled`.
- **Orphan-page wiring + live notification badges (M-027).** thought-shredder/ai-log into nav; unified connection home with unread badges (RUN-006 notification dead-angle).
- **App-wide typography + one modal + design tokens (M-029/M-063).** Fonts via `mountShell`; overlays via `IC.showModal`; adopt dead `--fs-*`/`--ring` tokens. *Unblocks consistent polish on all 35 pages.*
- **Safety observability + session-rhythm guardian (M-056).** Wire the dead `SessionDurationLimiter`/`TokenBudgetLimiter`/`ResourceRedirectService` (the "no emotional dependency" boundary exists only in the prompt today); micrometer counters + moderator triage queue.
- **Encryption-at-rest + retention hygiene + privacy cockpit (M-058/M-059).** Column/DB encryption for intimate text; retention job dropping raw utterance after N days; granular consent + per-turn "what Aurora knew" view.

### (C) Wow / Differentiate — delight + vision realization
- **Closed "this landed for me" resonance signal (M-067).** Warm affordance on a received echo → `echoEnergy` + capsule count. *The most on-brand wow available.*
- **Parallax "in transit" flight animation (M-068).** FLYING state + parallax column already exist — an honest "your letter is crossing a small cosmic distance" viz is 90% scaffolded. Signature wow.
- **Editable self-understanding UX + provenance badges (M-069).** "我的更正 / Aurora 对我的理解" page; review/amend/retire corrections; "you told me" vs "I noticed" provenance. *Makes Aurora's model auditable.*
- **Real agent loop (M-071).** Observe portrait+state → decide intent → optional memory/emotion tool → draft → self-critique → emit. Raises reply quality + makes "how Aurora decides" honest.
- **First-run onboarding + privacy moment (M-070).** Landing → sign-up → empty cosmos → 3-step tour → a privacy moment teaching the real P0→P3 promise. *The differentiator, taught.*
- **Capsule audition + "my ongoing echoes" resume home (M-072).** Zero-cost pinned first utterance; resume surface for ongoing persona-chats.
- **Proactive safety routing into the harbor.** Detect rising distress from timeline/shredder; gently surface `safety-harbor.html`.
- **Replayable golden reply-tests (M-073).** `LlmRequest` carries temp/topP/seed + deterministic mock pin → voice/safety/robustness regressions caught end-to-end.

---

## 6. Vision-completion plan (what to add so the product fully realizes its vision)

Cross-checking the audit against the vision brief's 16 coverage gaps, these are **unfinished or at-risk vision promises** that Phase 3 must complete (audit verdict where verified):

| Vision promise | Status | Action |
|----------------|--------|--------|
| Aurora active multi-message + mode-specific behavior | Partially real (anti-rep exists) but mode temperature dead (M-012), boundary mis-roled (M-013) | Wire temperature + role:system + strategy registry |
| Conversation→memory async sediment non-blocking | Wired but **double-fires + non-atomic** (M-007) | AFTER_COMMIT + conditional UPDATE |
| Real nonlinear gravity + time decay | Formula exists, **decay dead** (M-014) | Wire `daysSince` in nightly + re-touch bumps |
| Capsule-chat P0 privacy at data layer | **Violated** — visitor P1 egressed (M-005) | Minimal visitor context; `includeMemory=false` |
| DataMaskingService real desensitization | Used only on capsule-preview branch (M-006) | One egress redaction pass |
| Slow-letter 9-state machine + safety filter + parallax | FLYING dead, parallax unused (M-068/M-039); 3 journeys broken (M-028) | Implement FLYING+parallax; fix journeys |
| SafetyBoundaryFilter HIGH fixed-flow (no free LLM) | Real + tested ✅ | Keep; add hotline numbers (M-002) |
| Rhythm protection (duration/token/message caps) | **Dead code** — prompt-only today | Wire the 3 limiters (M-056) |
| ≥5 quality seed capsules | ✅ 10 well-written seeds | Keep |
| Mock mode genuinely keyword-aware | Exists; but **default isn't Mock** (RT-01) | Fix default + verify keyword-awareness |
| Starfield life-fragment + 放入星海 | Detail exists; gravity frozen (M-014) | Make gravity live |
| Voice input + metadata + gentle injection | Path exists | Verify metadata flows; RT-confirm |
| Long-term memory summary-anchor window | Partial | Verify sliding window injection |
| UX art direction delivered | Strong surface, weak foundations (M-029/M-063) | App-wide fonts/tokens/modal |
| Thought-shredder end-to-end | **Privacy leak** (M-026) | Truthful DISPLAY_ONCE |
| User data control (view/delete/export/auth) | Hard-delete only (M-033) | Soft-disable + export-first + cockpit |

**The differentiators to protect & sharpen:** (1) emotional-gravity memory universe, (2) capsule-as-bridge slow-social with structural privacy, (3) Aurora as conversational journal whose win-state is "我好像终于把自己说清楚了一点," (4) ethics-as-architecture, (5) designer-website-grade inner-universe aesthetic.

---

## 7. Execution roadmap (Phase 3 implementation order)

Sequenced by (safety-of-life → trust boundary → core-loop integrity → make-signature-real → journeys/pipeline → hardening → delight). Each phase is independently shippable. Effort is rough team-days.

- **Phase 0 — Safety-of-life & workability (~2–3 days):** **RT-01** (chat 500 / default config), **M-002** (hotline numbers + test), **M-003** (safety recheck ≤4s), **M-020** (crisis false-negative coverage). *A person in distress must never wait minutes or land on a phone-less page; a new user must be able to chat.*
- **Phase 1 — Trust boundary & privacy enforcement (~4–5 days):** M-001, M-004, M-005, M-006, M-009, M-010, M-018, M-019. *Makes P0→P3 real; closes the worst disclosure holes.*
- **Phase 2 — Core-loop data integrity (~3–4 days):** M-007, M-008, M-021, M-022, M-025, M-026, M-030. *Protects starfield + conversation record.*
- **Phase 3 — Make the signature features real (~4–5 days):** M-011, M-017, M-014, M-015, M-012, M-013, M-016. *Starfield + spectrum + portrait + Aurora's voice all go live — the point of RUN-006.*
- **Phase 4 — Close the journeys & unify the pipeline (~4–5 days):** M-028, M-027, M-029, M-031 (unified outgoing-message gateway), M-024, M-064, M-067. *Realizes slow-social; makes Aurora consistent.*
- **Phase 5 — Hardening, observability, scale-readiness (~4–5 days):** M-032/M-033, M-055/M-056, M-035/M-036/M-054, M-040/M-041/M-044, M-058/M-059, dead-code (M-051/M-052/M-079), the missing tests (Cross-cutting #6). *Prototype → product.*
- **Phase 6 — Delight & vision realization (ongoing):** M-068, M-069, M-070, M-071, M-072, M-073, a11y/skeleton/tokens (M-060/M-061/M-062/M-081). *Correct+safe → the experience the vision describes.*

> After Phase 3: dispatch **3 independent experts** to inspect the implementation (audit Phase 4), fix all findings (Phase 5 of this run), then **2 final agents** + full browser verification (Phase 6 of this run) → END.

---

## 8. Non-regression & verification

- **Green floor:** ~606 tests (per project memory). Every change must keep `mvn test` green; never regress. Watch the M2_HOME gotcha (use `.tools/apache-maven-3.9.9` / `scripts/run-dev.ps1`).
- **Add the missing tests as part of each fix** (Cross-cutting #6): phone-existence, negative-ownership MockMvc, gravity-decreases, DISPLAY_ONCE-absent-from-history, safety-recheck-latency. These pin the contracts so the bugs can't return.
- **Runtime/browser verification (user requirement):** after each phase, re-run the live-app smoke (`login`→`session/create`→`/api/aurora/message` must be 200) and walk the core journey in a real browser (register→login→chat→memory→starfield→capsule→plaza→slow-letter). Use `curl -b <jar> -c <jar>` (or a browser) — the app migrates the session cookie after first auth.
- **Hard-stop boundaries (do NOT cross during implementation):** no deploying/publishing/charging/exfiltrating; no real credentials/production data. Implementation + local commits + (future) push only.

---

## Appendix — source artifacts

- `00-vision-brief.md` — distilled north star, must-haves, coverage gaps, differentiators.
- `01-backend-data.md` — Backend & Data Layer (51 findings).
- `02-ai-prompt.md` — AI / Aurora / Prompt Pipeline (39 findings).
- `03-frontend-ux.md` — Frontend, UI/UX & Experience (61 findings).
- `04-safety-ops.md` — Safety, Security, Reliability & Ops (Expert 4; note: the `sec-ops` subagent died on 429 — ops/CI/test-coverage sub-aspects are thinner; re-cover during Phase 5).
- `05-critic-synthesis.md` — Synthesis Critic & full 83-row master list (the authoritative detail table).
- `runtime-findings.md` — live-app boot + API exercise (RT-0x items).

**Known audit coverage gap:** three subagents (`ai-client`, `be-data`, `sec-ops`) died on 429 rate-limiting; their sub-aspects (LLM client internals, schema/data-layer depth, ops/CI/deploy readiness) are partially covered by the expert syntheses but should be re-audited in Phase 5 if anything in those areas feels under-examined.
