# INNO-INNER-003 — Executable memory rollback and projection receipts

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## User-visible contract

- Living Aurora shows recent memory changes next to the Inner Cosmos starfield.
- A reversible change can be withdrawn in one action. Withdrawal creates a new monotonic memory
  version and a linked `ROLLBACK` operation; it never edits away the original audit record.
- Update/archive/reinforcement/decay/merge/split/contradiction/supersession and add outcomes have
  defined rollback behavior. Generated merge/split/replacement cards retire from current views and
  their typed links become `ROLLED_BACK` when the source version is restored.
- `FORGET` remains deliberately irreversible because its audit snapshot never contains the raw
  content. Link-only operations require a separate relationship confirmation rather than a broad
  automatic graph rewrite.

## Projection evidence

- Every effective operation and rollback writes one receipt for `AURORA_RETRIEVAL`, `STARFIELD`
  and `CAPSULE_CONTEXT` in the same transaction.
- Retrieval and starfield are marked `REBUILT` because they are query-time projections over the
  relational authority. Capsule context is marked `REVIEW_REQUIRED` and remains fail-closed until
  the user re-authorizes public context.
- PostgreSQL Flyway V9 and the baseline schema add `tb_memory_projection_receipt` with a unique
  operation/projection identity and foreign-keyed audit ownership.

## Verification

- Memory lifecycle focused gate: 10/10 PASS, covering update rollback, monotonic versions,
  duplicate rollback rejection, irreversible forget, merge-source restoration, generated-card
  retirement, link invalidation, receipt creation, owner isolation, retrieval and Starfield V2.
- Full Java 21 / Spring Boot 3.5 regression: 748/748 PASS across 111 suites.
- PostgreSQL 16 + pgvector Testcontainers: 9 migrations, 72 application tables, 70 identities,
  source-schema table/index/foreign-key equality and application smoke PASS.
- React Vitest 3/3 and production build PASS; bundled SPA regenerated.
- Packaged-JAR Playwright 7/7 PASS, including real API mutation, page reload, UI rollback,
  starfield restoration and visible operation history.

## Honest remaining work

This closes executable lifecycle rollback and durable projection receipts, not G5. Link changes
still require a dedicated preview/confirm withdrawal flow. Provider embedding quality, calibrated
retrieval thresholds, provenance drill-down, mobile performance measurement, capsule withdrawal
completion and independent blind review remain open.
