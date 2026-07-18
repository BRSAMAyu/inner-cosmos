# A1 — Living Aurora Runtime (Track A / Living Intelligence)

Status: **IN_PROGRESS**. This session fixed the concrete bug A0 found, added a permanent
regression gate for it, and added a first declared-demo-budget latency check. It did not attempt
the full A1 gate (blind-review package, multi-message pacing/interruption ablation depth, full
proactive-quality loop) — see "What is still missing" below.

## 1. The bug this session fixed

A0's real-provider smoke evidence (`evidence/track-a/A0-quality-laboratory/README.md` §4) found,
reproduced identically on GLM, DeepSeek and MiniMax: `AuroraDualKernelRuntime.planInstruction()`
and `speakerInstruction()` did not embed an inline JSON schema example the way the single-pass
instruction used in that smoke test does. Every real provider deviated from the expected
`AuroraPlanResult`/`AuroraResult` field names on the first attempt every time
(`badOutputEventsInThisCall: 2` per dual-kernel run, all three providers), even though the
repair-retry/critic path always recovered a non-fallback answer.

### Fix

`src/main/java/com/innercosmos/ai/runtime/AuroraDualKernelRuntime.java` — `planInstruction()`,
`speakerInstruction()`, and (for the same class of robustness, since it returns a nested
`AuroraResult` inside `repaired`) `criticInstruction()` now each embed a literal JSON schema
example matching their real result classes (`StructuredAiResults.AuroraPlanResult`,
`StructuredAiResults.AuroraResult`, `StructuredAiResults.AuroraCriticResult`), mirroring the style
already used by `PromptBuilder.withOutputSchema()` for the single-pass path. No behavior, control
flow, or protected capability was removed: the critic/repair-retry path, `needsCritic` triggers,
interruption handling (`interruptionContext` → "接受打断并按新方向重规划"), the
`normalizePlan`/`qualityIssues` guards, and `runtimeMode` single-vs-dual switch are all unchanged —
only the instruction *text* passed to the model changed.

## 2. Before / after measurement (real providers, not Mock)

Same harness, same two dev scenarios (`TA-SES-DEV-01` short_emotional_support,
`TA-CRISIS-DEV-01` crisis_safe_degradation), same three real providers, single-pass and
dual-kernel variants, `allowFallback=false` so a provider failure would throw rather than silently
substitute Mock.

| Provider | Variant | `badOutputEventsInThisCall` BEFORE | AFTER |
|---|---|---|---|
| GLM | dual-kernel | 2 (both scenarios) | **0** (both scenarios) |
| DeepSeek | dual-kernel | 2 (both scenarios, ai-lab session data not separately filed; see A0 README §4 "reproduced identically across all three") | **0** (both scenarios) |
| MiniMax | dual-kernel | 2 (both scenarios) | **0** (both scenarios) |
| all three | single-pass | 0 (unaffected — single-pass already embedded a schema example) | 0 (unchanged) |

Before evidence: `evidence/track-a/A0-quality-laboratory/real-provider-smoke/real-provider-smoke-{glm,deepseek,minimax}.json`
(unmodified — kept as the historical "before" record).
After evidence: `evidence/track-a/A1-living-aurora/real-provider-smoke-after/real-provider-smoke-{glm,deepseek,minimax}.json`
(this session, post-fix).

`visibleResultIsDeterministicFallback` was `false` in every row both before and after — the
repair-retry path was already masking the defect from end users; the fix's value is removing
reliance on that extra round-trip (see latency below), not correctness of the visible reply.

### Latency side-effect (informational, not a formal claim)

GLM dual-kernel latency dropped noticeably once the repair-retry round-trip was no longer needed
on the first attempt (illustrative, n=1 per cell, real network variance applies):

| Provider | Scenario | Variant | BEFORE latencyMs | AFTER latencyMs |
|---|---|---|---|---|
| GLM | TA-SES-DEV-01 | dual-kernel | 27970.49 | 7137.33 |
| GLM | TA-CRISIS-DEV-01 | dual-kernel | 29823.82 | 16250.57 |

(DeepSeek/MiniMax before/after both stayed in the same rough single-digit-to-low-double-digit
second range; GLM's pre-fix runs happened to hit the repair-retry path with unusually slow
round-trips this session, so the improvement there is the most visible but should not be read as a
precise, repeatable delta — re-run before relying on an exact number.)

## 3. New permanent regression gate

