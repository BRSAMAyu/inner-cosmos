# A3 capsule matching handoff checkpoint

Status: WIP_REVIEW_REQUIRED / IN_PROGRESS. This slice is preserved on the Track A branch and is
not accepted into main at this checkpoint.

## Implemented foundation

- Adds a capsule-embedding entity, mapper, index service and PostgreSQL V18 migration.
- Builds embedding text only from capsule public-safe pseudonym, introduction and public tags.
- Builds the viewer query from eligible memories while excluding LOCAL_ONLY,
  NO_EXTERNAL_PROCESSING and SIMULATOR scopes.
- Adds the semantic score to existing matching strategies and fails soft to the previous matching
  behavior if the embedding provider or database path is unavailable.

## Verification performed

- CapsuleMatchingTest: 18 tests, 0 failures/errors/skips.
- git diff --check: PASS.
- No full Maven regression was run for this WIP slice. The previous committed A0-A2 checkpoint
  reported 874/874 only after rerunning one unrelated concurrency flake.

## Review findings that block main integration

1. PostgreSQL scoring takes MAX over all ACTIVE embeddings for capsule/model/version but does not
   require the current capsule content hash. Editing a capsule can leave an older public-text vector
   active and able to influence ranking.
2. There is no dedicated service/integration test for index creation, content change invalidation,
   consent withdrawal, provider failure, rebuild behavior or real PostgreSQL cosine ranking.
3. First-use indexing is synchronous and may issue an embedding-provider call per candidate. There
   is no asynchronous rebuild, batch control, latency budget, backpressure or cache-warm evidence.
4. This is only the vector-matching foundation. Dynamic Genome fidelity, strategy calibration,
   complementarity/diversity behavior, privacy adversarial evaluation and blind human quality
   evidence are still open.

## Required continuation

- Bind active scoring to current content hash or atomically retire superseded embeddings.
- Add Testcontainers coverage for V18, vector scoring, idempotent rebuild and invalidation.
- Prove consent/correction/withdrawal propagation through embeddings and cached match results.
- Move indexing out of the synchronous candidate loop or enforce a measured bounded warm-up path.
- Evaluate semantic similarity, complementarity and controlled diversity against explicit
  baselines with held-out cases; keep safety/privacy hard filters independent of rank score.
- Re-run the full Maven suite, AI evaluation gate, secret scan and migration checks before proposing
  A3 for main.

No user-visible API or OpenAPI change is introduced by this WIP slice, so the contract-delta ledger
remains EMPTY.
