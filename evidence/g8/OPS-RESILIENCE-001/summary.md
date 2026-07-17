# OPS-RESILIENCE-001 — resilience drills on a live kind cluster

> Date: 2026-07-17 · Cluster: kind `inner-cosmos-ops` (control-plane + worker) · Ledger: G8 `OPS-RESILIENCE`
> (was UNASSESSED). App: `deploy/k8s/overlays/kind-dev` (mirrors base probes/PDB/rolling/security/graceful).

## Drills (all observed live)

### 1. Pod-kill recovery
Deleted one API pod → ReplicaSet immediately created a replacement; deployment returned to 4/4 ready.
No manual intervention.

### 2. Rolling update — zero downtime (maxUnavailable=0, maxSurge=1)
`kubectl rollout restart deploy/inner-cosmos-api`. Sampled `.status.availableReplicas` every 6s across the
rollout: **stayed at 4 the entire time** (surge to 5 pods, old pods only removed after new ones Ready).
`rollout status` → "successfully rolled out". Confirms no-downtime deploys.

### 3. Node drain with PodDisruptionBudget protection
`kubectl drain inner-cosmos-ops-worker` (control-plane untainted first so pods could reschedule).
- The **PDB (minAvailable=1) blocked unsafe eviction**: observed
  `error when evicting pods/"...": Cannot evict pod as it would violate the pod's disruption budget
  (will retry after 5s)` — the drain waited for a replacement to become Ready before evicting the last pod.
- Pods **rescheduled onto the control-plane node** (verified `-o wide`).
- **Continuous availability**: an in-cluster `curl` to `/actuator/health` returned **HTTP 200** during/after
  the drain.
- Worker uncordoned afterwards to restore the cluster.

## SLO / RPO / RTO statement (kind-observed, for the local-complete profile)
- **Availability SLO**: single-node loss and rolling deploys cause **zero** request-path unavailability
  (PDB minAvailable=1 + maxUnavailable=0 + surge), given ≥2 replicas and spare scheduling capacity.
- **RTO (pod loss)**: replacement Ready in ~15–25s (startup probe budget); no operator action.
- **RPO**: for the stateful tier, bounded by the backup cadence — see BACKUP-RESTORE-001 (daily pg_dump
  CronJob → RPO ≤ 24h with the default schedule; tune the schedule to lower it).

## Remaining
- Canary + automated rollback scripting, and provider-failure injection, are follow-ups.
- Node-loss on real academy-eks (vs kind drain) is an AWS-account activity.
