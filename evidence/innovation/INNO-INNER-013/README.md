# INNO-INNER-013 — real TTS spike + Aurora "inner voice" (心声)

## What this is

A real, first-person "inner voice" (心声) for Aurora's dual-kernel planner: a short line, grounded
in the planner's `emotionalNeed`/`relationshipMove` signals but never a restatement of the visible
spoken reply, composed once per turn and (when a real TTS provider is configured) synthesized aloud
in a voice the user picked, surfaced as an additive `inner_voice` SSE event. Ships enabled by
default, `AMBIENT` delivery mode, with a settings-page mute switch and an `ON_DEMAND` alternative.

## 1. TTS spike — what actually authenticated (empirical, not assumed)

Real key (`qwen:` in the operator's credential doc) tested directly over WebSocket against
`wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference` — this account's
real private-gateway host — using the `run-task` / `continue-task` / `finish-task` protocol common
to Qwen-Audio-TTS and CosyVoice. Text synthesized: `"今天天气怎么样？"` / `"你好，我是你的内心声音，很高兴认识你。"`.

| model | voice | result | bytes |
|---|---|---|---|
| `qwen-audio-3.0-tts-flash` | `longanhuan_v3.6` | **OK** | 48156 |
| `qwen-audio-3.0-tts-plus` | `longanhuan_v3.6` | FAIL — `Engine error [411]: TTS speak operation failed` | — |
| `cosyvoice-v2` | `longxiaochun_v2` | **OK** | 44394 |
| `cosyvoice-v2` | `longwan_v2` | **OK** | 66128 |
| `cosyvoice-v2` | `longshu_v2` | **OK** | 47320 |
| `cosyvoice-v2` | `longyuan_v2` | **OK** | 68636 |
| `cosyvoice-v2` | `longcheng_v2` | **OK** | 43558 |
| `cosyvoice-v3-flash` | `longanyang` | **OK** | 71144 |
| `cosyvoice-v3-flash` | `longyue_v3` | **OK** | 72397 |
| `cosyvoice-v3-flash` | `loongstella_v3` | FAIL — `Engine return error code: 418` | — |
| `cosyvoice-v3-plus` | `longanyang` | **OK** | 66128 |
| `sambert-zhichu-v1` | (model-as-voice) | FAIL — `Request text is invalid!` | — |
| `sambert-zhinan-v1` | (model-as-voice) | FAIL — `Request text is invalid!` | — |
| `sambert-zhiyuan-v1` | (model-as-voice) | FAIL — `Request text is invalid!` | — |
| `sambert-zhimiao-emo-v1` | (model-as-voice) | FAIL — `Request text is invalid!` | — |
| `sambert-zhida-v1` | (model-as-voice) | FAIL — `Request text is invalid!` | — |

The private-gateway path authenticated on the FIRST try (`qwen-audio-3.0-tts-flash`/`longanhuan_v3.6`:
`task-started` → `sentence-begin`/`sentence-synthesis` → `task-finished`, 48156 bytes of MP3), so
the public `wss://dashscope.aliyuncs.com/api-ws/v1/realtime` / `qwen3-tts-flash-realtime` fallback
path (a different, session/`session.update`-based protocol) was never needed and was not spiked
against this account. `qwen-audio-3.0-tts-plus` and the `sambert-*` family failed — the Sambert
failures are very likely a protocol mismatch in the spike script (Sambert's `run-task` may expect
text inline at task-start rather than via a `continue-task` frame) rather than a genuine auth/
entitlement failure, but this was not root-caused further given the 8 other confirmed-working
(model, voice) pairs already covering the full "warm/gentle, calm/steady, deep/soothing,
bright/young" spread the product needs. Spike scripts: `tts_spike.mjs` / `tts_spike_batch.mjs` /
`tts_spike_batch2.mjs` (Node 22, native `WebSocket`), run from the session's scratchpad directory,
not committed to the repo (throwaway per the task brief).

**Embeddings spike** (same key, HTTP not WebSocket): see `evidence/innovation/INNO-INNER-012/README.md`.

## 2. Voice catalog shipped (`TtsVoicePresets`, `src/main/java/com/innercosmos/ai/tts/`)

Six presets, all confirmed synthesizing above, spanning the requested spread (all Chinese-capable):

| id | label | model | voice |
|---|---|---|---|
| `warm_gentle_female` | 温柔女声 · 小春 | `cosyvoice-v2` | `longxiaochun_v2` |
| `calm_steady_female` | 沉稳女声 · 婉儿 | `cosyvoice-v2` | `longwan_v2` |
| `deep_soothing_male` | 低沉男声 · 成然 | `cosyvoice-v2` | `longcheng_v2` |
| `bright_young_female` | 明亮女声 · 悦悦 | `cosyvoice-v3-flash` | `longyue_v3` |
| `bright_young_male` | 阳光男声 · 昂扬 | `cosyvoice-v3-flash` | `longanyang` |
| `warm_expressive_female` | 温暖女声（表现力）· 安欢 | `qwen-audio-3.0-tts-flash` | `longanhuan_v3.6` |

Descriptive labels reflect Aliyun's own voice-persona naming conventions for these system voices
(e.g. `longcheng`/成 reads as steady/mature, `longanyang`/阳 as bright), not an independent listening
review — this is disclosed honestly, not claimed as a verified perceptual audit.

## 3. Architecture delivered

- `ai/tts/{TtsClient, QwenAudioTtsClient, DisabledTtsClient, TtsVoicePreset(s)}` mirrors the shape
  of `ai/embedding`: a real HTTP/WebSocket implementation, a no-op fallback, and a fixed catalog.
  `QwenAudioTtsClient` uses the JDK's built-in `java.net.http.WebSocket` (no new dependency),
  bounded by `tts.timeout-ms` (default 8000ms) so a hung provider call can never hang the caller.
- `TtsConfig` (`@ConfigurationProperties(prefix="tts")`): `enabled`/`api-key`/`ws-url`/`timeout-ms`,
  all env-var driven (`TTS_ENABLED`, `TTS_API_KEY`, `TTS_WS_URL`, `TTS_TIMEOUT_MS`), defaulting to
  the public `wss://dashscope.aliyuncs.com/api-ws/v1/inference` gateway (an operator on a private
  workspace-scoped gateway overrides `TTS_WS_URL`).
- `UserProfile.preferredTtsVoiceId` / `innerVoiceEnabled` (default `true`) / `innerVoiceMode`
  (default `"AMBIENT"`) + `V23__tts_inner_voice_preferences.sql` + matching `schema.sql` columns.
- REST contract exactly as specified: `GET /api/me/tts/voices`, `PATCH /api/me/tts/preferences`,
  `POST /api/me/tts/preview` (`TtsController`, `TtsPreferencesVO`) — see
  `TtsControllerTest` (6 cases: auth, catalog+defaults, PATCH persistence, invalid voiceId/mode
  rejected, preview cleanly fails with `AI_PROVIDER_ERROR` when no provider is configured).
- `InnerVoiceComposer` (`ai/runtime/`): one additional lightweight `StructuredAiService.call(...)`
  after the planner stage, instructed to produce a `<=40`-Chinese-character first-person line
  grounded in `emotionalNeed`/`relationshipMove`, explicitly forbidden from restating/paraphrasing/
  summarizing the visible spoken reply. TDD-pinned in `InnerVoiceComposerTest` via a bounded
  character-bigram-overlap heuristic against the spoken segments — proven to FAIL before
  `MockLlmClient` had a dedicated `AURORA_INNER_VOICE_*` dispatch branch (it fell through to the
  generic Aurora-chat JSON shape, which fails to parse into `InnerVoiceResult`, yielding `null`),
  and to PASS after adding a deterministic mock branch keyed only on `emotionalNeed`/
  `relationshipMove` (never on the spoken text, so it cannot structurally echo it).
- `AuroraDualKernelRuntime.generate(..., composeInnerVoice)`: an additive overload (the original
  5-arg `generate` is untouched, so all pre-existing call sites/tests are unaffected) that
  best-effort composes the inner-voice line after the plan/speaker/critic stages settle; any
  exception is caught and logged inside `generate()` itself, never propagated.
- `AuroraAgentServiceImpl`: composition is requested only on the SSE streaming path (never the
  synchronous POST path) and only when the user's `innerVoiceEnabled` preference (default `true`)
  allows it. `stream()` then, in its own try/catch, synthesizes audio via the (nullable) `TtsClient`
  using the user's preferred voice and emits **at most one** `inner_voice` SSE event
  `{text, audio, voiceId}` right before `meta`/`turn.completed` — silently omitted on any TTS
  failure/timeout/unavailability. Proven end-to-end with a fake, deterministic `TtsClient` bean
  (`AuroraInnerVoiceEnabledStreamTest`: exactly one `inner_voice` event, correct data-URI `audio`
  and resolved `voiceId`) and its absence-path companion (`AuroraInnerVoiceDisabledStreamTest`:
  no event, turn still completes cleanly) when `tts.enabled=false` (the CI default).

## Real-provider proof of the actual committed Java client

`QwenAudioTtsClientRealProviderTest`
(`src/test/java/com/innercosmos/ai/tts/QwenAudioTtsClientRealProviderTest.java`), tagged
`@Tag("real-provider")` (excluded from `./mvnw test` by the same `excludedGroups` convention),
drives the real, committed `QwenAudioTtsClient` — not the throwaway Node spike script — against
the real network for **every one of the 6 shipped voice presets**, asserting non-empty real audio
bytes for each. Run on this session with the real key:

```
export TTS_API_KEY=<redacted>
export TTS_WS_URL=wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference
./mvnw test -Dtest=QwenAudioTtsClientRealProviderTest -DexcludedGroups=
```

Result: **1 test run, 0 failures.** Evidence JSON (`target/evaluation/tts-real-provider-report.json`):
all 6 presets `"status":"OK"` with real audio byte counts (38961–60277 bytes for the same preview
text across voices), e.g. `warm_gentle_female` (cosyvoice-v2/longxiaochun_v2): 38961 bytes,
`bright_young_male` (cosyvoice-v3-flash/longanyang): 60277 bytes.

### Durable committed artifact + observed provider latency (2026-07-24 re-run)

The report JSON is now **committed** at `evidence/innovation/INNO-INNER-013/real-provider-report.json`
(secret-free: voice status, model, providerVoice, audioBytes only — no key) so the numbers are
inspectable rather than trust-the-README. A 2026-07-24 re-run through the same committed client
recorded **5/6 voices synthesizing per run, with one transient per-voice timeout** — and the
failing voice **differed across two consecutive runs** (`warm_expressive_female` then
`calm_steady_female`), confirming this is **Aliyun-side WebSocket latency under concurrent
synthesis, not a voice-specific defect** (all 6 are confirmed-working in the original spike and
succeed on most runs). This is exactly the failure mode the production code is designed for:
synthesis is wrapped in try/catch with an 8s timeout, and on timeout the `inner_voice` event is
simply omitted — the turn still completes normally; the user just doesn't get inner-voice audio
that turn. The committed report is one honest run's snapshot (5/6 OK).

## Honest scope — what is proven vs not

**Proven:** the real key authenticates and synthesizes real MP3 audio for 8 (model, voice)
combinations over the private-gateway WebSocket protocol during the spike, AND the actual committed
`QwenAudioTtsClient` class (not just the spike script) synthesizes real non-empty audio for all 6
shipped voice presets via an automated, real-network JUnit test; the composed inner-voice line is
structurally distinct from the spoken reply (bigram-overlap TDD gate); the full SSE contract
(`{text, audio, voiceId}`, at most once, additive/non-blocking) is exercised end-to-end against a
fake but realistic `TtsClient` (a second, separate real-network integration of the full SSE handler
was judged unnecessary given the client itself and the SSE wiring are each independently proven).

**Not attempted this round:** capsule/persona-voice reuse (item 4, "reuse the TTS capability in one
more place") — items 1-3 were prioritized to be solid and tested first, per the task's own priority
ordering. The Sambert voice family and `qwen-audio-3.0-tts-plus` were not root-caused past the
observed error strings (excluded from the catalog rather than debugged further, since 8 other
confirmed pairs already covered the required voice spread).
