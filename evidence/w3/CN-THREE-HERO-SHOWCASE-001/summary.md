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
