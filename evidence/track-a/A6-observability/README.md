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

## 4. Remaining / not yet proven

- Live-turn emission through the fully-wired Spring context (counter increments on a real chat turn)
  is exercised by any full-context chat integration test at the merge node; a dedicated
  assert-the-counter-incremented `@SpringBootTest` would make it explicit.
- The rest of A6 remains open: OpenTelemetry distributed spans (not just metrics), failure-injection
  for timeout/429/malformed/partial-stream with a defined user-visible degradation contract, and
  executable domain boundaries (Spring Modulith / ArchUnit).
