# INNO-INNER-015 — live HTTP verification of the voice stack (fresh boot, real TTS)

## What this is

An end-to-end live verification that the voice feature works over **real HTTP** (not just unit
tests), against a **fresh boot of the current integrated HEAD**, with **real Qwen TTS**. This is the
"make sure everything works well" check for the user-facing voice stack the operator asked for.

## Setup (2026-07-24)

Booted a fresh Spring Boot instance from `codex/w0-integration` HEAD on port **8090** (in-memory H2,
Mock chat so it boots offline, **real Qwen TTS** via the operator's key, read at runtime from the
gitignored `API及文档.txt`):

```
SERVER_PORT=8090 SPRING_DATASOURCE_URL=jdbc:h2:mem:voiceverify ... TTS_ENABLED=true TTS_API_KEY=… \
  ./mvnw spring-boot:run
-> Started InnerCosmosApplication in 7.54 seconds (Tomcat on :8090)
```

## Driven journeys (real HTTP, real registered user)

1. **CSRF + register** — `GET /api/v1/auth/csrf` then `POST /api/auth/register` → **HTTP 200**,
   user created (the live CSRF contract works).
2. **Voice catalog** — `GET /api/me/tts/voices` (authenticated) → **HTTP 200**, returns all 6
   presets with bilingual labels + per-voice preview text (warm_gentle_female / calm_steady_female
   / deep_soothing_male / bright_young_female / bright_young_male / warm_expressive_female).
3. **Real on-demand synthesis** — `POST /api/me/tts/preview {voiceId:"bright_young_male"}` →
   **HTTP 200**, returned a `data:audio/mpeg;base64,...` payload; decoded to **71144 bytes** of
   audio with magic bytes `49 44 33` = **"ID3"** (a valid MP3 ID3 header) — i.e. genuine
   Qwen-synthesized speech through the live REST endpoint, not a stub.

## What this proves vs not

**Proven:** the current HEAD boots cleanly into a running server; the auth/CSRF/register flow works
live; the voice-picker catalog endpoint serves the 6 presets to an authenticated user; the
on-demand synthesis endpoint returns real, valid MP3 audio synthesized by the real provider over
HTTP. This is the live integration of the voice stack end-to-end (controller → service →
`QwenAudioTtsClient` → Aliyun WebSocket → base64 response).

**Not re-driven here:** the streaming `inner_voice` SSE path (proven separately by
`AuroraInnerVoiceEnabledStreamTest` + the real-provider `QwenAudioTtsClientRealProviderTest`), and
the capsule-voice / slow-letter-voice endpoints (proven by their controller tests). A live
multi-turn SSE drive remains a follow-up (it needs the dual-kernel streaming path, not just REST).

The 8090 instance was stopped after verification; no key value was committed.
