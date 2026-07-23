# 2026-07-24 — 8-Agent Delivery-Readiness Audit

Status: ACTIVE — remediation in progress this session (goal: `INNER-COSMOS-COMPLETE-PRODUCT`, see
`docs/goal/closure-campaign-state.yml`).

## 0. Why this document exists

Dispatched per user direction: 8 agents in two rounds of 4, auditing the whole project against the
delivery bar "judges can download the APP or reach the web app remotely, with the user's own PC as
the server (reverse-proxy/tunnel for public access), full feature loop working end to end." Round 1
was breadth (backend/security, frontend/mobile, deployment/public-access, product completeness);
round 2 was depth/adversarial, seeded with round 1's findings (e2e judge journey actually executed,
test/evidence reconciliation actually re-run, adversarial security, delivery packaging). 45 findings
total (23 round 1 + 22 round 2). This document is the durable record so nothing gets lost; each
finding is checked off with a commit reference as it closes.

Full raw agent output (verbatim): see workflow run `wf_4dd81d62-ffc`,
`C:\Users\dengb\.claude\projects\D--code-inner-cosmos\3839ed58-fa89-47b3-b7ce-5cff87537f30\subagents\workflows\wf_4dd81d62-ffc\journal.jsonl`.

## 1. Executive summary

The project's technical core is solid: `./mvnw test` = 1184/1184 (0 failures, 1 pre-existing
environment-gated skip), frontend `npx vitest run` = 522/522 (72 files), `tsc` clean, production
bundle reproducible byte-for-byte from source, `scan-secrets.ps1` clean on the tracked tree, K8s
overlays render cleanly. The acceptance ledgers under-claim (stale in the honest direction), not
over-claim.

But three **P0** issues make the project **not actually deliverable to a judge today**:

1. **Android APK / native (Tauri) login cannot complete at all** — native builds hard-require
   OIDC/PKCE and nothing stands up a publicly-reachable IdP. A judge installing the APK hits an
   unbreakable "Mobile OIDC is not enabled" / sign-in wall and never sees the product.
2. **The default zero-config Mock AI path misclassifies ordinary anxiety/stress language as a
   mental-health CRISIS**, producing an alarming "contact emergency support" reply and a
   persistent, non-recovering "Crisis 10/10 STORM" mood badge on the main chat screen — reproduced
   twice on fresh sessions with an entirely plausible first message ("我今天有点焦虑，工作压力很大").
3. **A real, live MiniMax API key sits in plaintext** in four orphaned `.claude/worktrees/*`
   scratch directories, structurally invisible to `scripts/scan-secrets.ps1` because it walks
   `git ls-files --exclude-standard`, which respects the `.git/info/exclude` rule hiding that path.
   Any zip/copy of the working directory for submission would leak this key.

The good news: the **web-browser-via-Cloudflare-Tunnel path is real and already proven** for
unauthenticated endpoints (INNO-MOBILE-008) and is the correct primary delivery recommendation —
it just needs the P1 hardening below (stable named tunnel, trusted-proxy rate-limit key, secure
cookies) and a full authenticated rehearsal from a separate device before relying on it for grading.

## 2. Findings ledger

Severity legend: P0 = blocks delivery entirely · P1 = judge will notice / a core journey breaks ·
P2 = quality/polish gap · P3 = minor.

### P0 — blocks delivery

- [x] **P0-1** Android APK / Tauri desktop native login cannot complete — OIDC-only auth, no public
  IdP anywhere in the delivery pipeline. (found independently by `r1-frontend-mobile`,
  `r1-deployment-public-access`, `r1-product-completeness`)
  `web/src/AuroraApp.tsx:401-403`, `web/src/components/AuthGate.tsx:63-80`,
  `web/src/mobile-auth.ts:175`, `PublicAuthConfigurationController.java`.
  Fix applied: demo-mode native session/password fallback — see §3.
