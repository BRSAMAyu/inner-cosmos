# Track A — A5 / G5 PROFILE-PROPAGATION: capsule matching-vector erasure + auditable receipts

> Status: BUILDER_VERIFIED_IN_PROGRESS (H2 executed; PostgreSQL/pgvector assertion authored, runs at a Docker-equipped node)
> Binds to: `docs/goal/complete-product-acceptance.yml` G5 `PROFILE-PROPAGATION`
> ("Correction and withdrawal re-evaluate downstream insights, vectors, caches, capsules, and matching.")
> and the A3-open item `consent_correction_deletion_receipts_across_all_derivatives_and_caches`.

## 1. The gap this closes

Audit of live `HEAD` (85b3a5b) found that the newest derivative — the capsule **matching** vector in
`tb_capsule_embedding` (Flyway V18, the A3 checkpoint) — had **no invalidation hook at all**:

- `CapsuleEmbeddingIndexService` exposed only `similarities` / `rebuildMissing` / `pendingCount`.
  The only lifecycle transition was content-hash-driven supersession inside `indexIfMissing`.
- The three owner-initiated erasure paths — memory **forget**
  (`MemoryLifecycleServiceImpl.withdrawCapsuleForForgottenMemory`), capsule **archive**
  (`CapsuleServiceImpl.archiveCapsule`) and data-use-**grant revoke**
  (`DataUseGrantServiceImpl.revoke`) — delisted the capsule, withdrew the Genome and nulled inline
  persona derivatives, but left the compiled matching vector `ACTIVE` on disk until some future
  rebuild.
- The consent/erasure path wrote **no auditable receipt** (unlike memory operations, which write
  `tb_memory_projection_receipt`). There was no honest "what got retracted, which derivative, when,
  how many rows" record for the data-rights path.

## 2. What was implemented

1. `CapsuleEmbeddingIndexService.retireForCapsule(capsuleId)` (+ impl): physically **deletes** every
   embedding row (current + superseded) for the capsule, so the derived pgvector is erased from the
   serving index the instant the owner acts — not soft-flagged. Idempotent (returns 0 on re-run) and
   it frees the `(capsule, model, version, content_hash)` uniqueness slot for a clean future
   re-consent + rebuild.
2. `tb_data_retraction_receipt` — a new append-only, **sensitive-payload-free** audit table
   (`user_id`, `subject_type` ∈ {MEMORY, CAPSULE, DATA_USE_GRANT}, `subject_id`, `derivative_type`,
   `action`, `affected_count`, `reason`). Entity `DataRetractionReceipt`, mapper, and
   `DataRetractionReceiptService` (`record` + owner-scoped `listForOwner`). It stores counts and a
   short reason only — never memory text, persona prompts or embeddings.
3. Wired `retireForCapsule` + a receipt into all three erasure paths (each writes exactly one receipt
   with the correct subject).
4. Schema across all three mechanisms: Flyway `V19__data_retraction_receipts.sql` (PostgreSQL),
   `schema.sql` (fresh H2), `SchemaM18Initializer` (long-lived non-Flyway H2).

## 3. Files

Production:
- `src/main/java/com/innercosmos/entity/DataRetractionReceipt.java`
- `src/main/java/com/innercosmos/mapper/DataRetractionReceiptMapper.java`
- `src/main/java/com/innercosmos/service/DataRetractionReceiptService.java`
- `src/main/java/com/innercosmos/service/impl/DataRetractionReceiptServiceImpl.java`
- `src/main/java/com/innercosmos/service/CapsuleEmbeddingIndexService.java` (+ `retireForCapsule`)
- `src/main/java/com/innercosmos/service/impl/CapsuleEmbeddingIndexServiceImpl.java`
- `src/main/java/com/innercosmos/service/impl/CapsuleServiceImpl.java` (archive path)
- `src/main/java/com/innercosmos/service/impl/DataUseGrantServiceImpl.java` (grant-revoke path)
- `src/main/java/com/innercosmos/service/impl/MemoryLifecycleServiceImpl.java` (forget path)
- `src/main/java/com/innercosmos/config/SchemaM18Initializer.java`
- `src/main/resources/db/migration/postgresql/V19__data_retraction_receipts.sql`
- `src/main/resources/schema.sql`

Tests:
- `src/test/java/com/innercosmos/service/impl/CapsuleEmbeddingRetirementTest.java` (new, 4 tests, H2)
- `src/test/java/com/innercosmos/service/impl/CapsuleEmbeddingPostgresIntegrationTest.java`
  (extended: asserts both derived vectors and their pgvector column are erased)
- `src/test/java/com/innercosmos/service/impl/CapsuleMatchingTest.java` (constructor mock added)

## 4. Verification (this session)

- `./mvnw test -Dtest=CapsuleEmbeddingRetirementTest,CapsuleMatchingTest,CapsuleEmbeddingIndexServiceIntegrationTest`
  → **Tests run: 24, Failures: 0, Errors: 0, Skipped: 0**.
- Broader regression over every touched service
  (`*Forgetting*,*DataUseGrant*,*MemoryLifecycle*,*Capsule*,*Correction*`) → **144/145 pass**. The one
  non-pass is `CapsuleEmbeddingPostgresIntegrationTest`, which **errored** (not failed) with
  "Could not find a valid Docker environment" — a Testcontainers infrastructure gap on this machine,
  not a code defect (per CLAUDE.md this is NOT permission to mark it passed).
- `git diff --check` → clean.

The four H2 regressions assert, per erasure path: the derived vector row count drops to 0; exactly one
receipt is written with the correct `subject_type`/`subject_id`/`derivative_type`/`action=ERASED`/
`affected_count`; erasure is idempotent; and a fresh rebuild is possible afterward.

## 5. Remaining / not yet proven

- **PostgreSQL/pgvector execution** of the erasure assertion needs a Docker daemon (unavailable this
  session); the assertion is authored in `CapsuleEmbeddingPostgresIntegrationTest` and must be run at
  the merge node / CI where Docker is present.
- This checkpoint covers the **capsule matching index** derivative. Full A5 still needs the single
  source→derivative registry and equivalent receipts/erasure for memory embeddings on the correction
  (supersession) path, prompt snapshots/summaries, analytics, notifications and backup tombstones,
  plus routing retraction through the durable outbox (`data.retracted.v1`) for cross-process,
  replayable delivery.
- No real-provider evidence is required for this slice — retirement is pure DB and provider-agnostic.
- The receipt table is not yet exposed through an owner-facing API (no contract delta yet); a
  data-rights audit-trail endpoint is a natural Track A follow-up + Track B integration request.
