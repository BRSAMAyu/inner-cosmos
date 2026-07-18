# A0 — Quality Laboratory (Track A / Living Intelligence)

Status: **IN_PROGRESS** (real, test-verified partial progress; see "What is still missing" below).

This directory is the evidence trail for workstream `A0-quality-laboratory` from
`docs/tracks/TRACK-A-LIVING-INTELLIGENCE.md` §4. It complements — rather than replaces — the
existing Python harness under `ai-lab/` and its evidence at `evidence/innovation/INNO-EVAL-001/002/003`.
The gap this workstream targets (per `对齐文档/20`) was that no harness actually exercised the real
Java AI runtime under configurable ablations with genuine pass/fail discrimination; the `ai-lab`
harness's adapters are deterministic fixtures that match `expected` by construction, and the one
prior ad-hoc DeepSeek run was not a repeatable ablation. This directory's suites exercise real
production code (`AuroraDualKernelRuntime`, `MemoryRetrievalService`, `CapsuleGenomeService`,
`CapsuleSandboxService`, `AliveDecisionEngine`) end to end.

## 1. Scenario catalog (dev + frozen held-out, 13 types)

Source of truth: `src/test/resources/evaluation/track-a-scenario-catalog-v1.json`.

TRACK-A-LIVING-INTELLIGENCE.md §4 lists scenario families in prose: "short emotional support,
ambiguous need, action request, disagreement, user correction, interrupted response, long-gap
return, quiet-hours, changing preference, crisis-safe degradation, capsule fact/style/boundary and
data withdrawal." We interpret "capsule fact/style/boundary" as **two** composite capsule scenario
types (fact fidelity; style+boundary fidelity together) to land on exactly 13 distinct types, which
also matches this workstream's own two capsule-ablation axes. Each of the 13 types has one
`development` instance and one `frozen_held_out` instance (26 scenario instances total). Held-out
instances are loaded and scored by the same suites but their expectations were authored once,
before any run, and are never adjusted after seeing results — matching the split policy documented
in the catalog file itself (`splitPolicy` key).

| # | Scenario type | Ablation surface | Test suite |
|---|---|---|---|
| 1 | short_emotional_support | single-pass vs dual-kernel | `TrackARuntimeAblationEvaluationTest` |
| 2 | ambiguous_need | single-pass vs dual-kernel (critic catches over-diagnosis) | same |
| 3 | action_request | single-pass vs dual-kernel | same |
| 4 | disagreement | single-pass vs dual-kernel (critic catches invalidation) | same |
| 5 | user_correction | single-pass vs dual-kernel + memory authority | same + `TrackAMemoryAuthorityAblationEvaluationTest` |
| 6 | interrupted_response | single-pass vs dual-kernel (critic catches repeated cancelled plan) | `TrackARuntimeAblationEvaluationTest` |
| 7 | long_gap_return | proactive gate (recency) | `TrackAProactiveDecisionEvaluationTest` |
| 8 | quiet_hours | proactive gate (quiet hours) | same |
| 9 | changing_preference | proactive gate (cadence) | same |
| 10 | crisis_safe_degradation | single-pass vs dual-kernel (critic catches missing safety escalation) + real-provider smoke | `TrackARuntimeAblationEvaluationTest` + `TrackARealProviderSmokeEvaluationTest` |
| 11 | data_withdrawal | single-pass vs dual-kernel + memory authority | `TrackARuntimeAblationEvaluationTest` + `TrackAMemoryAuthorityAblationEvaluationTest` |
| 12 | capsule_fact_fidelity | static-card vs dynamic-Genome | `TrackACapsuleGenomeAblationEvaluationTest` |
| 13 | capsule_style_boundary_fidelity | static-card vs dynamic-Genome | same |

## 2. Ablation runner

Four JUnit suites under `src/test/java/com/innercosmos/evaluation/` (and one under
`src/test/java/com/innercosmos/ai/proactive/`) form the ablation runner, wired into the normal
`./mvnw test` gate (no special flag needed — they run offline/deterministically by default):

- **`TrackARuntimeAblationEvaluationTest`** — single-pass vs dual-kernel. Uses a per-scenario
  scripted `LlmClient` (deterministic, offline) that plays the plan/speaker/critic/single-pass
  roles. For 6 of the 8 runtime scenario types the scripted speaker candidate carries a
  deliberate, observable flaw (a diagnostic claim, an unauthorized-memory reference, a repeated
  cancelled plan, a missing safety escalation, or a forgotten-memory reference); the single-pass
  path has no critic and ships the flaw verbatim, while the dual-kernel critic catches and repairs
  it. This is asserted directly, not scored lexically: `expectedSinglePassBaselineGaps` in the
  report records the (expected) single-pass failures as the ablation finding itself;
  `unexpectedFailureLedger` is the real regression gate and must be empty.
  Report: `runtime-ablation-report.json`.
- **`TrackAMemoryAuthorityAblationEvaluationTest`** — with vs without retrieval authority. Seeds a
  SUPERSEDED (stale) and a FORGOTTEN memory alongside their current/authorized replacements, then
  compares the real `MemoryRetrievalService` (which unconditionally excludes
  FORGOTTEN/SUPERSEDED/ARCHIVED) against a naive keyword-overlap baseline defined only in the test
  (not production code) that ignores status entirely. Report: `memory-authority-ablation-report.json`.