- [x] **P0-2** Mock LLM path misclassifies ordinary stress/anxiety as CRISIS; emergency-contact
  reply + stuck "Crisis 10/10 STORM" mood badge that never recovers. (`r2-e2e-judge-journey`)
  `src/main/java/com/innercosmos/ai/lexicon/ChineseSentimentLexicon.java`,
  `src/main/java/com/innercosmos/ai/semantic/PseudoSemanticAnalyzer.java`,
  `src/main/java/com/innercosmos/ai/client/MockLlmClient.java`.
  Fix applied: see §3.
- [x] **P0-3** Real MiniMax key in plaintext in orphaned `.claude/worktrees/*` dirs, invisible to
  `scan-secrets.ps1`'s `git ls-files --exclude-standard` scan. (`r2-adversarial-security`)
  Fix applied: see §3 (dirs removed; scanner gap noted for follow-up; key rotation is a human gate).

### P1 — core journey breaks / judge will notice

- [ ] **P1-1** No current/committed installer artifacts — on-disk MSI/EXE/APK are gitignored and
  predate ~8 later merges; would need a fresh rebuild under time pressure. (`r1-frontend-mobile`)
  **Not closed by this session** — rebuilding fresh is an operator action right before delivery;
  already scripted (`build-demo-apk.sh`/`build-tauri.ps1`), not re-run here.
- [x] **P1-2** Free `trycloudflare.com` quick-tunnel URL is ephemeral; any restart breaks it and any
  already-built APK/shared URL. Needs the documented stable **named** tunnel setup before grading.
  (`r1-frontend-mobile`, `r1-deployment-public-access`) — DEMO-RUNBOOK.md now states this is
  required (not optional) for unattended/asynchronous grading.
- [ ] **P1-3** Only unauthenticated static endpoints were rehearsed through the tunnel
  (INNO-MOBILE-008); the actual register/login/streamed-SSE-chat journey from a physically separate
  device was never verified end to end. (`r1-deployment-public-access`) **Not closed by this
  session** — requires real hardware/network, flagged explicitly in DEMO-RUNBOOK.md as a required
  follow-up.
- [x] **P1-4** Rate limiting collapses into one shared bucket for every judge once traffic goes
  through `cloudflared` (loopback → same `getRemoteAddr()` for everyone) because
  `inner-cosmos.security.trusted-proxy-enabled` defaults false and is never turned on in the demo
  path. (`r2-adversarial-security`) — `run-demo-server.sh` now sets it true.
- [x] **P1-5** Session cookie ships without `Secure`; `CORS_ALLOWED_ORIGINS`/`SEED_ENABLED` hardening
  is undocumented for the public-tunnel scenario. (`r2-adversarial-security`) — `COOKIE_SECURE`/
  `COOKIE_SAME_SITE` now set by the demo script; documented in DEMO-RUNBOOK.md.
- [x] **P1-6** Hardcoded `admin/admin123` (+ `demo/demo123`, etc.) seed account is one
  profile/env-var mix-up away (`application-demo.yml`, `application-mysql.yml` both default
  `seed-enabled:true`) from being live on the public tunnel. (`r2-adversarial-security`) — startup
  WARNING added in `MockDataInitializer`.
- [x] **P1-7** Documented zero-Docker `./mvnw spring-boot:run` quick-start reports
  `/actuator/health` as DOWN (Redis health indicator has no local Redis) — contradicts CLAUDE.md's
  own health-check verification guidance. (`r2-e2e-judge-journey`) — `application-dev.yml` now
  disables the Redis health indicator by default.
- [x] **P1-8** README.md / README.zh-CN.md — the de facto judge entry point — never mention the
  tunnel/APK delivery path at all. (`r2-delivery-packaging`) — "For judges / graders" section added.
- [x] **P1-9** README.md, README.zh-CN.md, and CLAUDE.md disagree about which handoff doc / machine
  state file is current (READMEs point at superseded docs 21/23 and superseded state files).
  (`r2-delivery-packaging`) — both READMEs now point at CLAUDE.md / doc 24 / closure-campaign-state.yml.

