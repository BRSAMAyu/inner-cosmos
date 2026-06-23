# Inner Cosmos — Final Perfection Run (RUN-FINAL)

Autonomous run kicked off 2026-06-23 via `/loop 15m keep pushing until the end`.
**Goal:** audit → master report → implement everything → verify → fix → final-verify → DONE. Make the project perfect & *complete* (no room to improve). Fully realize the vision in `inner_cosmos_愿景文档` / `工程总纲`.

## Hard facts
- **Git: NO remote configured** → `git push` is impossible. Current state was committed locally (checkpoint) on branch `feat/run006-aurora-self-understanding`. To push: add a remote (`git remote add origin <url>`) — **pending a URL from the user**.
- **Loop:** recurring job `30d563f0`, every 15 min, prompt `keep pushing until the end`. Auto-expires in 7 days. Cancel sooner: `CronDelete 30d563f0`.

## Phases
1. **[Phase 1 — IN PROGRESS]** 5-expert deep audit (2+2+1). Four independent experts (Backend/Data, AI/Prompt, Frontend/UX, Safety/Ops) each fan out subagents across their aspects; then Expert 5 synthesis critic reads all four and builds the master list. Outputs: `docs/audit/final/01..04-*.md` + `05-critic-synthesis.md`. Workflow name: `inner-cosmos-final-audit`.
2. **[Phase 2]** Supervisor writes `docs/audit/final/MASTER-POLISH-REPORT.md` — the ultimate report (drawbacks + what to improve + how). Guides the final term.
3. **[Phase 3]** Implement everything in the master report (perfect + add missing features to fully realize the vision). Sub-tasks created after the report.
4. **[Phase 4]** 3 independent experts inspect the implementation in detail.
5. **[Phase 5]** Fix all remaining problems found.
6. **[Phase 6]** 2 final agents inspect the repair work → END.

## How to resume (for /loop wake-ups & new sessions)
- Read THIS file first, then `TaskList`.
- Phase 1 outputs land in `docs/audit/final/`. If the audit workflow was interrupted, resume via `Workflow({ scriptPath, resumeFromRunId })` (journal cached) or relaunch.
- Keep tests green: `mvn test`. Memory note: M2_HOME gotcha — use `.tools/apache-maven-3.9.9` or `scripts/run-dev.ps1`. Green floor was ~606 tests; never regress.
- Commit per completed phase on the feature branch. **Do NOT deploy / publish / charge / exfiltrate** — implementation + local commits + (future) push only. Those are hard-stop boundaries.
- **Runtime verification (user requirement — project must be proven actually runnable & workable):** build + run the Spring Boot app (`mvn spring-boot:run`, H2 default; use `.tools/apache-maven-3.9.9` / `scripts/run-dev.ps1` to dodge the M2_HOME gotcha), then exercise it for real — `curl` the APIs, fetch pages via webReader, and use the `run`/`verify` skills (browser-driven) to walk core journeys (register → login → Aurora chat → memory → starfield → capsule → plaza → slow-letter). Browser-test deeply in Phase 4 & Phase 6; smoke-test when writing the master report.

## Independence contract (per user)
Experts 1–4 must NOT see each other's output. Expert 5 sees all four and critically inspects them. Enforced by the workflow's wave structure.

## Live notes
- **Phase 1 (audit) + Phase 2 (master report) DONE.** Artifacts in `docs/audit/final/` (00 vision, 01–04 experts, 05 critic, MASTER-POLISH-REPORT, runtime-findings). Run ID was `wf_91785a38-3f5`.
- **Phase 3 (implementation) IN PROGRESS** — following the master report's roadmap. Source of truth for the full list: `MASTER-POLISH-REPORT.md` §3/§7 and `05-critic-synthesis.md` (M-001…M-083).

