# INNO-TEMP-001 independent review — 2026-07-15

Reviewed commit: `73bae88cbdcde82ba6453d513dbedc6174da69c4`

Review result: **CHANGES_REQUIRED for production-ready temporal scheduling; accepted as the first durable WakeIntent foundation.** `AURORA-TEMPORAL` correctly remains `IN_PROGRESS`.

The review used a detached worktree so the shared tree's later Self/Emergence work did not affect the result.

## Verified

- `mvnw.cmd clean verify` on Oracle JDK 21.0.10: 106 suites, 721 tests, 0 failures, 0 errors, 0 skipped; SpotBugs 0; build success.
- PostgreSQL/Testcontainers and Flyway V1–V4 ran inside that suite without skipped tests.
- `pnpm test`: 1 file, 3 tests passed.
- `pnpm run build`: TypeScript/Vite production build passed.
- Playwright against the detached JAR started with explicit demo seed: 3/3 passed.
- `scripts/scan-secrets.ps1`: 0 findings.
- Owner-scoped create/list/reschedule/cancel, cancellation race, lease competition/recovery, durable notification-before-live-fanout and honest ledger status are present.

The Playwright command is not self-contained with the repository defaults: the tests default to `demo/demo123`, while demo data is opt-in. Reproduction required `--inner-cosmos.demo.seed-enabled=true` (or externally provisioned `E2E_USERNAME`/`E2E_PASSWORD`). This prerequisite should be encoded in an E2E launcher or evidence command instead of remaining implicit.

## Required corrections

### P1 — Autonomous schedules use the wrong instant in UTC/Kubernetes runtimes

`AliveDecisionEngine` builds `preferred` from `LocalDateTime.now()` in the server default zone and then declares that wall-clock value to be `Asia/Shanghai` when calling `WakeIntentService.schedule`. A normal UTC container therefore turns, for example, server `12:30` into `12:30+08:00` and persists `04:30Z`, roughly eight hours earlier than intended. Singapore and China both being UTC+8 does not help because the source wall clock is the UTC container.

Required direction: obtain the user's persisted IANA timezone and construct the target from an `Instant`/`Clock` or `ZonedDateTime` in that zone. Do not combine an unzoned server `LocalDateTime` with a hard-coded user zone. Add a test with JVM/server UTC and a non-UTC user zone, plus DST gap/overlap cases.

### P1 — V4's global notification uniqueness breaks existing Capsule retry semantics and may block migration

V4 creates a unique index across every notification `(user_id,type,ref_type,ref_id)`. Existing `CapsuleSyncService` intentionally calls ordinary `notify` for every retry; repeated `SYNC_FAILED` events for the same queue therefore produce duplicate keys. An existing database containing those legitimate duplicates can fail while applying V4, and after migration a second failure notification can throw from the retry path.

Required direction: scope idempotency to WakeIntent notifications (for example a partial PostgreSQL unique index for `type='AURORA_RETURN' AND ref_type='WAKE_INTENT'`), or introduce a dedicated idempotency key with explicit semantics. Add a V3→V4 migration test seeded with duplicate legacy Capsule notifications and a post-migration Capsule retry regression test.

### P2 — Local-time API accepts ambiguous/nonexistent DST wall times without an explicit contract

`LocalDateTime.atZone` silently resolves a DST gap or overlap. The persisted instant can therefore differ from the time a user selected, while the API reports success. The New York round-trip test covers a normal date only.

Required direction: either make the API instant/offset based, or validate zone rules and require an explicit offset/choice for overlaps while rejecting or clearly normalizing gaps. Cover both transitions with deterministic clock-based tests.

### P2 — The browser test proves CRUD, not an actual due-time delivery and conversation continuation

The WakeIntent Playwright case creates, postpones and cancels a fixed one-hour card. It does not observe a due intent become a durable notification, deep-link into the related conversation, restore context, or collect timing feedback. This is already broadly admitted by `known-limitations.md`; the test name/evidence should keep calling it management CRUD rather than full temporal experience.

## Acceptance boundary

The commit should remain in history: its owner checks, claim-token lease, cancellation race handling and atomic durable-notification path are useful foundations. It must not be promoted to a production-safe “Aurora returns at the correct time” capability until the two P1 corrections land and pass PostgreSQL, UTC-container and Capsule-retry regression tests. Product completion additionally requires relevance/supersession, natural time negotiation, push/deep-link continuation and user feedback as defined by `对齐文档/16-体验优先的完全体重构策略与产品战役.md`.
