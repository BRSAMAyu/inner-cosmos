# A3 capsule matching foundation checkpoint

Status: BUILDER_VERIFIED_IN_PROGRESS. The bounded engineering foundation is accepted for integration;
the broader A3 product-quality and research work remains IN_PROGRESS.

## Accepted foundation

- Persists versioned capsule embeddings through PostgreSQL V18 and pgvector.
- Embeds only public-safe pseudonym, introduction and public tags.
- Builds viewer queries only from eligible memory sources, excluding LOCAL_ONLY,
  NO_EXTERNAL_PROCESSING and SIMULATOR.
- Keeps safety, privacy, visibility and block rules as hard filters independent of ranking.
- Interactive matching performs one query embedding and scores only warmed current vectors; it does
  not synchronously embed every candidate.
- Both PostgreSQL and local scoring require the capsule current public-content hash. Successful
  rebuild atomically marks older active rows for the same model/version as SUPERSEDED.
- A ShedLock-protected background job performs bounded, configurable missing-index rebuilds.
- Embedding/provider/database failures degrade to existing deterministic strategies without
  widening access.

## Verification

- ./mvnw clean test: 873 tests, 0 failures, 0 errors, 0 skipped.
- CapsuleMatchingTest: 18/18.
- CapsuleEmbeddingIndexServiceIntegrationTest: 2/2. Proves no interactive candidate-provider
  N+1, bounded rebuild, content-change invalidation, supersession, and private/simulator exclusion.
- CapsuleEmbeddingPostgresIntegrationTest: 1/1 against PostgreSQL 16 + pgvector. Proves Flyway
  V1-V18, vector persistence, current-hash scoring and old-vector exclusion.
- PostgreSQL baseline/smoke and scheduler lease contract tests were updated and pass.
- git diff --check: PASS before checkpoint commit.

## Honest remaining A3 work

This checkpoint establishes correctness and operability; it does not prove that the matching
experience is already best-in-class. The next owner must still:

1. Measure real-provider latency, cost, failure/backpressure behavior, and warm-index operations.
2. Evaluate similarity, complementarity and controlled diversity on held-out bilingual cases
   against lexical/theme baselines.
3. Validate Dynamic Genome fidelity and simulator realism with blind human pairwise review.
4. Close end-to-end correction/withdrawal/deletion receipts for all derived data and cached match
   results, not only current capsule-vector scoring.
5. Tune user-facing explanations and controls without weakening hard privacy/safety filters.

No user-visible API or OpenAPI change is introduced by this checkpoint, so the contract-delta
ledger remains EMPTY.
