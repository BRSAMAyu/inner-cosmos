# Capsule Genome v3 Experience Contract

Status: builder-verified structural contract; real-person fidelity remains unproven.

## Promise to the owner

- A compiled feature is visible to runtime only when it comes from a current, explicitly authorized
  memory in the active immutable Genome version.
- Claims remain episode-scoped. Values, habits, and temporal state require an explicit cue in the
  same cited memory; one event is never silently promoted into a universal personality trait.
- Every feature carries `memoryId`, source version, and confidence. Missing categories are recorded
  as unknowns instead of being filled with flattering defaults.
- A pre-v3 user Genome cannot continue speaking silently after this contract changes. It enters the
  existing owner-review path and must be recompiled.

## Promise to the visitor

- Each question produces a `ContextBuildManifest` naming the active Genome version, detected intent,
  selected category, and exact memory IDs used for that turn.
- PersonaChat receives only matching features from the immutable Genome snapshot. Mutable capsule
  draft JSON, unrelated claims, and unselected style provenance do not enter provider context.
- When no grounded feature matches an unfamiliar question, the runtime selects no evidence and uses
  `ACKNOWLEDGE_UNKNOWN`; it must not improvise an answer from unrelated memories.
- Existing identity, contact, original-dialogue, authorization, withdrawal, and output-redaction
  boundaries remain in force.

## Observable acceptance

1. Compiler integration proves claims, values, habits, and temporal state have category-local
   provenance and exposes per-category counts in the v3 evaluation artifact.
2. Runtime unit/integration tests prove feature-level selection, manifest propagation, immutable
   snapshot use, legacy-version fail-closed behavior, and absence of mutable/unselected context.
3. The annotated runtime dataset must keep intent and exact-selection accuracy at `1.0`, evidence
   leaks at `0`, and unfamiliar fallback accuracy at `1.0`.
4. Groundedness evaluation must include every IR category, not only legacy scene/voice citations.

## Explicit non-claim

This contract establishes traceability, minimization, and graceful degradation. It does not prove
that deterministic cue extraction sounds like the real owner, nor that it beats a long-prompt
baseline. Real Provider pairwise runs and independent blind reviewers are still required.
