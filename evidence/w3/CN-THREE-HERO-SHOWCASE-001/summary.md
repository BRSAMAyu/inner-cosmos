# CN-THREE-HERO-SHOWCASE-001 — three-scene classroom rehearsal

Date: 2026-07-27
Environment: local `kind-kubedeploy`, namespace `inner-cosmos-w3`, real PostgreSQL 16 + Redis, Mock AI

## Presenter contract

The classroom story is deliberately limited to three short, causally visible scenes:

1. **Continuity** — an Aurora SSE starts on one API Pod; that Pod is deleted; the turn reaches
   `COMPLETED`; another Pod returns the same durable history with one user message and one or more
   committed Aurora messages.
2. **Business-pressure elasticity** — an isolated synthetic outbox backlog appears; KEDA changes
   the worker deployment from one replica to multiple replicas; backlog drains; consumer receipts
   remain unique; all synthetic rows are removed.
3. **Explainability** — finishing the isolated dialog creates a fresh W3C trace context; Jaeger
   shows one trace spanning `inner-cosmos-api` and `inner-cosmos-worker`; the trace contains no
   forbidden body, prompt, completion, user-id, or DB-statement tags.

The executable entry point is:

```powershell
.\scripts\demo\run-three-hero-showcase.ps1 -Scene Preflight
.\scripts\demo\run-three-hero-showcase.ps1 -Scene All -HoldViews
```

`Preflight` is read-only and fails closed unless the current context is exactly
`kind-kubedeploy`. `All` uses unique account/outbox identities, owns its localhost
port-forwards, and cleans the account, consumer receipts, synthetic events, temporary KEDA
configuration, and processes in `finally`.
`-HoldViews` keeps the printed Grafana and Jaeger URLs alive until the presenter presses Enter;
cleanup still runs afterward.

## Live rehearsal

### Continuity

The final cross-Pod implementation was re-proven separately in
`CN-ZERO-LOSS-DRAIN-003`: graceful deletion completed turn 10 in 1.233 seconds; direct JVM
`SIGKILL` plus reconnect completed turn 12 in 16.677 seconds, with one distinct user message and
two distinct Aurora bubbles. The classroom default uses graceful deletion because it proves the
user-facing contract without a node-level PID operation.

### KEDA

A clean-baseline rehearsal inserted 3,000 isolated
`system.outbox-smoke-probe.v1` events:

- worker desired replicas visibly changed `1 → 3 → 6`;
- ready backlog visibly fell `3000 → 2875 → 975 → 725 → 450 → 150 → 0`;
- all 3,000 events reached `PUBLISHED`;
- 3,000 receipts existed, with zero duplicate `(event_id, handler_name)` pairs and zero failed
  events;
- cooldown returned the deployment `6 → 3 → 1`;
- cleanup removed exactly the 3,000 receipts and 3,000 synthetic outbox rows and restored the
  manifest-defined worker environment.

### Observability

An isolated `ACTION_SPLIT` dialog finish persisted traceparent
`00-3a79adb6a69a1be1fc4beab83f66375b-56310ed02537846f-01`.
Jaeger returned:

- trace ID `3a79adb6a69a1be1fc4beab83f66375b`;
- 8 spans across `inner-cosmos-api` and `inner-cosmos-worker`;
- HTTP finish, authorization/security, `inner.cosmos.outbox.consume`,
  `inner.cosmos.projection.memory`, and `inner.cosmos.projection.profile`;
- zero forbidden privacy tags.

Browser visual QA confirmed that Grafana provisions all five Inner Cosmos dashboards, that the
KEDA dashboard renders ready events, oldest age, worker replicas and dead letters together, and
that the Jaeger trace renders 2 services, depth 4 and 8 spans.

### Final single-entry rehearsal

The final `-Scene All` entry point passed from a clean baseline in one run:

- Hero 1: deleted `inner-cosmos-api-f746fd46d-n46fg`; SSE completed; the Service returned
  one user message and one committed Aurora message; the deployment returned to two APIs.
- Hero 2: KEDA visibly changed desired workers `1 → 3 → 6`; 3,000 events reached
  `PUBLISHED` in 101 seconds; duplicate receipts remained zero; cleanup restored one worker
  and removed all synthetic rows.