### P2 — quality / polish

- [x] **P2-1** `scripts/demo/run-demo-server.sh` hardcodes `LLM_PROVIDER=glm` in its status echo
  regardless of actual (DeepSeek-default) provider; `docs/demo/DEMO-RUNBOOK.md` has 3 stale GLM
  references. Confirmed **still unfixed** across 17+ commits between round 1 and round 2.
  (`r1-backend-security`, `r1-deployment-public-access`, `r2-test-evidence-reconciliation`) —
  fixed alongside the P0-1/P1 batch.
- [ ] **P2-2** No real-AI-vs-Mock indicator in the main Aurora chat UI (only wired into
  ThoughtShredder); a silent mid-demo fallback would go unnoticed. (`r1-backend-security`)
- [ ] **P2-3** `LlmConfig.failoverClient()` gates its Mock-fallback candidate on the raw
  `allowFallback` field instead of `isEffectiveFallbackAllowed()`, a defense-in-depth gap if
  `llm.mode=prod` is ever set without the `prod` Spring profile. (`r1-backend-security`)
- [ ] **P2-4** No automated test enforces H2 `schema.sql` / Flyway parity — currently in sync by
  convention only. (`r1-backend-security`)
- [ ] **P2-5** TTS voice-synthesis endpoints hit a paid provider per call, no caching, only the
  generic rate limit. (`r1-backend-security`)
- [ ] **P2-6** Footer link (`web/src/AuroraApp.tsx:1383`) sends users to the unfinished legacy V0.1
  static dashboard (`/pages/dashboard.html`), self-labeled "tools not yet migrated". (`r1-frontend-mobile`)
- [ ] **P2-7** Production JS bundle (577 kB) exceeds Vite's 500 kB warning threshold, single unsplit
  chunk. (`r1-frontend-mobile`)
- [ ] **P2-8** Actuator `/metrics` and `/prometheus` are `permitAll` and become internet-exposed once
  tunneled. (`r1-deployment-public-access`)
- [ ] **P2-9** No mock-fallback rehearsal plan if the operator's real-provider key hits a rate
  limit/quota during the actual grading window with concurrent judges. (`r1-deployment-public-access`)
- [ ] **P2-10** The tunnel+APK judge-delivery plan is not referenced anywhere in the authoritative
  document map (对齐文档/24) or either machine ledger — undiscoverable by design. (`r1-deployment-public-access`)
- [ ] **P2-11** Capsule `safeVisibility()` fails OPEN to `PUBLIC` for any unrecognized status value
  instead of failing closed to `PRIVATE` (not reachable via current shipped frontend, but a real
  fail-open default). (`r2-e2e-judge-journey`)
- [ ] **P2-12** Malformed/invalid-UTF-8 JSON bodies return 500 instead of 400 —
  `GlobalExceptionHandler` has no `HttpMessageNotReadableException` handler. (`r2-e2e-judge-journey`)
- [ ] **P2-13** Repo HEAD advances mid-verification (another autonomous session commits concurrently)
  — no lock/coordination mechanism; snapshot/tag a fixed commit before actual grading.
  (`r2-test-evidence-reconciliation`)
- [ ] **P2-14** Acceptance-ledger top-level `last_reconciled_commit`/`reconciliation_boundary` is
  ~17-18 commits stale (undercount, honest direction) vs. current HEAD; per-gate notes are more
  current than the header. (`r1-product-completeness`, `r2-test-evidence-reconciliation`, ×2 duplicate framings)
- [ ] **P2-15** Emotion baseline (EWMA) is a real, tested vision feature with zero acceptance-ledger
  coverage and no explicit user-facing surface. (`r1-product-completeness`)
