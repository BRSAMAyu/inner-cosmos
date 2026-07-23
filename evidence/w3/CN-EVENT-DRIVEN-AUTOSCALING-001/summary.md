# CN-EVENT-DRIVEN-AUTOSCALING-001

Status: **PASS on local kind; Academy portability remains a separately declared environment boundary.**

Date: 2026-07-23  
Cluster: `kind-kubedeploy`, Kubernetes `v1.36.1`, one control-plane node  
Application source checkpoint: `ec948b7`  
KEDA chart: `kedacore/keda` `2.20.1`

## Product hook and mechanism

The worker does not scale from generic CPU alone. `OutboxQueueMetrics` exports only:

- `inner_cosmos_outbox_ready`
- `inner_cosmos_outbox_oldest_ready_age_seconds`
- `inner_cosmos_outbox_dead`

`deploy/k8s/extensions/keda/worker-scaled-object.yaml` consumes backlog count and oldest-ready age
through Prometheus. The worker stays at one replica when idle, may scale to six, and has an explicit
one-replica fallback. No payload, content, user ID, event ID or aggregate ID is a metric label.

## Live scale and recovery drill

The live drill created a valid synthetic outbox backlog while the ScaledObject was paused, then
removed the pause:

1. baseline backlog: 135 ready rows; duplicate inbox receipts: 0;
2. worker Deployment scaled `1 -> 6` in approximately 15 seconds;
3. the backlog drained to `PUBLISHED`;
4. the worker scaled down `6 -> 3 -> 1` under the declared stabilization policy;
5. during a second lease drill, one valid `dialog.finished.v1` event was placed in `PROCESSING`
   with a 20-second lease and a worker Pod was deleted;
6. after lease expiry another worker completed it: final status `PUBLISHED`, one inbox receipt,
   zero duplicate receipts.

Final recheck after the trace drill:

```text
ScaledObject Ready=True Active=False Fallback=False Paused=False
HPA targets: 0/10 ready, 0/30 oldest age
worker replicas: 1/1
outbox: PUBLISHED|1847
duplicate (consumer_name,event_id) inbox groups: 0
```

## Verification layers

- `OutboxQueueMetricsTest`: privacy-safe names and values.
- `JdbcOutboxRepositoryIntegrationTest`: PostgreSQL 16 + Flyway V1-V22 queue counts, age and dead
  transitions.
- live Prometheus/KEDA/HPA scale-out and scale-in.
- live worker deletion with lease recovery and inbox exactly-once check.

## Honest boundaries

- This is a single-node local kind result; it proves application/KEDA semantics, not Academy EKS
  add-on permissions or multi-AZ scheduling.
- The workload was synthetic but used the real schema, repository, worker, lease and inbox paths.
- KEDA is optional: the worker has a bounded one-replica fallback and can be operated with ordinary
  HPA/manual replicas in restricted Academy environments.