- Hero 3: fresh trace `8443ecb17602eb925b916bcef414642e` contained 8 spans across
  `inner-cosmos-api` and `inner-cosmos-worker`; Prometheus reported two available API replicas;
  the privacy scan found zero forbidden tags.
- Terminal result: `ALL_THREE_HERO_SCENES_PASS`; isolated account cleanup passed.

## Fast stage direction

- **0:00–0:35** — continuity terminal: point only at `stream=STARTED`, Pod deletion, completed
  history.
- **0:35–1:20** — KEDA dashboard: point at backlog and worker replicas moving in opposite
  directions. Do not wait silently for scale-in; explain that cooldown is deliberate.
- **1:20–1:50** — Jaeger: point at the API root span, worker consumer span, and two projection
  spans. End on `forbidden_tags=0`.

## Claim boundary

This is a real local kind multi-Pod experiment with real PostgreSQL, Redis, Prometheus, Grafana,
KEDA, OpenTelemetry Collector and Jaeger. It proves application/runtime semantics, not regional
failover, managed EKS capacity, production persistence of Jaeger, or live external-model quality.
The kind profile uses the labelled Mock provider. The current trace is a real
conversation-finish-to-worker-projection tree, not a fabricated single tree covering every later
embedding, matching, capsule, and slow-letter scheduler phase.

## 2026-07-28 final H2/H3 closure rehearsal

Environment: `kind-kubedeploy`, namespace `inner-cosmos-w3`, source baseline
`916991db` plus the presenter-script closure in this change.

Two consecutive H2 scale-out rehearsals passed:

- run 1 reached desired/available worker `6/3` in `25,872 ms`;
- run 2 reached desired/available worker `6/3` in `33,287 ms`;
- both were below the 40-second machine gate and the 45-second presenter budget;
- the final run drained `1,200/1,200` events, produced `1,200/1,200` inbox receipts,
  reported `duplicate_receipts=0`, removed every synthetic row, and restored worker baseline `1`.

Two consecutive H3 rehearsals also passed. The final closure run:

- completed in `28,561 ms`, below the 60-second presenter gate;
- used stable presenter endpoints `8081`, `16686`, `9090`, and `3000`;
- produced trace `89d2d4740f9e7cefa75b279aef0305cc`;
- showed `21` spans across `inner-cosmos-api` and `inner-cosmos-worker`;
- included the HTTP Aurora request, `aurora.turn`, memory retrieval, provider call,
  dialog finish, outbox consume, memory projection, and profile projection;
- measured `13,890 ms` client end-to-end, `8,733 ms` provider time, and `5,107.6 ms`
  worker consume time;
- reported `forbidden_tags=0` and cleaned the isolated demo account.

The final script now fails closed on observability deployment readiness and KEDA
`ScaledObject` readiness, reuses the fixed live-showcase ports when available, treats cleanup or
post-scale invariants as command failures, and prints explicit `H2_PRESENTER_READY` /
`H3_PRESENTER_READY` markers.

## 2026-07-28 frozen classroom backup

The final runtime snapshot was rechecked on `kind-kubedeploy` with API `2/2`, worker `1/1`,
`LLM_PROVIDER=gemini`, `GEMINI_MODEL=gemini-3.6-flash`, Mock fallback disabled and memory
embedding disabled. This provider note applies to the final 2026-07-28 H1/H3 runs; it does not
retroactively change the Mock-AI boundary of the older 2026-07-27 rehearsals above.

The offline backup is now self-contained in the repository:

- machine-readable result: `final-results-2026-07-28.json`;
- raw H1 terminal log: `h1-live-final-run-2026-07-28.txt`;
- H1 restored client and Grafana screenshots;
- H2 Grafana screenshot;
- H3 Jaeger screenshot;
- public Demo screenshots for lived-in stories, Aurora, memory, resonance and connections;
- presenter deck: `docs/demo/HERO-EXPERIMENT-OFFLINE-BACKUP.html`;
- claim/evidence/new-experiment matrix: `docs/demo/EXPERIMENT-EVIDENCE-PACK.md`.

The H3 screenshot shows `31` total spans because account-cleanup requests reused the same injected
trace context after the script had already passed its required `21`-span application contract.
Jaeger's `Incomplete` badge describes the unexported external client root span; the rehearsal
independently failed closed unless every API, Aurora, provider, memory, outbox and worker projection
span required by the claim was present.
