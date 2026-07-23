# INNO-INNER-014 — capsule persona-voice reuse (TTS beyond Aurora)

## What this is

The product's real-time TTS capability (`ai/tts`: `TtsClient` / `QwenAudioTtsClient` /
`DisabledTtsClient` / `TtsVoicePresets`) was built for Aurora's "inner voice" (心声, see
INNO-INNER-013). This record proves the SECOND reuse the user asked for: a visitor chatting with an
Echo Capsule can now HEAR the capsule's reply spoken aloud, in a voice **distinct from Aurora's**
warm/feminine defaults — reinforcing "this is a different persona, not Aurora."

Bounded scope: on-demand (tap-to-play) synthesis of the visitor's most recent capsule reply, in a
fixed capsule persona voice. No new auth surface; no autoplay; no second audio player.

## 1. Distinct capsule persona voice catalog (`CapsuleVoicePresets`)

A separate, isolated catalog from Aurora's `TtsVoicePresets`, so capsule persona voices never appear
in the Aurora settings picker and vice-versa. Each entry's (model, voice) pair was confirmed
synthesizing during the INNO-INNER-013 spike and was **not** already used by Aurora's catalog:

| id | label | model | voice |
|---|---|---|---|
| `capsule_calm_neutral` | 平和回声 · 书 | `cosyvoice-v2` | `longshu_v2` |
| `capsule_deep_steady` | 沉稳低音 · 远 | `cosyvoice-v2` | `longyuan_v2` |

Descriptive labels reflect Aliyun's published voice-persona naming conventions for these system
voices, not an independent listening review (same honest disclosure as INNO-INNER-013).

`QwenAudioTtsClient.resolvePreset` now resolves a voice id across **both** catalogs (Aurora +
capsule), so the capsule path reuses the same committed `TtsClient` rather than wrapping a second
one. Pinned by `QwenAudioTtsClientVoiceResolutionTest` and the disjoint-catalog assertions in
`CapsuleVoicePresetsTest`.

## 2. Backend synthesis path

The capsule-chat transport is a **synchronous POST** (`PersonaChatController` / `PersonaChatService`),
not an SSE stream like Aurora. The least-surface-area option was therefore a dedicated on-demand REST
endpoint rather than a new SSE event:

- `POST /api/persona-chat/session/{id}/voice` → synthesizes the visitor's most recent CAPSULE reply
  in the session, in `CapsuleVoicePresets.defaultVoice()`, and returns the audio as an inline
  base64 data URI `{"audio":"data:audio/mpeg;base64,..."}` — exactly mirroring the established
  `POST /api/me/tts/preview` audio contract.
- Why a REST endpoint (not extending the reply VO / entity): `PersonaChatMessage` is a persistent
  entity; adding an `audio` field would bloat the DB and force every visitor to pay TTS latency even
  if they never tap play. A dedicated on-demand endpoint adds zero latency to the chat turn, is
  tap-to-play by construction, and is one new method + one new route.
- Resilience (mirrors Aurora's `inner_voice` try/catch in `AuroraAgentServiceImpl.stream()`):
  `PersonaChatServiceImpl.synthesizeVoice` wraps synthesis in try/catch; on any failure or bounded
  timeout (`tts.timeout-ms`, default 8s) it throws a clean `BusinessException(AI_PROVIDER_ERROR)`,
  never a raw 500. Because the audio call is separate from `POST /message`, a synthesis failure
  **never** breaks the chat — the text reply was already delivered.

## 3. Owner/visitor authorization (reuses the existing gate, never bypasses it)

`synthesizeVoice` reuses the same gates `reply()`/`create()` already enforce:

1. **Ownership** — calls the existing private `requireOwnedSession(userId, sessionId)` (the same
   helper `report()`/`block()` use): a visitor may only synthesize audio for a session they own.
2. **Published-capsule** — reapplies the EXACT condition from `create()` and `finalizeAiTurn()`'s
   `stillEligible` check: `Boolean.TRUE.equals(capsule.isPublic) && "PUBLIC".equals(capsule.visibilityStatus)`.
   A withdrawn or needs-review capsule cannot be heard aloud, even if the visitor already received
   the text reply earlier.

Both gates are pinned by `PersonaChatServiceImplSynthesizeVoiceTest` (unit, stubbed `TtsClient`) and
`PersonaChatVoiceControllerTest` (integration: 401 anonymous, `AI_PROVIDER_ERROR` when no provider,
`CAPSULE_WITHDRAWN` for a withdrawn capsule, `NOT_FOUND` when there is no capsule reply yet).

## 4. Real-provider proof of the actual committed client

`QwenAudioTtsClientCapsuleVoiceRealProviderTest` (tagged `@Tag("real-provider")`, excluded from the
default `./mvnw test` gate by the same `excludedGroups` convention) drives the real, committed
`QwenAudioTtsClient` against the real network for every capsule preset:

```
export TTS_API_KEY=<redacted>
export TTS_WS_URL=wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference
./mvnw test -Dtest=QwenAudioTtsClientCapsuleVoiceRealProviderTest -DexcludedGroups=
```

Result (2026-07-24 run): **1 test, 0 failures.** Committed secret-free report at
`real-provider-report.json`:

| voice | model / providerVoice | bytes |
|---|---|---|
| `capsule_calm_neutral` | `cosyvoice-v2` / `longshu_v2` | 63202 |
| `capsule_deep_steady` | `cosyvoice-v2` / `longyuan_v2` | 83682 |

Both synthesized real non-empty MP3 on the first try this run (no transient timeout observed here;
INNO-INNER-013 documents that Aliyun-side WebSocket latency can cause a per-voice timeout that
**differs across runs** and is handled by the production try/catch + omit-on-failure path).

## Honest scope — proven vs not

**Proven:** both committed capsule persona voices authenticate and synthesize real non-empty MP3 via
the real committed Java client over the private-gateway WebSocket; the broadened `resolvePreset`
resolves Aurora + capsule ids (and rejects unknown); every authorization gate (ownership,
published-capsule, no-reply, unavailable, synthesis-failure) is unit- and integration-pinned; the
REST contract returns the same base64-data-URI shape as `POST /api/me/tts/preview`, and fails clean
(`AI_PROVIDER_ERROR`) rather than 500 when no provider is configured.

**Not attempted this round (deferred):** a perceptual/listening review of the capsule persona voices
(labels follow Aliyun's naming, as disclosed); letting the visitor pick among capsule voices (the
backend selects a fixed default — bounded); replaying earlier (non-latest) capsule replies (the
endpoint synthesizes the most recent CAPSULE message only); a second, separate real-network
integration of the full controller→service→client chain (the client and each gate are independently
proven, mirroring INNO-INNER-013's judgment that this is sufficient).
