# Runtime Findings — from booting + exercising the app (2026-06-23 16:46–18:18)

App **boots OK** (~2.7s with `fork=false`, ~3.3s forked) on :8080, profile **dev**, H2 **file** DB (`jdbc:h2:file:./data/innercosmos`). Java 21, Spring Boot 3.3.6.

## ✅ What actually WORKS (verified in browser-equivalent conditions)
- App compiles & starts fast. Scheduled jobs (`LetterDeliveryJob`, `AuroraProactiveJob`, capsule-sync retry) tick.
- **Login** demo/demo123 → 200. **All core GET endpoints return 200** when cookies are tracked across requests (as a browser does): `/api/auth/current`, `/api/memory/starfield`, `/api/memory/cards`, `/api/user/profile`, `/api/aurora/modes`, `/api/dashboard/summary`, `/api/capsule/my`, `/api/letters/inbox`, `/api/emotion/timeline/today`, `/api/portrait`, `/api/dialog/session/create`.
- `/api/aurora/greeting` → 200 with a rich, memory-referenced Aurora greeting (userId=2).
- **Core chat `/api/aurora/message` → 200** with a real, coherent Aurora reply (verified ASCII **and** UTF-8 Chinese) after the config fix below.
- `MockDataInitializer` seeds 10 well-written personas + 4 users.

## 🔧 Fixed this run
1. **[P1, FIXED & VERIFIED] Shipped LLM default (`prod`/`minimax`) contradicted the docs and broke the no-key demo.**
   - Defect: `application.yml:99-100` shipped `llm.mode: ${LLM_MODE:prod}` + `provider: ${LLM_PROVIDER:minimax}`. Prod mode force-disables fallback; all provider keys invalid (401 at boot: MiniMax/GLM/DeepSeek) → the proactive job threw `AiProviderException` and fell back to a hardcoded nudge. Contradicted README/AGENTS ("默认 Mock LLM，无 API Key 也可演示核心闭环"; AGENTS even claims `LLM_PROVIDER` default = `mock`).
   - **Fix applied:** defaulted to `mode: ${LLM_MODE:dev}` + `provider: ${LLM_PROVIDER:mock}` (matches docs; `LlmConfig` creates `MockLlmClient` for `provider=mock`, no keys needed).
   - **Verified:** `/api/aurora/message` → **200** with a real reply (ASCII + UTF-8 Chinese via `--data-binary @file`).
   - **Honesty correction:** my *original* "chat → 500" report was a **test artifact** — Windows `curl` GBK-encoded the inline Chinese, so Jackson threw `JsonParseException: Invalid UTF-8 middle byte 0xd2` *before* any LLM call. The config defect itself was real (the proactive job's `AiProviderException` under prod is direct evidence; the chat path uses the same `FailoverLlmClient`), but I over-claimed the chat symptom. A real browser sends UTF-8 and would not hit the parse error.
2. *(Not yet addressed — carried into the master report)* **[P2]** Spring Security emits a generated default password (`UserDetailsServiceAutoConfiguration` defaults active alongside the custom JWT filter) — cosmetic/hardening.
3. *(carried)* **[P2]** Schema migrations silently swallow "bad SQL grammar" on several `ALTER TABLE ADD COLUMN` (H2 MODE=MySQL).
4. *(carried)* **[P2]** Doc drift: README/AGENTS say "H2 In-Memory" + "18 pages"; actual is H2 *file* + 35 pages.

## ⚠️ Test-tool artifacts I fell for (lessons recorded)
- **"403 on 19 endpoints / session-create"** → was a curl artifact: `-b` without `-c` meant the post-migration `JSESSIONID` (Spring Security session-fixation protection) wasn't saved → stale cookie → 403. With `-b`+`-c` (browser-equivalent), all endpoints 200. Session migration is benign/expected.
- **"chat → 500"** → was a curl artifact: inline Chinese was GBK-encoded on Windows → `Invalid UTF-8 middle byte 0xd2`. Use `printf ... > file` + `--data-binary @file` for non-ASCII payloads (or a real browser).
- *Discipline for future runtime tests on Windows:* (a) always `-b <jar> -c <jar>` on every call; (b) send non-ASCII bodies from a UTF-8 file via `--data-binary @file`; (c) when a 4xx/5xx looks systemic, vary one thing (encoding, cookie, method) before concluding it's an app bug.

## Real-world verification status
- [x] App boots; login; all core GET endpoints 200 (browser-equivalent).
- [x] Greeting path works.
- [x] **Core chat `/api/aurora/message` → 200** after dev/mock config fix (verified ASCII + UTF-8). The prior "500" was a curl encoding artifact.
- [ ] Full browser walkthrough (register→login→chat→memory→starfield→capsule→plaza→slow-letter) — deferred to Phase 4/6 (test the FIXED state).

## Request shapes & test notes (for later testing)
- `SessionCreateRequest`: `{ title?, sessionType="AURORA_CHAT" }`
- `ChatRequest` (`@Valid`): `sessionId` (@NotNull), `message` (@NotBlank), `inputType="TEXT"`, + rich context (audio metrics, `emotionHint`, `mode`, weather, location, `aiProviderPreference`, …)
- **Auth:** `curl -b <jar> -c <jar>` on every call (app migrates session cookie after first auth).
- **Non-ASCII bodies:** write payload to a UTF-8 file, then `curl --data-binary @file -H "Content-Type: application/json; charset=utf-8"`.
