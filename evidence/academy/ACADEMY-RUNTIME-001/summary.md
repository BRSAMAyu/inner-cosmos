# ACADEMY-RUNTIME-001 JDBC outbox and runtime-role foundation

Builder status: `IMPLEMENTED`; Academy live workload acceptance remains `IN_PROGRESS`.

Implemented:

- PostgreSQL Flyway V3 adds durable `tb_outbox_event` and idempotent `tb_inbox_receipt` tables.
- Source deduplication, `SKIP LOCKED` claiming, expiring leases, ownership validation, retry/dead-letter state, and consumer receipt deduplication are implemented and transaction tested.
- Dialog completion writes `dialog.finished.v1` before the source transaction commits. The current durable consumer validates the versioned payload and records an inbox receipt.
- Runtime roles are separated into `api`, `worker`, `scheduler`, and `migration`; API does not schedule jobs, worker only polls the outbox, scheduler owns the five leased business jobs, and migration is the sole Flyway owner.
- Academy manifests now render independent API, worker, scheduler, and migration workloads. All long-running roles wait for Flyway V3 before startup.
- The migration role disables unrelated Redis/session/rate/scheduler wiring and exits after schema initialization.

Validation:

- Java 21 full regression: 101 suites, 705 tests, 0 failures/errors/skips.
- SpotBugs high-confidence gate: 0 findings.
- PostgreSQL integration tests cover source/consumer deduplication, lease transfer, stale-owner rejection, handler rollback, and retry.
- Production image smoke: TLS PostgreSQL `VERIFY_FULL`, TLS Redis `VERIFIED_CA`, Flyway V3, 63 application tables, no demo users, non-root `appuser`.
- The same production image completed the migration role and a cross-process JDBC outbox probe ending in `PUBLISHED` with exactly one inbox receipt.
- Academy Kustomize/client dry-run: 20 resources, 0 forbidden findings, 0 missing controls, no SQS/EBS CSI/IRSA/committed Secret dependency.
- Offline capability preflight and secret scan: PASS.

Evidence boundary:

- The existing in-process post-dialog product listeners remain unchanged to preserve the current Aurora/memory experience. This slice does not claim that all six product side effects have moved behind the durable worker.
- The durable worker currently proves cross-process versioned delivery and inbox idempotency through the dialog-finished audit consumer. Migrating each user-visible side effect requires dedicated idempotency contracts.
- Academy resources have not yet been applied in this package; Gateway status, Pod recovery, rolling update, HPA behavior, and current-session external reachability remain open.
- Real-provider response quality and real IdP behavior were not evaluated by this infrastructure slice.
- No Aurora proactive, Self/Constitution/Emergence, portrait, relationship, capsule, matching, starfield, or slow-social semantics were removed or narrowed.

