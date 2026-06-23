# Runtime Findings — from booting + exercising the app (2026-06-23 16:46–17:27)

App **boots OK**: "Started InnerCosmosApplication in 3.259s" on :8080, profile **dev**, H2 **file** DB (`jdbc:h2:file:./data/innercosmos`). Java 21, Spring Boot 3.3.6.

## ✅ What actually WORKS (verified in browser-equivalent conditions)
- App compiles & starts in ~3s. Scheduled jobs (`LetterDeliveryJob`, `AuroraProactiveJob`, capsule-sync retry) tick.
- **Login** demo/demo123 → 200. **All core GET endpoints return 200** when cookies are tracked across requests (as a browser does): `/api/auth/current`, `/api/memory/starfield`, `/api/memory/cards`, `/api/user/profile`, `/api/aurora/modes`, `/api/dashboard/summary`, `/api/capsule/my`, `/api/letters/inbox`, `/api/emotion/timeline/today`, `/api/portrait`, `/api/dialog/session/create`.
- `/api/aurora/greeting` → 200 with a rich, memory-referenced Aurora greeting (userId=2) via a graceful non-LLM fallback.
- `MockDataInitializer` seeds 10 well-written personas + 4 users.

## 🔴 Real issues confirmed by real runtime calls
1. **[P0/P1] Core chat throws HTTP 500 under the shipped default config — the "no-API-key demo" is broken.**
   - Reproduced: `login`→200, `/api/dialog/session/create`→200 (sessionId=3), **`/api/aurora/message` → 500 `INTERNAL_ERROR` ("服务器内部错误")**.
   - Root cause: `src/main/resources/application.yml:99-100` ships `llm.mode: ${LLM_MODE:prod}` + `provider: ${LLM_PROVIDER:minimax}`. Prod mode force-disables fallback; all provider keys are invalid/expired (401: MiniMax "login fail", GLM "Invalid API Key"/"令牌已过期", DeepSeek "Authentication Fails" — seen at boot). The chat path's `FailoverLlmClient` throws `AiProviderException` → GlobalExceptionHandler maps to 500.
   - *Some* paths degrade gracefully (greeting → non-LLM fallback; proactive nudge → hardcoded "你好，今天过得怎么样？"), but the **core chat does not** — so a user who opens the app and talks to Aurora gets a 500.
   - Contradicts README + AGENTS.md ("默认 Mock LLM，无 API Key 也可演示核心闭环"; AGENTS even claims `LLM_PROVIDER` default = `mock`).
   - **Fix (Phase 3):** default `mode: ${LLM_MODE:dev}` and/or `provider: ${LLM_PROVIDER:mock}` so the no-key demo works end-to-end; reconcile the docs; (optionally) give the chat path a graceful fallback like greeting has. **Re-verify by re-running the message call after the fix.**
2. **[P2] Spring Security emits a generated default password** → `UserDetailsServiceAutoConfiguration` defaults active alongside the custom `JwtAuthenticationFilter`. Cosmetic / hardening (verify nothing relies on the default inMemory user).
3. **[P2] Schema migrations silently swallow "bad SQL grammar"** on several `ALTER TABLE ADD COLUMN` (H2 MODE=MySQL) — `SchemaM0M6/M2/M4Initializer`. Logged DEBUG + skipped. Harmless only if columns already exist via `schema.sql`; the silent-swallow is a robustness smell.
4. **[P2] Doc drift:** README/AGENTS say "H2 In-Memory" + "18 pages"; actual is H2 *file* + 35 pages.

## ⚠️ CORRECTED — earlier "403 on core endpoints / session-create" was a curl artifact, NOT a bug
Initial sweeps showed ~19 core endpoints + `/api/dialog/session/create` returning **403**. **Cause was test-tool error:** I sent `-b cookie` without `-c` on follow-up requests, so when Spring Security performed **session-fixation migration** (issuing a new `JSESSIONID` after the first authenticated request), the jar kept the **stale** session → subsequent calls 403'd. A browser accepts the new `Set-Cookie` automatically. Re-tested with `-b` AND `-c` on every call (browser-equivalent): **all endpoints return 200, including session/create.** Session-fixation migration is **expected, benign** security behavior. *Lesson:* always track cookie updates (`-b` + `-c`) when testing session-auth apps.

## Real-world verification status
- [x] App boots; login; all core GET endpoints 200 (browser-equivalent).
- [x] Greeting path works (graceful LLM fallback).
- [x] **Core chat `/api/aurora/message` → 500** under shipped default (the headline bug, confirmed).
- [ ] Re-verify core chat returns 200 **after** the Phase-3 config fix.
- [ ] Full browser walkthrough (register→login→chat→memory→starfield→capsule→plaza→slow-letter) — deferred to Phase 4/6 (test the FIXED state).

## Request shapes (for later testing)
- `SessionCreateRequest`: `{ title?, sessionType="AURORA_CHAT" }`
- `ChatRequest` (`@Valid`): `sessionId` (@NotNull), `message` (@NotBlank), `inputType="TEXT"`, + rich context (audio metrics, `emotionHint`, `mode`, weather, location, `aiProviderPreference`, …)
- **Auth test note:** use `curl -b <jar> -c <jar>` on every call (or a real browser) — the app migrates the session cookie after first auth.
