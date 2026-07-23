# FINAL-E2E-001 — W2 golden web journeys + visual matrix

Machine evidence for `G7.UX-COMPLETE` and `G9.FINAL-E2E` on the integrated HEAD
(voice feature + demo + capsule-voice + slow-letter). The plan's §6 explicitly
classifies browser/E2E runs and visual checks as **agent machine work**, not human
gates — this directory is that machine work.

## How to reproduce

Boot a fresh dev backend (Mock chat, no key required) and point the suite at it:

```bash
# backend (any free port; in-memory H2 + demo seed)
java -jar target/inner-cosmos-0.1.0.jar \
  --server.port=8091 --server.address=127.0.0.1 \
  --inner-cosmos.demo.seed-enabled=true --spring.task.scheduling.enabled=false \
  '--spring.datasource.url=jdbc:h2:mem:w2e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1'

# voice journeys (web/e2e/voice-features.spec.ts)
INNER_COSMOS_BASE_URL=http://127.0.0.1:8091 npx playwright test voice-features --reporter=list

# visual matrix (web/scripts/golden-visual-matrix.mjs)
INNER_COSMOS_BASE_URL=http://127.0.0.1:8091 node web/scripts/golden-visual-matrix.mjs
```

## Voice-stack golden journeys — `web/e2e/voice-features.spec.ts` (4/4 green)

| Journey | What it proves |
|---|---|
| voice picker renders 6 presets + instant-saves a pick | `TtsVoicePresets.ALL` (6 voices) reaches the UI; the instant-save PATCH path is error-free |
| voice preview tap → observable response, no page error | Mock has no real TTS key, so preview must **fail gracefully** (busy/audio/inline-error), never crash |
| slow-letter tap-to-play affordance renders | `▶ 朗读这封信` is shown on a delivered letter (W1 slow-letter voice reuse) |
| Aurora turn completes with inner-voice path wired | The `inner_voice` SSE event handling does not derail the normal turn (`在这里` status, bubble renders) |

Verified directly via API: `GET /api/me/tts/voices` returns exactly the 6 presets
(warm_gentle_female · 小春, calm_steady_female · 婉儿, deep_soothing_male · 成然,
bright_young_female · 悦悦, bright_young_male · 昂扬, warm_expressive_female · 安欢),
`currentVoiceId=warm_gentle_female`, `innerVoiceEnabled=true`, `innerVoiceMode=AMBIENT`.

## Visual matrix — `screenshots/` (20 files, 0 real console errors)

Five product spaces × {zh-CN, en-SG} × {desktop 1280×800, mobile 390×844}.
File naming: `<space>-<locale>-<viewport>.png`.

| Space | zh-CN desktop | zh-CN mobile | en-SG desktop | en-SG mobile |
|---|---|---|---|---|
| Aurora (今天 / Today) | ✓ | ✓ (reduced-motion) | ✓ | ✓ |
| Cosmos (内宇宙 / Cosmos) | ✓ | ✓ | ✓ | ✓ |
| Resonance (共鸣 / Resonance) | ✓ | ✓ | ✓ | ✓ |
| Letters (连接 / Connect) | ✓ | ✓ | ✓ | ✓ |
| Me/Settings (我的 / Me) | ✓ | ✓ | ✓ | ✓ |

Each file is a real render (158–433 KB at the correct dimensions; a blank/error
page is ~5–20 KB). The only console message is a single benign `401` on
`/api/dialog/session/create` during bootstrap (the app probes auth then recovers);
`voice-features.spec.ts` separately asserts **zero unhandled page errors** on the
same journeys. en-SG localization was independently confirmed (Me-space voice
heading "Aurora's voice" + 6 presets render in English).

## Honest notes / deferred

- The narrow (390 px) captures also emulate `prefers-reduced-motion: reduce`,
  proving the reduced-motion path does not break layout — not a separate WCAG audit.
- This is a render/voice-journey snapshot, not a WCAG 2.2 AA assistive-tech review,
  a perf-budget run, or a non-author blind-experience panel — those remain the
  human-gated items called out in `UX-COMPLETE.remaining`.