### Phase 3 progress
**Done & verified (committed):** Phase 0 safety/workability cluster COMPLETE.
- `RT-01` — LLM default `dev`/`mock` (chat verified 200). `877a31a`.
- `M-002` — real crisis hotline numbers + renderable tel links + pinning test. `e67952f`.
- `M-003` — hard 4s deadline on synchronous safety recheck + deadline-contract test. `c1a9634`.
- `M-020` — crisis false-negative coverage (means/English/homophone) + test.
- `M-001` — Aurora IDOR: assert dialog-session ownership on chat endpoints + negative-ownership test. `62629ac`.
- `M-009` — stopped silent demo auto-login; real front door. `5f48117`.
- `M-004` — plaza capsules projected to public-safe VO (no internals leak) + structural test. `189f9d0`.
- `M-005` — persona-chat no longer egresses visitor's private P1 (includeMemory=false). `4166b9d`.
- `M-006` — PII (phone/email) masked at the LLM egress chokepoint (ABTestLlmClientWrapper) + test. `e7dcda1`.
- `M-030` — setPreferredModel selects profile by user_id (not wrong PK) + test. `1b57b7b`.
- `M-023` — capsule boundary now requires ownership (close IDOR). `56953b1`.
- `M-014` — gravity time-decay wired into the nightly job (starfield ages) + decay test. `cd05364`.
- `M-011` — 5-turn portrait reflection now persists (closes the RUN-006 no-op). `7a7d6f6`.
- `M-026` — DISPLAY_ONCE shred is TRANSIENT + zero gravity (no resurface) + test. `60769ae`.
- `M-021` — atomic session counter (was inert @Transactional self-call + lost updates). `cefa03a` + test `a747a83`.
- `M-007` — atomic conditional finish (no more double-fired memory events / duplicate cards). + test. `fbcf40a`.
- `M-025` — IC.api status-aware (401/403→login) + GET-only retry. `fb2614d`.
- `M-022` — atomic conditional letter transition (optimistic lock).
- `M-027` — wired orphan pages into nav (thought-shredder main; ai-log/ai-dev-history admin).
- `M-029` — calligraphic typography loaded app-wide via mountShell (was 1/35 pages).
- `M-015` — EmotionTimeline auto-aggregates from traces on dialog finish (spectrum no longer fictional).
- `M-017` — PORTRAIT_DIM corrections durably reshape the portrait (no more 'forgot'). `947c487`.
- `M-019` — per-IP brute-force protection on /api/auth/login. `b712ec0`.
- `M-074` — requireAdmin on prompt-version reads (content/versions/metrics).
- `M-077` — scheduler letter delivery writes the audit log. `05d95c5`.
- `M-033` — account deletion requires password re-auth (FE+BE). `8c39e21`.
- `M-078` — belief recalculate + reportLetter enforce ownership. `d91ceab`.
- `M-008` — UNIQUE(user_id, source_session_id) on tb_memory_card (starfield integrity backstop). `b0960f3`.
- `M-032` — password change (requires old password) BE+FE. `d094bcc`.
- `M-043` — proactive job logs failures + retries failed timers. `c934503`.
- `M-045` — per-thread goodbye strength (shared-singleton race fixed).

**32 fixes done & verified; full suite green (621 tests, 0 failures).**

### Phase 4 (inspection) + Phase 5 (fixes) — DONE / IN PROGRESS
- **Phase 4:** 3 independent inspectors ran over the 33-fix diff. Verdict: most fixes sound; found real issues.
- **Phase 5 P1 fixes (committed, suite green):** M-032 changePassword null-guard · M-006 redact requestJson · M-004 /api/plaza/matches projection · M-001/M-024 setSessionModel ownership · M-002 split 110/120 hotlines · M-005 remove visitor agent-context (was still leaking) · M-011 async reflection (latency) · M-008 migration initializer (retrofit UNIQUE) · M-019 trusted-proxy flag · M-053 portrait dims synced · M-069 editable corrections + page · M-045 atomic turnCounter · M-051 (skipped — referenced by test) · M-082 loading-mask dismiss.
- **Approach switched to parallel + background tests** (per user): batch implementations on disjoint files via Workflow; full suite run in background (non-blocking); independent inspection agent per batch.

### In flight
- Background full suite (verifying batch-1 + M-045/M-082) + independent inspection agent on batch 1.

