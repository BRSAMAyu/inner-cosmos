# INNO-CAP-002 — Versioned Capsule Genome review and runtime contract

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Product and data contract

- Every newly created user capsule now compiles an immutable `capsule-genome.v1` version. The
  capsule stores an explicit active-version pointer; subsequent recompilation creates a parented
  version instead of mutating the prior artifact.
- The authorization snapshot contains memory IDs, source versions and consent scopes, but never
  copies private memory text. Compilation rejects non-owner or non-current cards, and recompile
  rejects foreign, withdrawn, `LOCAL_ONLY` and `NO_EXTERNAL_PROCESSING` selections atomically.
- The compiled persona, style, context preview, compiler version and deterministic eligibility
  evaluation travel together. Runtime consumes the selected compiled prompt rather than silently
  rebuilding personality from whatever data happens to be current.
- Corrections and authorization changes move the selected version to `NEEDS_REVIEW`, unpublish the
  capsule and stop both new and existing conversations before Safety, Provider, persistence or
  quota side effects. Owner recompile creates the next version and is required before republish.
- Archive advances the selected version to `WITHDRAWN` even when it was already awaiting review.
  PostgreSQL keeps the active pointer referentially valid and uses `ON DELETE SET NULL` so account
  deletion can still cascade through the version chain.
- Owner-only API endpoints expose version history and explicit recompile. No legacy seed capsule is
  forced through user-consent semantics, preserving the existing plaza experience.

## Reproducible verification

- `CapsuleGenomeServiceIntegrationTest`: 2/2 PASS, covering private-text exclusion, immutable
  parent chain, owner isolation, review-before-publish, consent rejection with rollback and archive
  after review.
- Capsule runtime focused gate: 30/30 PASS across Genome integration, matching, phase/safety and
  quota tests.
- Journey/migration focused gate: 22/22 PASS across `ApplicationFlowTest`, correction propagation,
  H2 Genome integration, PostgreSQL application smoke and Flyway baseline.
- PostgreSQL 16 + pgvector 0.8.1: 11 migrations, 74 application tables and 72 identity columns;
  exact table/index/foreign-key reconciliation PASS.
- Final full Java 21 / Spring Boot 3.5 regression: 761/761 PASS across 116 suites.

## Honest remaining work

This closes the durable Genome/version/review/withdrawal foundation, not all of G6. The current
compiler evaluation proves structural eligibility; it does not yet provide independently scored
fact/style/habit coverage, conflict resolution, owner-facing sandbox comparison or a materially
better multi-stage Capsule runtime. Real Provider pairwise, adversarial and longitudinal evaluation,
the React review/regenerate/republish experience and independent review remain required.
`CAPSULE-COMPILER`, `CAPSULE-SAFETY` and G6 therefore stay `IN_PROGRESS`.