- [x] **P2-16** Any self-registered (non-admin) user can read full `/actuator/health` component
  details incl. raw DB exception text — `show-details/show-components: when_authorized` with no
  `management.endpoint.health.roles` configured, so any authenticated (not just admin) principal
  qualifies. (`r2-adversarial-security`) — `management.endpoint.health.roles: ADMIN` added.
- [ ] **P2-17** Replayed memory/summary/history blocks bypass the JSON-escaping used for live user
  input in `PromptBuilder`, relying only on a soft instruction against prompt injection.
  (`r2-adversarial-security`)
- [ ] **P2-18** No feature walkthrough/demo script exists for judges (W4 explicitly not started per
  `closure-campaign-state.yml`). (`r2-delivery-packaging`)

### P3 — minor

- [ ] **P3-1** TTS/voice endpoints have no caching (see P2-5, same root).
- [ ] **P3-2** No CSP/HSTS headers set by Spring Security. (`r2-adversarial-security`)
- [ ] **P3-3** One high-severity transitive `npm audit` finding (`fast-uri`, build-time only, not
  shipped in runtime bundle). (`r1-frontend-mobile`, confirmed unchanged `r2-test-evidence-reconciliation`)
- [ ] **P3-4** Android release AAB has no signing config (moot — delivery correctly uses debug APK).
  (`r1-frontend-mobile`)
- [ ] **P3-5** Full backend test suite requires a forced Surefire kill 30s after `System.exit(0)` —
  a non-daemon thread/resource isn't shut down cleanly. (`r2-test-evidence-reconciliation`)
- [ ] **P3-6** `run-teacher-demo.ps1` is loopback-only/PowerShell-only and not cross-referenced with
  the real tunnel path in README. (`r2-delivery-packaging`)
- [ ] **P3-7** Per-client rate-limit key trust in `X-Forwarded-For` unverified against cloudflared's
  actual forwarding behavior (same root cause as P1-4). (`r1-deployment-public-access`)

## 3. Remediation log

(Newest first. Each entry: finding id(s), what changed, commit.)

