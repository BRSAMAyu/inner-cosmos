# INNO-CAP-008 — Provenance Genome IR and bounded runtime retrieval

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16.

## Product slice

- `capsule-genome.v3` adds episode-scoped claims plus explicitly-cued values, habits, and temporal
  state. Every feature carries a stable id, scope, confidence, extraction method, and
  `{memoryId, sourceVersion, confidence}` evidence.
- `capsule-context-preview.v3` carries the IR and a versioned retrieval policy. Unsupported
  categories are explicit unknowns with `ACKNOWLEDGE_UNKNOWN`, not inferred traits.
- PersonaChat now composes each turn from the immutable active Genome, at feature granularity. Its
  `ContextBuildManifest` records the Genome/compiler version, query intent, selected categories,
  selected memory IDs, reason, and fallback policy.
- Mutable capsule style/context drafts, unselected claims, and style provenance outside the manifest
  no longer enter provider context. A persisted v2 user Genome fails closed into owner review rather
  than running under the new contract without an IR.
- Contact-like data is masked when IR statements and scene excerpts are compiled.

The living experience contract is
[`docs/campaigns/capsule-genome/experience-contract.md`](../../../docs/campaigns/capsule-genome/experience-contract.md).

## Evidence and gates

- Red gate: the new compiler/runtime contract initially failed compilation because the runtime
  composer did not exist. The first green candidate then exposed a real extraction false positive:
  `重要展示` was classified as a value. The cue list was narrowed before acceptance.
- `CapsuleGenomeServiceIntegrationTest`: proves v3 category counts, exact category-local provenance,
  extraction semantics, retrieval policy, legacy scene/tension behavior, consent, review, withdrawal,
  sandbox and simulator isolation.
- `CapsuleRuntimeContextComposerTest` and `PersonaChatServiceImplPhaseBTest`: prove feature-level
  routing, unfamiliar fallback, manifest propagation, immutable snapshot use, removal of unselected
  style evidence, and v2 fail-closed review.
- [`runtime-retrieval-report.json`](runtime-retrieval-report.json): 6 annotated scenarios; intent
  accuracy `1.0`, exact selection accuracy `1.0`, evidence leak count `0`, unfamiliar fallback
  accuracy `1.0`.
- [`compiler-groundedness-report.json`](compiler-groundedness-report.json): 4 consent/ownership
  scenarios; all legacy and v3 IR citations remain within the legitimate authorization set;
  ungrounded citations `0`.
- Focused compiler/runtime/groundedness/forgetting/privacy gates passed. Campaign checkpoint full
  Java 21 regression: 129 suites, 805 tests, 0 failures, 0 errors, 0 skipped (`183.1s`).

## Honest remaining work

This closes the machine-verifiable v3 structural IR and retrieval-minimization slice. It does not
close `CAPSULE-RUNTIME` or G6: there is no real-provider candidate generator, planner/critic/reranker,
long-prompt baseline comparison, independent blind fidelity review, or longitudinal adversarial
evaluation. The current environment has no real LLM credential configured, so those truth claims
remain provider/human gated rather than being replaced with Mock scores.
