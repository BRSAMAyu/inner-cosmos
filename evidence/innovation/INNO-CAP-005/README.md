# INNO-CAP-005 — Explainable multi-strategy resonance

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience contract implemented

- A visitor can switch among Mirror, Complement, Growth Edge, Serendipity and Contextual matching
  without leaving the Resonance Network.
- Each strategy changes the deterministic relevance signal and ordering, not only its label. Every
  result carries the selected strategy, a human label, a short explanation and strategy-specific
  reasons.
- Cold-start backfill remains explicit and never claims high resonance. Owner-private data stays
  behind the existing public capsule projection and all strategies reuse the same visibility,
  withdrawal and self-exclusion filters.
- Growth Edge explains a directional bridge such as `任务压力 → 希望期待`; Contextual prefers
  current portrait themes; Serendipity is deterministic and visibly exploratory.

## Verification at checkpoint

- `CapsuleMatchingTest`: `16/16 PASS`, including four strategy-specific ranking/explanation cases.
- React Vitest protocol suite: `3/3 PASS`; TypeScript production build: `PASS`.
- Full Playwright suite: `9/9 PASS` in 45.6 seconds. The cross-account journey switches through
  all five strategies before continuing through conversation, slow-letter delivery and withdrawal.
- Visual QA: [`resonance-strategy-switching.png`](resonance-strategy-switching.png) was inspected
  at full-page scale. The active Growth Edge control, strategy explanation, explicit cold-start
  language and resulting cards remain readable without overlap.

## Honest remaining work

This is an explainable deterministic baseline, not learned matchmaking. Real member feedback,
diversity calibration, strategy outcome measurement and independent blind acceptance are still
required before `MATCH-MULTI` can pass.
