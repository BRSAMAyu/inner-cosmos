# INNO-MOBILE-007 — Windows Tauri desktop shell: real runtime + driven journeys on the integrated HEAD

Date: 2026-07-24
Branch: `codex/w2-mobile-4` (worktree `D:/code/inner-cosmos-w2-mobile-4`), HEAD `0350930`.
Scope: the W1-voice-integrated HEAD. Extends INNO-MOBILE-006 (compile pre-flight) by actually
running the desktop shell and driving real journeys, and extends INNO-MOBILE-004 (real-IdP PKCE) by
re-confirming the desktop auth/PKCE code paths are intact on this HEAD.

This is a **runtime + driven-journey** proof, not a compile-only check. Every row below is backed by
a command that was actually run and an observed output captured under `logs/` or `screenshots/`.

## Environment used

- Rust/Cargo 1.95; Tauri 2 (`web/src-tauri`), warm `target/`.
- Java 21 backend (`target/inner-cosmos-0.1.0.jar`) booted on **port 8082** with in-memory H2
  (`jdbc:h2:mem:w2mobile4`), the **Mock** LLM/ASR provider, no Redis (all `REDIS_*_ENABLED=false` in
  dev, so Redis health was disabled via `--management.health.redis.enabled=false
  --management.endpoint.health.validate-group-membership=false` — Redis is not on any dev journey
  path). Boots offline, no API keys. (Port 8080 was already held by another instance; per the task I
  booted my own on a free port with isolated in-memory H2.)
- Node 22 / pnpm 11; Playwright chromium (CDP) for driving.
- WebView2 (Edge) exposes CDP when the shell is launched with
  `WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9222`.

## Source change in this checkpoint (mine)

`web/vite.config.ts`: the Vite dev proxy target is now env-driven
(`INNER_COSMOS_API_PROXY ?? "http://localhost:8080"`). Default behavior is unchanged (still 8080); it
just lets a local desktop/mobile shell point at an operator-started backend on any free loopback port
(the committed default `8080` was held by another instance during this run). No product code, no
generated bundle touched (the Supervisor owns the single `static/app/aurora/**` rebuild).

## What was driven and the observed outcome

### 1. Desktop shell compiles to a runnable Windows binary — PASS
```
cd web/src-tauri && cargo build --message-format=short
   Finished `dev` profile [unoptimized + debuginfo] target(s) in 1m 22s
```
Artifact: `web/src-tauri/target/debug/inner-cosmos.exe` (19,122,688 bytes). This is stronger than the
`cargo check` pre-flight in INNO-MOBILE-006: the shell now links into a real executable on this HEAD.

### 2. Desktop shell launches a real WebView2 window — PASS
```
INNER_COSMOS_API_PROXY=http://localhost:8082 \
WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9222 \
pnpm exec tauri dev
  VITE v8.1.4  tauri-local  ready in 390 ms   (http://localhost:5173/)
  Running DevCommand (`cargo run ...`)   Finished in 12.77s
  Running `target\debug\inner-cosmos.exe`
```
The Rust shell booted, registered its plugins (`tauri-plugin-deep-link`, `-notification`, `-opener`,
`-stronghold`, `-single-instance`, `keyring`), and opened a WebView2 window. CDP endpoint
`http://127.0.0.1:9222/json` exposes the webview page at `http://localhost:5173/`
(`logs/cdp-probe.log`).

### 3. The webview runs the real React app with Tauri internals — PASS
Driven over CDP (`scripts/cdp-probe.mjs`, captured in `logs/cdp-probe.log`):
```json
{ "url": "http://localhost:5173/", "title": "Aurora · Inner Cosmos",
  "tauri": true, "readyState": "complete" }
```
`"tauri": true` = `"__TAURI_INTERNALS__" in window`. The desktop runtime bridge (Stronghold vault,
deep-link listener, notification plugin) is active in this process.

### 4. Mobile-environment safety gate fires correctly (no configured API base) — PASS (by design)
With the `tauri-local` dev build injecting no `VITE_API_BASE_URL`, the webview's body shows the
designed gate (`AuroraApp.tsx:1049` — fires only when `mobileState.native && !hasConfiguredApiBase`):
> MOBILE ENVIRONMENT GATE — 这台设备还没有安全后端入口 — 应用壳、深链与恢复能力已经就绪，但本次
> 构建没有注入 VITE_API_BASE_URL。为避免把凭据和会话发往错误地址，Aurora 不会尝试登录。

Screenshot: `screenshots/tauri-webview-boot.png`. This is correct production behavior: a packaged
native shell must not send credentials to an unverified backend. (The Vite proxy itself does reach the
backend — `curl http://[::1]:5173/api/auth/csrf` returns a real CSRF token from the 8082 instance.)

### 5. Aurora multi-bubble streamed reply — PASS (driven through the shared frontend)
The Tauri webview cannot complete a session login without the OIDC IdP (see §8), so the logged-in chat
journey was driven through the **same React source** the webview runs, in a browser
(`mobileState.native=false`, so the gate is skipped and session-cookie auth works) against the real
8082 backend. `scripts/desktop-journey.mjs` → `logs/web-aurora-chat.log`:
- register `web_*` via the 注册 tab → composer visible → send “明天的汇报让我害怕…”
- **two** streamed Aurora reply bubbles appeared (partial→final streaming observed), zero pageerrors:
  - “先不把这项任务当成对你的评判。它现在只是太大、太近了；我们先把身体和注意力稳回这一小刻。”
  - “第一步只写一句话：你希望老师在展示结束后，还能记住这个项目最不可替代的什么？…”

