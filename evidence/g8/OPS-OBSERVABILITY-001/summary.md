# OPS-OBSERVABILITY-001 — observability as-code on a live cluster

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Cluster: local kind `inner-cosmos-ops` (2 nodes)
> Ledger: G8 `OPS-OBSERVABILITY` (was UNASSESSED).

## What was added (as-code)
`deploy/k8s/observability/` (kustomize):
- **Prometheus v2.54.0** — ServiceAccount + ClusterRole/Binding (pod/service discovery), a ConfigMap with
  a `kubernetes_sd` pod-discovery scrape config (keeps pods annotated `prometheus.io/scrape=true`, uses the
  `prometheus.io/path`/`port` annotations) scraping `/actuator/prometheus` (Micrometer), plus **4 alert
  rules** (`InnerCosmosApiDown`, `InnerCosmosNoApiReplicas`, `InnerCosmosHighJvmHeap`, `InnerCosmosHigh5xxRate`).
- **Grafana 11.3.0** — provisioned Prometheus datasource + provisioned dashboard
  **"Inner Cosmos — App Health & JVM"** (API replicas up, HTTP req rate by status, HTTP 5xx ratio, JVM heap
  used/max per pod, process CPU per pod).
- The app deployment carries the `prometheus.io/scrape|path|port` annotations so discovery is automatic.

## Verification (live, observed)
- `kubectl apply -k deploy/k8s/observability` → prometheus + grafana rolled out.
- Prometheus targets (via API): **both API pods `up=1`** + prometheus self = 3 active targets, all healthy.
- Metric series present: `jvm_memory_used_bytes`=16, `http_server_requests_seconds_count`=4,
  `process_cpu_usage`=2. Alert rules loaded: 4 (all `inactive` = healthy baseline).
- Grafana dashboard rendered live during the HPA load test (screenshot captured in session): "API replicas
  up" = 4, HTTP request rate ~1000 req/s (status 200), per-pod JVM heap and CPU curves populated, 5xx ratio
  "No data" (no errors occurred).

## Reproduce
```
kubectl apply -k deploy/k8s/observability
kubectl -n observability port-forward svc/grafana 3000:3000     # http://localhost:3000 (admin/admin, local only)
kubectl -n observability port-forward svc/prometheus 9090:9090
```

## Remaining
- OpenTelemetry traces (this closes metrics + dashboards + alerts; distributed tracing not yet wired).
- Non-author runbook rehearsal is tracked under G9 FINAL-OPERATIONS.
- Grafana admin creds here are local-kind only; production must supply a real Secret.