- **P1-4, P1-5, P1-6, P1-7, P1-8, P1-9, P2-16** (2026-07-24), bundled with the P0-1 fix since they
  touch the same demo-delivery/security surface:
  - `scripts/demo/run-demo-server.sh` now also exports `COOKIE_SECURE=true`,
    `COOKIE_SAME_SITE=none`, `INNER_COSMOS_SECURITY_TRUSTED_PROXY_ENABLED=true` (so cloudflared's
    `X-Forwarded-For` is honored and rate limits are per-visitor, not one shared bucket for every
    judge — P1-4), and `MANAGEMENT_HEALTH_REDIS_ENABLED=false`; a comment documents why it must
    never be combined with `SPRING_PROFILES_ACTIVE=demo`/`mysql` or `SEED_ENABLED=true` (P1-6).
  - `src/main/resources/application.yml`: `same-site`/`secure` cookie flags made configurable via
    `COOKIE_SAME_SITE`/`COOKIE_SECURE` env vars (default unchanged: `lax`/`false`); added
    `management.endpoint.health.roles: ADMIN` so `show-details/show-components: when_authorized`
    actually means admin-only instead of any self-registered user (P2-16, was silently satisfied
    by any authenticated principal with no roles list configured).
  - `src/main/resources/application.yml`: added a new `dev`-profile-gated YAML document
    (`spring.config.activate.on-profile: dev`) setting `management.health.redis.enabled: false`
    and dropping `redis` from the readiness health group's `include` list, so the
    CLAUDE.md-documented zero-Docker `./mvnw spring-boot:run` quick-start reports a healthy
    `/actuator/health` instead of a false DOWN (P1-7). **Not** placed in
    `src/main/resources/application-dev.yml` — that file is gitignored (a personal/local override,
    confirmed via `git ls-files`/`.gitignore`), so a fix living only there would never reach a
    fresh clone. Verified live: booted with the exact documented command
    (`./mvnw spring-boot:run`, no env vars) — `/actuator/health` and `/actuator/health/readiness`
    both now report `UP` (previously `DOWN`); first attempt without also adjusting the readiness
    group's `include` list hit a Spring Boot startup validation failure ("Included health
    contributor 'redis' in group 'readiness' does not exist"), corrected before re-verifying.
  - `src/main/java/com/innercosmos/config/MockDataInitializer.java`: logs a loud startup WARNING
    (not a hard-fail, to avoid breaking legitimate LAN-only classroom demos) when
    `inner-cosmos.demo.seed-enabled=true` is active alongside a non-localhost
    `CORS_ALLOWED_ORIGINS` — the one signal available at this layer that the hardcoded
    `admin/admin123` seed account might be reachable from the public internet (P1-6).
  - `README.md` / `README.zh-CN.md`: added a "For judges / graders" section near the top pointing
    at `docs/demo/DEMO-RUNBOOK.md` (P1-8); fixed the header link and the "Coding Agent bootstrap"
    section in both files, which pointed at superseded docs 21/23 and superseded state files
    instead of `CLAUDE.md` / doc 24 / `closure-campaign-state.yml` (P1-9).
  - `docs/demo/DEMO-RUNBOOK.md`: documented the new APK login path and its env-var dependency,
    strengthened the stable-named-tunnel guidance for unattended/asynchronous grading (P1-2),
    added an explicit "verify on a real device" call-out (P1-3), and fixed the stale
    `LLM_PROVIDER=glm` references (P2-1).
  - Verified: full `./mvnw test` re-run after all of the above — **1186/1186, 0 failures, 1
    pre-existing skip, BUILD SUCCESS**; targeted `DemoDataConfigurationTest`/
    `AuroraConstitutionServiceTest` (touching the seeding path) green.
  - **Not done in this pass** (documented as required follow-ups, not silently claimed): P1-1
    (rebuild fresh APK/installer artifacts immediately before the actual delivery — an operator
    step, already scripted via `build-demo-apk.sh`/`build-tauri.ps1`) and P1-3's actual physical
    device rehearsal (requires real hardware, cannot be done by an agent in this environment).

- **P0-2** (2026-07-24): `PseudoSemanticAnalyzer.categorizeSentiment()` (Mock LLM's lexicon-based
  sentiment classifier) now only returns `CRISIS` when a genuine severe signal is present (real
  `SELF_HARM` intent, or a single lexicon word independently scored ≤ -4 via
  `hasSevereNegativeWord()`) — never from additively summing several merely-mild negative words
  (the original bug: "焦虑"=-3 + "压力"=-2 clamped to -5 and read as CRISIS). This fixes both
  symptoms the audit reproduced: `MockLlmClient`'s emergency-support canned reply (gated on
  `sentimentLabel=="CRISIS"`) and the `GET /api/aurora/mood` widget getting permanently stuck at
  "Crisis 10/10 STORM" for the rest of a session (root cause: `EmotionTraceListener` re-analyzes
  the *whole accumulated session text* on every turn, so one mundane crisis-sounding word anywhere
  in history kept re-triggering the old threshold). Also fixed a real, independently-discovered bug
  in `ChineseSentimentLexicon`: a duplicate `"绝望"` entry (HashMap.put semantics silently
  downgraded it from the intended -4/severe to -3/moderate), which would otherwise have weakened
  the new severe-word signal. Added regression tests in `PseudoSemanticAnalyzerTest`
  (`ordinaryStressPhraseNeverClassifiesAsCrisis`, `singleSevereWordAloneStillClassifiesAsCrisis`).
  Verified: targeted tests green (46/46 incl. Safety/EmotionBaseline/MockLlmClient suites), full
  `./mvnw test` re-run clean (see below), no existing test referenced the old `CRISIS` threshold.