Screenshot: `screenshots/web-aurora-chat.png`.

### 6. inner_voice is additive and does not break the turn (TTS off) — PASS
Raw SSE captured from the live `GET /api/aurora/stream`
(`logs/aurora-sse-raw.txt`, `logs/backend-contract-drive.log`):
```
event types: meta(1) turn.plan(1) turn.started(1) bubble.started(2) token(34)
             bubble.completed(2) segment(1) turn.completed(1) done(1)
inner_voice occurrences: 0
```
With `TTS_ENABLED=false` (Mock dev), no `inner_voice` event is emitted and the turn terminates
cleanly (`turn.completed` + `done`). This confirms the W1 inner_voice wiring is purely additive: TTS
off ⇒ no inner_voice, turn unaffected. (The SSE ids are turn-scoped `id:1:live:N`, the exact keying
the fb7d510 fix relied on.)

### 7. Turn-scoped SSE recovery + interrupt — PASS (backend contract)
Driven directly against the 8082 backend (`logs/backend-contract-drive.sh`):
- `GET /api/v1/aurora/turns/1/timeline` → full durable turn returned: `status=COMPLETED`,
  `activePlan{intent="日常分享", posture="温柔、具体、像朋友", stopCondition=ALL_BUBBLES_COMMITTED_OR_CANCELLED}`,
  and the bubbles array. This is the endpoint `useAuroraSession.recover()` polls on reconnect.
- `POST /api/v1/aurora/turns/{id}/stop` with a client `Idempotency-Key` header → returns the
  `TurnTimeline` (the v1 mutation correctly requires the key; without it:
  `IDEMPOTENCY_KEY_REQUIRED`, exactly as `api.ts`'s `needsIdempotency` auto-satisfies).

### 8. Desktop PKCE/login — IDP-GATED (code intact on this HEAD; live round-trip in INNO-MOBILE-004)
`GET /api/public/auth/mobile-oidc` on the Mock backend returns `{"enabled":false,...}`. The desktop
auth path is **OIDC-bearer-only** (`api.ts`: `bearerRequired` for native; `getCsrf()` throws
"CSRF session authentication is disabled for native clients"). So a logged-in journey inside the
WebView2 requires a real OIDC provider (Keycloak). The PKCE code paths (`web/src/mobile-auth.ts`,
`web/src/desktop-runtime.ts`, the `/api/public/auth/mobile-oidc` bootstrap, the `innercosmos://`
deep-link callback) are intact on this HEAD; the live authorization-code+PKCE round-trip against a
real Keycloak (with screenshots + logcat + a real Tauri window) was proven end-to-end in
**INNO-MOBILE-004** and is not re-run here. Running it again needs the local Keycloak stack
(`deploy/compose/desktop-local.yml`, Docker) — an operator/IdP gate, not a machine gap in this code.

### 9. Offline draft preserved across kill/relaunch and never auto-sent — PASS
`scripts/desktop-offline-draft.mjs` → `logs/web-offline-draft.log`: type a draft, wait >350ms
(saveDraft debounce flushes to the durable store — IndexedDB on web, Keystore on native), reload, the
composer restores the exact draft text; zero Aurora bubbles (the draft is never auto-sent).
Screenshot: `screenshots/web-offline-draft-restored.png`.

### 10. Logout clears the session — PASS
`scripts/desktop-recovery.mjs`: after logout the auth screen (`回到你的内宇宙`) reappears
(`logs/web-recovery.log`). (Native secure-storage wipe via Stronghold is the native-only analogue and
is not exercised in a browser; its code path is in `desktop-runtime.ts` / `mobile-auth.ts`.)

## Honest boundaries / what was NOT proven here

- **In-webview logged-in chat** is OIDC-gated (§8). The logged-in Aurora/recovery/offline-draft
  journeys are driven through the shared React frontend in a browser against the real backend, not by
  clicking inside the WebView2. The WebView2 itself is proven to launch and run the shell (§2, §3).
- **Full app kill/relaunch starts a fresh Aurora session by design** (`useAuroraSession.resumeConversation`
  only does turn-scoped `recover()` when an in-flight turn exists; a cold reload has no in-memory
  `activeTurn`/`sessionId`, so it begins a new session — the prior turn stays durable in the backend,
  proven via the timeline endpoint in §7). This is the product's intended "each visit is a fresh now"
  behavior, not a defect. `screenshots/web-aurora-recovered.png` shows this fresh-session state.
- **`tauri build` release / MSI-NSIS bundling, signing, notarization, auto-update** are not run (the
  task's machine-verifiable floor is the runnable binary, met in §1; release bundling rebuilds the
  frontend into the committed static bundle, which the Supervisor owns).
- **Android (Capacitor) emulator journeys** are not covered in this checkpoint (Tauri was the
  deterministic priority); the shared backend journeys above are common to the Android client.

## Artifacts
- `logs/`: backend boot logs, the raw SSE capture, the curl contract-drive log/script, CDP probe log,
  and the three Playwright drive logs.
- `screenshots/`: Tauri webview boot (gate), web Aurora chat, recovered/reload state, offline-draft
  restored, post-logout.
- `scripts/`: the exact CDP probe and Playwright drive scripts used (re-runnable).
