# Track A — A6 privacy-safe AI turn observability

> Status: BUILDER_VERIFIED_IN_PROGRESS
> Binds to: `docs/goal/complete-product-acceptance.yml` G8 `OPS-OBSERVABILITY` ("AI cost/quality signals")
> and Track A workstream A6 ("privacy-safe AI spans/metrics").

## 1. What was implemented

A dedicated, privacy-safe per-turn metric pair emitted for every Aurora turn and exposed through the
existing `/actuator/prometheus` endpoint:

- `aurora.turn.count` — counter of turns produced.
- `aurora.turn.latency` — timer of end-to-end produce-reply latency.

Both are tagged **only** with bounded, low-cardinality, non-sensitive dimensions:
`route` (chat | boundary-refusal), `runtime` (single-pass.v1 | dual-kernel.v1 | …), `provider`
(glm | deepseek | minimax | mock | …), `mode` (DAILY_TALK | …), `fallback` (true|false),
`memory_referenced` (true|false). It never records message text, prompts, retrieval content, memory
ids or the user id.

## 2. Files

- `src/main/java/com/innercosmos/ai/observability/AiTurnMetrics.java` — the metrics component
  (Micrometer `MeterRegistry`, already on the classpath via `micrometer-registry-prometheus`).
- `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — wired via the same
  `@Autowired(required = false)` optional-injection idiom the class already uses for
  `dualKernelRuntime`/`choreographyService` (Spring always wires it; null only in the two
  constructor-level legacy unit tests, where `recordTurnMetrics` is a no-op). Records on both the
  main produce-reply completion (route=chat, captures fallback + memory-referenced + latency) and the
  hard-boundary-refusal early return (route=boundary-refusal).
- `src/test/java/com/innercosmos/ai/observability/AiTurnMetricsTest.java` — unit tests.

## 3. Verification

- `./mvnw test -Dtest=AiTurnMetricsTest,AuroraEmergenceTest,AuroraStreamServiceTest`
  → **Tests run: 8, Failures: 0, Errors: 0**.
  - `AiTurnMetricsTest` (2): counter+timer recorded with the declared tags and correct latency; the
    meter tag KEYS are a fixed allowlist (no `userId`/`message`/`content`), and null/blank inputs are
    normalised to `unknown`, not dropped — the privacy-safety regression.
  - `AuroraEmergenceTest` (3) + `AuroraStreamServiceTest` (3): the two constructor-level unit tests
    that build `AuroraAgentServiceImpl` directly still pass with `aiTurnMetrics == null`, proving the
    no-op guard.

## 4. Executable domain boundaries (follow-up)

The modular monolith's layering is now enforced by a test, so cross-layer drift fails the build in
review instead of being discovered later. Rather than add an ArchUnit/bytecode dependency (and a
network fetch the offline build can't do), this is a **dependency-free** source-scanner:

- `src/test/java/com/innercosmos/architecture/DomainBoundaryArchitectureTest.java` (4 tests) walks
  `src/main/java/com/innercosmos/<layer>` and asserts no `import com.innercosmos.<forbidden>.`
  statements. Rules verified to hold today: `entity` imports none of
  service/controller/mapper/vo/dto/ratelimit/idempotency/scheduler/streaming (pure persistence);
  `mapper` ↛ service/controller; `service` ↛ controller; `safety` ↛ controller. Violations are
  reported as `file -> import line`.
- `./mvnw test -Dtest=DomainBoundaryArchitectureTest` → **4/4**.

## 5. User-visible degradation contract (follow-up)

The turn-failure → user-message mapping is now an isolated, failure-injection-tested contract instead
of inline string-matching buried in a 1200-line service:

- `src/main/java/com/innercosmos/ai/runtime/AiFailureContract.java` — `classify(Throwable)` walks the
  whole cause chain and keys off exception TYPE and message, returning
  `TIMEOUT | RATE_LIMITED | MALFORMED_OUTPUT | PROVIDER_UNAVAILABLE`; each carries the stable historical
  risk flag (TIMEOUT/RATE_LIMITED/PARSE_ERROR/NETWORK_ERROR) and a calm default user message.
- `AuroraAgentServiceImpl.differentiatedFallback` now delegates to it; `PROVIDER_UNAVAILABLE` keeps the
  state-aware message (VS-004 coherence). Behavior preserved — the two service tests that exercise the
  fallback (`AuroraEmergenceTest`, `AuroraStreamServiceTest`) stay green.
- `src/test/java/com/innercosmos/ai/runtime/AiFailureContractTest.java` (6 tests): timeout by
  type/message/nested-cause, 429/rate-limit, malformed (parse + `JsonParseException`), safe default for
  unknown/null, stable flags, and no infinite loop on a self-referencing cause.

`./mvnw test -Dtest=AiFailureContractTest,AuroraEmergenceTest,AuroraStreamServiceTest` → **12/12**.

## 6. Privacy-safe AI spans via the Observation API (follow-up)

Distributed AI-turn **spans** are now emitted through Micrometer's Observation API — which is already on
the compile classpath (no new *main* dependency), and which Spring Boot turns into real OpenTelemetry
spans as soon as a tracing bridge is configured:

- `src/main/java/com/innercosmos/ai/observability/AiTurnObservation.java` — emits an `aurora.turn`
  observation per turn with the same bounded, non-sensitive attributes as the metrics (route, runtime,
  provider, mode, fallback, memory_referenced) plus a coarse `duration_bucket` (never a raw-ms,
  high-cardinality value). No tracer → cheap no-op; tracer present → an `aurora.turn` span.
- Wired next to `AiTurnMetrics` at the single `recordTurnMetrics` site in `AuroraAgentServiceImpl`
  (same `@Autowired(required=false)` idiom; both are no-ops when unwired).
- `src/test/java/com/innercosmos/ai/observability/AiTurnObservationTest.java` (3) — uses
  `TestObservationRegistry` to assert the observation is started/stopped with the declared bounded
  attributes, normalises null inputs, never emits a `userId`/`message`/`content`/`duration_ms` key, and
  buckets latency coarsely. Added `micrometer-observation-test` (test scope) to the pom.

## 7. Remaining / not yet proven

- Turning the `aurora.turn` observation into an exported OTel span in prod is a **deploy/config** step
  (add a tracing bridge — e.g. `micrometer-tracing-bridge-otel` + an exporter endpoint — and point it at
  a collector). The instrumentation + span contract are done and tested here; wiring an actual exporter
  is environment configuration, not code.
- Live-turn emission through the fully-wired Spring context is exercised by any full-context chat
  integration test at the merge node.
- Partial-stream-reconnect degradation remains open.