- **`TrackACapsuleGenomeAblationEvaluationTest`** — static-card vs dynamic-Genome. Uses the real
  `CapsuleService.createFromMemory` → `CapsuleGenomeService.compile` → `CapsuleSandboxService.respond`
  pipeline for the "dynamic_genome" variant, and a naive unfiltered string concatenation (built only
  in the test) for the "static_card" baseline. Confirms the dynamic path structurally excludes a
  foreign user's memory from `authorizedMemoryIds`, rejects direct compilation of a non-owner-bound
  memory (`CapsuleGenomeService.compile` throws), and declares an `identityDisclosureAllowed`
  invariant in its evaluation metadata — none of which a raw concatenated string has. Report:
  `capsule-genome-ablation-report.json`.
- **`TrackAProactiveDecisionEvaluationTest`** — quiet_hours / long_gap_return / changing_preference.
  These three types are gated by `AliveDecisionEngine` + `QuietWindowResolver`, not by
  `AuroraDualKernelRuntime`, so their ablation axis is "gate enforced" (quiet hours checked before
  the model is ever consulted; a long silence yields exactly one gentle check-in, not a barrage; a
  "more companionship" request results in a scheduled return). Reports:
  `proactive-decision-quiet-hours-report.json`, `proactive-decision-long-gap-return-report.json`,
  `proactive-decision-changing-preference-report.json`.

## 3. Failure ledger

Every suite above writes a structured `unexpectedFailureLedger` (or `failureLedger` field) into its
own report — scenario id, scenario type, variant, pass/fail, and a human-readable reason — and each
suite's own JUnit assertion fails the build if that ledger is non-empty. There is currently no
ledger content in the checked-in reports because the suites are green (see `verification` in
`docs/goal/tracks/track-a-status.yml`); the ledger mechanism itself is exercised by
`TrackARuntimeAblationEvaluationTest`'s `expectedSinglePassBaselineGaps`, which shows the same
structure being populated for the (expected, not regressive) single-pass baseline gaps.

## 4. Real-provider evidence (not Mock)

`src/test/java/com/innercosmos/evaluation/TrackARealProviderSmokeEvaluationTest.java` is tagged
`real-provider` and **excluded from the default `./mvnw test` gate** via the `excludedGroups`
property in `pom.xml` (default value `real-provider`), per TRACK-A-LIVING-INTELLIGENCE.md §4:
"Real-provider runs are explicit, separately reported and never silently replaced by Mock." Run it
explicitly with:

```bash
export $(grep -v '^#' .env.track-a.local | xargs)   # never echoed, never committed
./mvnw test -Dtest=TrackARealProviderSmokeEvaluationTest -DexcludedGroups=
```

Captured real runs (this session, redacted — see `real-provider-smoke/*.json`): GLM, DeepSeek and
MiniMax were each called for real over HTTPS (`GlmLlmClient`/`DeepSeekLlmClient`/`MiniMaxLlmClient`
constructed directly with `allowFallback=false`, so a provider failure would surface as a thrown
`AiProviderException` rather than a silent Mock substitution) for the `short_emotional_support` and
`crisis_safe_degradation` dev scenarios, in both single-pass and dual-kernel variants (12 real API
calls total this session; no key VALUE appears anywhere in the reports, only the env VAR NAMES and
structural outcomes).

**Honest finding, reproduced identically across all three real providers:** the single-pass
instruction used in this smoke test embeds an explicit JSON schema example and parses cleanly on
the first attempt every time (`badOutputEventsInThisCall: 0`). The dual-kernel path's plan/speaker
instructions are the actual production `AuroraDualKernelRuntime.planInstruction()` /
`speakerInstruction()` text, which does **not** embed a literal JSON schema example — and all three
real providers deviated from the expected `AuroraPlanResult`/`AuroraResult` field names on the first
attempt (`badOutputEventsInThisCall: 2` on every dual-kernel run, all three providers). In every
case the eventual visible result was still NOT the deterministic fallback sentinel
(`visibleResultIsDeterministicFallback: false`) — i.e. the repair-retry and/or critic path recovered
a valid answer despite the schema drift — but this is a genuine, repeatable, actionable finding for
A1: **production's dual-kernel plan/speaker instructions should carry an inline JSON schema example
the way the single-pass instruction in this test does**, to reduce reliance on the repair-retry path
across real providers. This is exactly the kind of concrete, non-lexical, real-provider quality
signal `对齐文档/20` said the harness could not yet produce.

## 5. What is still missing before A0 can be called COMPLETE

- **MiMo/ASR** was not exercised this session (text-chat smoke only); `MIMO_API_KEY` is present in
  `.env.track-a.local` and the test class has room for a fourth `@Test` method following the same
  pattern, but ASR requires an audio fixture this session did not build.
- **Held-out set is currently small** (1 instance per type) and authored by the same agent that
  wrote the ablation logic — it is "frozen" in the sense that expectations were fixed before any
  run, but it has not yet been reviewed by a non-author, which §4's "non-author can run" deliverable
  ultimately implies for full sign-off.
- **Human/blind-review pairwise packaging** (`对齐文档/08`/A1 §5's blind-review package) is not
  built here; this session's real-provider evidence is structural (parse success, latency,
  fallback-sentinel detection), not a human quality judgment.
- **The `ai-lab` Python harness and this Java harness are not yet cross-referenced** beyond this
  README — a future pass should either fold `track-a-scenario-catalog-v1.json`'s 13 types into
  `ai-lab/evals/datasets/manifest.json`'s splits or explicitly document why they remain two
  independent catalogs.
- **P0 gates are enforced per-suite, not yet aggregated** into one combined ledger/report across
  all five suites; today an engineer must open five JSON files, not one.

## 6. Reproducing this evidence

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw test -Dtest=TrackARuntimeAblationEvaluationTest,TrackAMemoryAuthorityAblationEvaluationTest,TrackACapsuleGenomeAblationEvaluationTest,TrackAProactiveDecisionEvaluationTest
# reports land in target/track-a-eval/*.json (gitignored `target/`; copies checked in here)
```
