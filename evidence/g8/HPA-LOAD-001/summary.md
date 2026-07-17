# HPA-LOAD-001 — HPA scale-out/in exercised under real CPU load

> Date: 2026-07-17 · Cluster: kind `inner-cosmos-ops` · Ledger: G8 `ACADEMY-RELIABILITY` (HPA behavior).
> Closes the long-standing gap that ACADEMY-LIVE-001 disclosed: "Actual HPA scale-out/load behavior was
> not exercised." Manifest contract is 2..4 @ CPU 70% (deploy/k8s/base/app-hpa.yml; kind-dev mirrors it).

## Method
- metrics-server installed on kind (patched `--kubelet-insecure-tls`).
- App running via `deploy/k8s/overlays/kind-dev` (2 replicas, cpu request 250m).
- Load: `fortio load -qps 0 -t 240s -c 100 http://inner-cosmos-api...:8080/actuator/prometheus`.

## Observed (kubectl get hpa / describe)
- Under load: `TARGETS cpu: 149%/70%` → HPA scaled **2 → 4** (maxReplicas).
  Event: `SuccessfulRescale  New size: 4; reason: cpu resource utilization (percentage of request) above target`.
- All 4 replicas reached Running (verified via `get pods -o wide`).
- After load stopped: `SuccessfulRescale New size: 3` then `New size: 2` ("All metrics below target");
  settled at `cpu: 8%/70%`, 2 replicas.
- Grafana confirmed the same visually (see OPS-OBSERVABILITY-001): "API replicas up" = 4, CPU per pod ~100%.

## Result
Full autoscaling cycle (2→4→2) driven by real CPU utilization and metrics-server. HPA is not just
installed — it is now demonstrably functional.

## Remaining
- Scale-out on the real academy-eks cluster (this drill is on local kind) remains an AWS-account activity;
  the manifest and behavior are proven identical (kind-dev mirrors base HPA contract).