`src/test/java/com/innercosmos/evaluation/TrackARealProviderSmokeEvaluationTest.java` now asserts,
for every dual-kernel `CALLED` row: `badOutputEventsInThisCall == 0`. If a future prompt or runtime
change reintroduces schema drift on a real provider, this manually-run (`real-provider`-tagged,
excluded from the default gate) suite will fail with a message naming the provider/scenario, not
silently regress. This directly turns the A0 finding into a durable A1 test.

## 4. New declared demo latency budget (A1 §5 partial)

The same test now declares and checks generous per-call latency ceilings — `30_000ms` for
single-pass, `60_000ms` for dual-kernel (dual-kernel is 2-3 sequential real model calls: plan,
speaker, and a conditional critic, so it is expected to cost strictly more, not match single-pass
latency call-for-call; TRACK-A-LIVING-INTELLIGENCE.md §5 asks for non-inferiority and staying
within a *declared* budget, not latency parity). It also reports a nearest-rank P95 over this run's
own samples (`latencyBudget.singlePassLatencyP95Ms` / `dualKernelLatencyP95Ms` in each provider's
JSON report) as an evidence figure. This session's observed dual-kernel P95 ranged 9.96s (DeepSeek)
to 22.67s (MiniMax) — comfortably inside the declared ceiling with headroom, but this is **not**
yet the spec's "first-visible-message latency" (the runtime returns a complete `Generation`
synchronously; it does not yet expose a distinct timestamp for when the first bubble becomes
visible to a streaming client — see "What is still missing").

## 5. What is still missing before A1 can be called COMPLETE

- **First-visible-message latency is not separately instrumented.** The current runtime
  (`AuroraDualKernelRuntime.generate`) is a synchronous call chain (plan → speaker → optional
  critic) that returns one complete `Generation`; there is no intermediate timestamp for "first
  bubble visible to the user" distinct from "entire turn finished," so today's latency budget
  measures total latency only. Instrumenting true first-visible-message timing would require
  either streaming the speaker's first segment before the critic runs, or plumbing a
  segment-level timestamp through the SSE path — a real runtime change, not just a test change.
- **No formal non-inferiority statistical test.** §5's "dual/adaptive system is non-inferior to
  the strong single-pass baseline overall and wins clearly on continuity/proactive/interruption
  subsets" needs a scored comparison across many scenarios with a documented statistic (e.g. a
  paired win/tie/loss count or a non-inferiority margin), not just "the critic caught scripted
  flaws" (which A0's `TrackARuntimeAblationEvaluationTest` already demonstrates structurally) and
  not just "badOutputEventsInThisCall dropped to 0" (which is a correctness/parse-quality
  finding, not a relational-quality judgment).
- **Blind-review pairwise package** (A1 §5's explicit human gate) does not exist yet. This
  remains a human gate per `docs/goal/tracks/track-a-status.yml`.
- **Adaptive dual-kernel budget policy** (§5: "Dual kernel is adaptive, not mandatory. Simple
  turns should remain fast; high-ambiguity, high-continuity or high-risk turns may spend more
  budget.") is not implemented as a policy — `AuroraDualKernelRuntime.enabled()` is a global
  single/dual switch (`inner-cosmos.aurora.runtime`), not a per-turn adaptive decision. This is
  the next highest-value A1 lead: today the same "dual" mode always spends the full plan +
  speaker (+ conditional critic) budget, regardless of how simple the turn is.
- **Multi-message pacing/delay** (bubble-by-bubble delay/pause timing between `segments`) is not
  separately measured here; this session did not find or add a dedicated pacing test.
- **Interruption/replan** already has one scripted regression scenario
  (`interrupted_response` in `TrackARuntimeAblationEvaluationTest`, offline/deterministic); it was
  not extended this session with additional interruption variants (e.g. mid-plan interruption vs
  mid-speaker interruption, or an interruption arriving during the critic pass).
- **P0 privacy/factual-boundary regression coverage** relies on the existing A0 scripted
  scenarios (unauthorized_memory, forgotten_memory_reference, diagnostic_claim); no new P0 case
  was added this session.

## 6. Reproducing this evidence

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw test -Dtest=TrackARuntimeAblationEvaluationTest,TrackAMemoryAuthorityAblationEvaluationTest,TrackACapsuleGenomeAblationEvaluationTest,TrackAProactiveDecisionEvaluationTest
# real-provider (manual, needs credentials, never printed):
export $(grep -v '^#' .env.track-a.local | xargs)
./mvnw test -Dtest=TrackARealProviderSmokeEvaluationTest -DexcludedGroups=
# reports land in target/track-a-eval/*.json (gitignored target/; copies checked in here and under A0)
```