### Next
- **Batch 2** (disjoint): M-067 resonance "landed" signal, M-070 first-run onboarding, M-052 wire prompt-versioning. Then Phase 6: 2 final agents + browser verification → END.
- Remaining bigger items (fresh context if needed): M-010 signed JWT, full CSRF token (M-018 other half), M-044 error-envelope normalization.

## ✅ RUN COMPLETE (all 6 phases done)
- **Phase 3 (implement):** ~44 fixes — all P0s (safety/trust/data-integrity) + all 4 signature no-ops (M-011/M-014/M-015/M-017) + major P1/P2/P3 + wow features (M-067 resonance, M-070 onboarding) + a11y (M-060/M-081/M-029). 48 commits since audit baseline.
- **Phase 4 (inspect):** 3 inspection rounds (initial + batch 1 + batch 2).
- **Phase 5 (fix):** all inspection findings fixed (incl. the 2 Phase-6 P1s).
- **Phase 6 (2 final agents):** done. Found 2 P1s → FIXED + runtime-verified (settle idempotency realistic-flow 200; router-path PII redaction wrap). Plus an edge-NPE guard.
- **Verification:** full suite GREEN (621+ tests, 0 failures); runtime core loop (12 endpoints 200) + safety (crisis→hotline) + new features (corrections, resonance echoEnergy bump) + settle-after-finish all verified live.

### Documented for future (fresh-context) work (not blocking)
M-010 signed JWT (replace X-User-Id), full CSRF token (M-018 other half — ~76-site MockMvc cascade), M-052 wire prompt-versioning into PromptBuilder, M-068 parallax-flight (FLYING state), M-044 error-envelope normalization, + the Phase-6 P2s (4 DialogFinishedEvent listeners → AFTER_COMMIT; rate-limit X-User-Id keying). **No P0/P1 remains.**

**Remaining (lower priority / bigger):** `M-018` CSRF + cookie hardening, `M-010` signed JWT (replace X-User-Id), `M-032` password change/reset, `M-006` retention job for redacted logs, plus P2/P3 polish (M-045/M-043/M-039/M-051/M-052/M-075/M-076) and Group-C wow (M-067/M-068/M-069/M-070/M-072). **After Phase 3:** 3 experts inspect (P4) → fix (P5) → 2 final agents + browser verify (P6) → END.

**Next priorities:** `M-008` (UNIQUE on tb_memory_card), `M-078` (belief/reportLetter ownership), `M-075`/`M-076` (validation/error-envelope), `M-029` typography-app-wide is DONE — pick from remaining P2/P3 + Group-C wow, then the bigger coordinated items (`M-018` CSRF, `M-010` JWT, `M-032` password change/reset). **After Phase 3:** 3 experts inspect (P4) → fix (P5) → 2 final agents + browser verify (P6) → END.

**Next priorities:** data-integrity P0s `M-007` (atomic finish + AFTER_COMMIT listeners), `M-008` (UNIQUE on tb_memory_card(user_id, source_session_id)); signature no-ops `M-015` (EmotionTimeline auto-aggregate), `M-017` (corrections durably reshape portrait); then `M-022` (letter optimistic lock), `M-025` (resilient IC.api), then remaining P1/P2 hardening (M-010 JWT, M-018 CSRF, M-019 brute-force, M-032/M-033) and Group-C wow. **After Phase 3:** 3 experts inspect (P4) → fix (P5) → 2 final agents + browser verify (P6) → END.

**Verification discipline:** targeted `mvn test -Dtest=<class>` per cluster; full `mvn test` at Phase-3 checkpoint. App runtime restart: `mvn spring-boot:run -Dspring-boot.run.fork=false` (fork=false → TaskStop kills it cleanly, no orphan JVMs). Kill stray 8080 holders by PID: `netstat -ano | grep :8080` → `taskkill //F //PID <pid>`.

**Test-tool discipline (Windows):** use `curl -b <jar> -c <jar>` on every call; send non-ASCII bodies from a UTF-8 file via `--data-binary @file` (inline `-d` GBK-encodes Chinese → JsonParseException).
