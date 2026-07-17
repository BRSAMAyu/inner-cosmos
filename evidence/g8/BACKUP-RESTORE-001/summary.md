# BACKUP-RESTORE-001 — PostgreSQL backup + restore drill

> Date: 2026-07-17 · Cluster: kind `inner-cosmos-ops` · Ledger: G8 `OPS-RESILIENCE` (backup/restore).
> Closes the durability gap disclosed in ACADEMY-LIVE-001 (static hostPath PV, "not durable across node
> replacement", backup/restore "not exercised") and wires the previously-orphan `scripts/backup/backup.sh`
> logic into an actual in-cluster resource.

## What was added (as-code)
`deploy/k8s/backup/pg-backup.yaml`:
- **CronJob `inner-cosmos-pg-backup`** (daily 03:00, `concurrencyPolicy: Forbid`, history limits, non-root
  securityContext) running `pg_dump -Fc` into a dedicated **PVC `inner-cosmos-backups`**, verifying the dump
  is >1KiB, then pruning dumps older than 30 days. Connection params from a ConfigMap-style env + the
  existing `inner-cosmos-runtime` Secret (same the app uses) — drops onto academy-eks by pointing at
  `inner-cosmos-postgres`.

## Drill (observed live)
1. Seeded a test table: `tb_demo` with **42 rows**.
2. Ran the backup by materializing the CronJob: `kubectl create job --from=cronjob/inner-cosmos-pg-backup`.
   Job completed; log: `pg_dump -> /backups/innercosmos_20260717-090906.dump` … `backup ok` (2878 bytes).
3. Simulated data loss: `DROP TABLE tb_demo` → `to_regclass('tb_demo')` returned empty (gone).
4. Restore Job (mounts the backups PVC): `pg_restore --clean --if-exists` of the latest dump → "restore done".
5. Verified: `SELECT count(*) FROM tb_demo` → **42** (fully recovered).

## Result
Backups now actually run in-cluster (CronJob, not an un-wired shell script), and restore is proven
end-to-end. Combined with OPS-RESILIENCE-001, the stateful tier has a real RPO/RTO story.

## Remaining
- Off-cluster/object-store copy of dumps (PVC alone is single-cluster durability); PITR/WAL archiving.
- Running this CronJob on real academy-eks against the static-PV Postgres is an AWS-account activity; the
  manifest is ready to layer onto that overlay.