- **P0-1** (2026-07-24): Native (Capacitor Android / Tauri desktop) builds no longer hard-require
  OIDC/PKCE when built with `VITE_DEMO_MODE=true` (the flag `scripts/demo/build-demo-apk.sh`
  already sets). `web/src/AuroraApp.tsx`'s bootstrap effect, its `AuthGate` render call, and its
  `logout()` handler now all gate the native/OIDC path on `native && !demoModeBuild` instead of
  bare `native`, so a demo build falls back to the same session-cookie username/password flow the
  web build uses (`bearerRequired=false`) — closing the "unbreakable OIDC sign-in wall" P0. This
  surfaced a real secondary gap: the demo APK's WebView origin (`https://localhost`, per
  `web/capacitor.config.json`'s `androidScheme`) is cross-site relative to a tunneled backend
  origin, so the session cookie needs `SameSite=None` (requires `Secure=true`) to survive that
  cross-origin request — `same-site: lax` was hardcoded. Made both configurable
  (`COOKIE_SAME_SITE`/`COOKIE_SECURE` env vars, default unchanged: `lax`/`false` for plain local
  dev) in `application.yml`, and set them (plus `INNER_COSMOS_SECURITY_TRUSTED_PROXY_ENABLED=true`
  for correct per-judge rate-limit keys through cloudflared, and
  `MANAGEMENT_HEALTH_REDIS_ENABLED=false` fixing P1-7's false health-DOWN) in
  `scripts/demo/run-demo-server.sh`. Updated `docs/demo/DEMO-RUNBOOK.md` to describe the new APK
  login path, its exact env-var dependency, and explicitly flag that **a real logged-in journey on
  a physical Android device has not yet been rehearsed** — this is a code-level fix proven by
  `tsc`/vitest/build, not yet proven end-to-end on hardware; treat that rehearsal as a required
  follow-up before relying on the APK for actual grading. Also fixed the stale
  `LLM_PROVIDER=glm` echo in the same script (P2-1) since it was touched anyway. Verified: frontend
  `tsc --noEmit` clean, `npx vitest run` 522/522 (72 files, unchanged — `AuthGate.test.tsx`'s
  existing prop-driven tests are unaffected since only the caller's computed prop changed),
  `npm run build` clean rebuild committed.
- **P0-3** (2026-07-24): Removed the 3 orphaned scratch dirs actually leaking the plaintext
  MiniMax key (`agent-a4b3735dc7a583de4`, `agent-a554c0d0477ebd4e3`, `agent-a7afd36cb89549855` —
  confirmed no `.git` metadata, not in `git worktree list`, stale May-26 checkouts) plus one
  clean orphaned sibling from the same batch (`agent-a5d4a6b53ec014f9f`). Additionally removed
  all 6 *registered* worktrees under `.claude/worktrees/` via `git worktree remove --force` after
  confirming every one of their HEAD commits is already fully merged into `codex/w0-integration`
  (they were the four W0 "locked sources" plus two already-merged W0V/P1 branches) — this was
  needed because the scanner fix below immediately surfaced a known, already-fixed-on-HEAD
  false-positive test fixture (`sk-abc123...` placeholder) living in one of those stale worktree
  checkouts. Confirmed via `git branch --contains` that every removed worktree's commit is
  reachable from `codex/w0-integration` before removing. Also patched `scripts/scan-secrets.ps1`
  to explicitly walk `.claude/worktrees/**` on the filesystem (bypassing `--exclude-standard`,
  which had structurally hidden this whole path from the current-tree scan) so a stray checkout
  can never hide a live credential from the scanner again. Verified: `scripts/scan-secrets.ps1`
  now PASSes with 0 findings; broadened a manual check across all sibling
  `D:/code/inner-cosmos-*` worktrees for the same hardcoded-key pattern — none found. **Key
  rotation for the exposed MiniMax key is a human gate** (the user must rotate it; an agent
  cannot revoke a third-party provider credential) — not yet done, flagged to the user.
