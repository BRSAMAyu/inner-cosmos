# INNO-INNER-001 — Campaign B correction authority checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience slice

- React now offers a plain-language “if this is not quite you” flow with old understanding,
  corrected understanding, a read-only impact preview and an explicit confirmation step.
- Preview names the affected Aurora retrieval, portrait, memory, starfield, weekly insight and
  capsule-context surfaces. It does not write state.
- Confirmation creates a highest-authority user claim version, supersedes rather than deletes
  the previous claim, and stores one propagation record per affected downstream surface.
- Explicitly targeted or old-value-matching memory cards are superseded, not erased. The current
  starfield and all existing production retrieval paths already select `ACTIVE` memories.
- Affected capsule memory authorization becomes `NEEDS_REVIEW`; the existing user-consented
  capsule sync proposal path is triggered rather than silently rewriting the public capsule.
- Correction retirement now preserves the audit row, retires the linked claim, restores the
  superseded predecessor when available and emits withdrawal/re-evaluation propagation records.

## Data and failure semantics

- `tb_understanding_claim` is the relational claim/version authority. It stores source,
  authority, confidence, status, evidence, version and supersession relation.
- `tb_claim_propagation` records applied or review-required downstream effects.
- Correction, claim version, portrait update, memory state and propagation rows share one
  Spring transaction. Portrait propagation is fail-closed; the prior swallowed exception path
  was removed.
- PostgreSQL Flyway V7 and the H2/MySQL-compatible baseline schema are both supplied.

## Verification

- Java focused gate: `UserCorrectionControllerTest`, `UserPortraitServiceApplyDeltasTest`,
  `ApplicationFlowTest` — PASS.
- New contracts prove preview is read-only, confirm creates a claim and propagation evidence,
  and repeated confirmation preserves a `SUPERSEDED` old version.
- React Vitest: 3/3 — PASS.
- TypeScript + Vite production build: PASS; bundled SPA assets regenerated.
- Full Java 21 / Spring Boot 3.5 gate: 739/739, including PostgreSQL/pgvector and Redis
  Testcontainers contracts — PASS.
- Packaged-JAR Playwright: 5/5 — existing interrupt/replan, durable recovery, WakeIntent and
  Self journeys plus the new correction preview/confirm journey all pass.
- `git diff --check`: PASS (line-ending notices only).

## Honest remaining work

This is the first Campaign B vertical checkpoint, not G5 completion. Remaining work includes
the full ADD/MERGE/SPLIT/CONTRADICT/FORGET operation ledger and rollback semantics, calibrated
hybrid retrieval and contradiction evaluation, derived-index invalidation receipts, time/topic/
person starfield exploration with accessible list equivalence, capsule withdrawal completion,
packaged-browser blind experience evidence and independent review.
