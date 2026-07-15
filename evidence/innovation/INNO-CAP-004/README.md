# INNO-CAP-004 — Visitor resonance to slow-letter journey

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience contract implemented

- The React Resonance Network explains why a match appeared and shows shared dimensions without
  popularity rankings or owner-private data.
- Before the first turn, the interface explicitly states that the visitor is talking to an
  authorized AI resonance, not a person typing live. The conversation uses the existing isolated
  persona session, durable messages, server-side daily quota, safety gate and withdrawal checks.
- Official seed capsules are clearly separated from user capsules and are rejected as a route to a
  real person, even if a client attempts to supply a receiver id.
- After a substantive response, a visitor can write their own slow letter. The browser submits only
  the public capsule id; the server resolves and validates the current public owner, so the public
  API does not expose or trust an owner user id.
- The sent letter enters the existing state machine as `SENT` with an estimated arrival time. It
  does not promise a reply or expose contact details.

## Verification at checkpoint

- Focused Java slow-letter, quota, persona and application flow gate: `33/33 PASS`, including
  forged receiver rejection and ownerless seed isolation.
- React TypeScript production build: `PASS`; Vitest protocol suite: `3/3 PASS`.
- Playwright browser suite: `9/9 PASS` in 42.6 seconds. The cross-account journey publishes a
  current owner Genome, logs in as a visitor, discovers that exact capsule, converses, sends the
  letter, returns as the owner, and withdraws the capsule.
- Visual QA: [`resonance-visitor-letter-journey.png`](resonance-visitor-letter-journey.png) was
  inspected at full-page scale. Discovery rationale, AI identity disclosure, conversation roles,
  slow-letter flight state and account isolation are simultaneously visible without overlap.

## Honest remaining work

This slice uses the existing match ranking and persona runtime. Multi-strategy controls, Genome
planner/retrieval/critics/reranking, owner inbox consent UX, refusal/block/report UI, mutual human
connection, real Provider pairwise and independent blind scoring remain required before G6 passes.
