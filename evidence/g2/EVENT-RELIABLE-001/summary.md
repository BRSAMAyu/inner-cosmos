# EVENT-RELIABLE-001 — transactional outbox → idempotent consume → retry → DLQ replay

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Ledger: G2 `EVENT-RELIABLE` (was UNASSESSED).
> Verified against real PostgreSQL (pgvector/pgvector:0.8.1-pg16) via Testcontainers — Docker required.

## Chain verified (JdbcOutboxRepositoryIntegrationTest, 3/3 green, 32s)
1. **Transactional outbox + source dedup** — `append(...)` uses `INSERT ... ON CONFLICT (dedup_key) DO
   NOTHING`; a duplicate dedup_key is rejected (returns false).
2. **Single-consumer claim** — `claim(...)` uses `FOR UPDATE SKIP LOCKED`; a second worker gets nothing
   while the first holds the lease.
3. **Idempotent consumption** — `complete(...)` writes `tb_inbox_receipt ON CONFLICT DO NOTHING` before
   invoking the handler; re-completing the same event does not re-run the side effect (receipt count stays 1).
4. **Lease recovery** — an expired `locked_until` lets a healthy worker re-claim a crashed worker's event;
   the stale owner's `complete` fails with an ownership error.
5. **Failure rolls back the receipt before retry** — a throwing handler rolls back the whole transaction
   (no receipt persisted), then `retry(...)` increments attempts and reschedules.
6. **NEW — dead-letter on retry exhaustion + replay** (`exhaustedRetriesLandInDeadLetterThenReplayReprocessesExactlyOnce`):
   - 5 failed attempts drive the event to `status=DEAD`; it is then **not** returned by normal `claim`.
   - No side effect leaked across the 5 failures (inbox receipt count = 0).
   - `replayDead(limit)` (new repository method) requeues DEAD events to `PENDING` with a clean attempt
     count; a healthy handler then processes it **exactly once** → `status=PUBLISHED`, receipt count = 1.

## Code added
`JdbcOutboxRepository.replayDead(int limit)` — dead-letter replay operation (resets DEAD→PENDING,
attempts=0, clears lease/error). Idempotent consumption still guards against duplicate effects on replay.

## Remaining (not blocking; documented)
- **SQS**: production uses a swappable queue abstraction; the JDBC outbox is the Academy/local substitute
  (proven cross-process in ACADEMY-LIVE-001). Real SQS wiring + delivery is an AWS-account activity
  (`HG-PRODUCTION-ACCOUNTS`).
- Non-author independent review of this chain.
