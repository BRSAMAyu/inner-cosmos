# W1 — slow-letter voice reuse (听慢信朗读)

## What this is

The third reuse of the product's TTS capability: when a recipient opens a **delivered slow letter**
(慢信), they can tap to hear the letter body read aloud in a warm voice. Reuses the existing
`TtsClient` + a warm preset from `TtsVoicePresets` (NO new voice catalog) and the existing shared
`InlineAudioPlayer` on the frontend (NO second player). Completes the "reuse the voice model
elsewhere" theme alongside Aurora's inner voice (心声) and the capsule persona voice.

Bounded: on-demand only (a tap-to-play extra on top of text the recipient already has), synchronous
POST returning an inline base64 MP3 data URI, exactly mirroring `POST /api/persona-chat/session/{id}/voice`.

## Backend

- **Endpoint**: `POST /api/letters/{id}/voice` → `{audio: "data:audio/mpeg;base64,..."}` —
  `LetterController.voice` (mirrors `PersonaChatController.voice`).
- **Service**: `SlowLetterServiceImpl.synthesizeVoice(userId, letterId)` — added to the
  `SlowLetterService` interface. Field-injects `TtsClient` (`@Autowired(required=false)`, exactly like
  `PersonaChatServiceImpl#ttsClient`) so the heavily-audited 11-arg constructor signature is unchanged.
- **Voice**: `TtsVoicePresets.defaultVoice()` (`warm_gentle_female` / 温柔女声·小春, `cosyvoice-v2`) —
  deliberately the SAME warm Aurora default, NOT a distinct persona voice (that distinction belongs to
  the capsule path). `QwenAudioTtsClient.resolvePreset` already resolves both catalogs, so no client
  change was needed.
- **Resilience**: bounded by `tts.timeout-ms` (default 8000ms in `application.yml`); `try/catch` →
  `BusinessException(AI_PROVIDER_ERROR)` on any synthesis failure/timeout. Never a 500. With
  `tts.enabled=false` (CI default) it returns a clean `AI_PROVIDER_ERROR`.

### Authorization (security-critical) — gates REUSED, not added

1. **Recipient-scoped access** — reuses the SAME fetch-by-id + party-membership pattern as the existing
   `getLetter()` / `reportLetter()` (only a party to the letter may act on it), narrowed to
   **recipient-only** (the sender already knows what they wrote; a third party is IDOR-rejected):
   `!userId.equals(letter.receiverUserId)` → `UNAUTHORIZED`. This is the existing recipient-scoped
   letter-read gate reapplied to a new action, NOT a new surface and NOT a bypass.
2. **Delivery-state gate** — reuses the EXACT delivered-to-recipient status set the `inbox()` query
   already treats as "arrived". That status list was extracted to a single named constant
   `DELIVERED_TO_RECEIVER_STATUSES = {DELIVERED, READ, REPLIED, DECLINED, BLOCKED, ARCHIVED}` and is now
   shared by `inbox()` and `synthesizeVoice()`, so "hearable" and "appears in the recipient's inbox"
   cannot drift apart. In-transit `SENT`/`FLYING` (and unreadable `DRAFT`) are rejected →
   `LETTER_STATE_INVALID`: a recipient can never hear (or even see) a letter before it is delivered.

A non-recipient or a not-yet-delivered letter is rejected before `TtsClient` is ever touched
(`verifyNoInteractions(ttsClient)` in the unit tests pins this).

## Frontend (minimal, reuse)

- `api.letterVoice(id)` — `web/src/api.ts`, same `TtsPreviewResult` (`{audio}`) shape as `personaVoice`.
- `useConnectionsAndLetters` — adds `letterVoiceLetterId` / `letterVoiceAudio` / `letterVoiceError`
  (one active clip at a time) + a per-letter `playLetterVoice(letter)` action guarded by
  `useBusyKeys` (so playing one letter never disables another's button).
- `LettersInbox.tsx` — renders the shared `InlineAudioPlayer` on each inbox letter's body (every inbox
  letter has already arrived). Tap-to-play by default; `autoPlay` on arrival is safe because the tap is
  the user gesture that authorizes playback (same reasoning as the capsule-voice bubble). Bilingual
  labels (zh-CN / en-SG). The voice props are OPTIONAL, so callers/tests not passing them render
  unchanged. `AccountSettings.tsx` / `AuroraConversation.tsx` were NOT touched (out of scope).

## Verification (run on this branch)

- Backend focused: `SlowLetterServiceImplSynthesizeVoiceTest` (9) + `LetterVoiceControllerTest` (4) = 13 new, all green.
- Backend full: `./mvnw test` → **Tests run: 1184, Failures: 0, Errors: 0, Skipped: 1** — BUILD SUCCESS.
- Web: `npx tsc -b` clean; `npx vitest run` → **522 passed (522)** across 72 files (4 new LettersInbox cases).
- Secrets: `scripts/scan-secrets.ps1` → **PASS: 0 findings**.

## Known boundaries / deferred

- Real-provider happy-path synthesis for a letter body is proven structurally by the stubbed-client
  unit test (asserts the warm default voice id + bytes) and by the already-shipped Aurora inner-voice /
  capsule-voice real-provider evidence (INNO-INNER-013) which exercises the SAME `TtsClient`/
  `QwenAudioTtsClient`/warm-preset path. A letter-specific real-key listening review is a human gate,
  not automated here.
- The play affordance is on the inbox (received) letters only — outbox/threads are out of scope (the
  sender reading their own text aloud is not the product intent).
- The generated web bundle under `src/main/resources/static/app/aurora/**` was intentionally NOT
  rebuilt here (Supervisor/integrator owns the bundle).
