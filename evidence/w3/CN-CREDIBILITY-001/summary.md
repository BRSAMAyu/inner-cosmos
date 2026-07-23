# CN-CREDIBILITY-001 — W3 6.1 credibility fixes, proven live on kind

> Date: 2026-07-23 - Cluster: kind `kubedeploy` (single node `kubedeploy-control-plane`,
> kindnetd `v20260528-9350166c`, Kubernetes server v1.36.1) - Branch: `codex/w3-cloud` off
> `codex/w0-integration` @ `dcfe365`. New overlay: `deploy/k8s/overlays/kind-full` (real
> Postgres + real Redis + api/worker/scheduler + migration Job, dev+Mock-AI profile - see
> that overlay's `kustomization.yaml` header for the disclosed boundary: no TLS, no Redis
> auth, no OIDC). Namespace `inner-cosmos-w3`. Image `inner-cosmos:w3-dev` built from this
> branch's own source (`docker build -t inner-cosmos:w3-dev .`, digest
> `sha256:283db46ffe3078011c048aba19b1d81b6e3dabc8a296291cd5aa6b088149ce26`) so the probe/health
> config fixes below are actually running, not just edited on disk.
>
> Cluster reuse note: `kubedeploy` pre-existed (10 days old) with only `kube-system`,
> `local-path-storage`, and an idle `nginx-gateway` namespace - no app workload, so nothing
> was clobbered. `kubectl config` on this Windows box defaults to a real
> `arn:aws:eks:us-east-1:...` context; the first action taken this session was
> `kubectl config use-context kind-kubedeploy` and every command below ran against that
> context only. No AWS credentials were used or touched.

## 6.1-1 - liveness/readiness separation (fixed + fault-injected proof)

Before: `deploy/k8s/base/app-deployment.yml`, `overlays/kind-dev/app-deployment.yaml`,
`overlays/academy-eks/{worker,scheduler}-deployment.yml` pointed startup, readiness, AND
liveness at the same aggregate `/actuator/health`, which includes the DB health indicator
(and a custom indicator that itself re-checks DB connectivity). A PostgreSQL/Redis outage
would flip that aggregate DOWN and fail liveness too, restarting the JVM.

Fix: `src/main/resources/application.yml` now declares
`management.endpoint.health.group.liveness.include: livenessState` (process-internal only)
and `...readiness.include: readinessState,db,redis,custom`. All four deployment manifests
above now point startup+readiness at `/actuator/health/readiness` and liveness at
`/actuator/health/liveness`.

Live fault-injection proof (real command output, not a static review):

```
$ date -u; kubectl -n inner-cosmos-w3 scale statefulset inner-cosmos-postgres --replicas=0
T0 scale-down: 2026-07-23T03:59:13.227Z
statefulset.apps/inner-cosmos-postgres scaled
```

Polling both API pods directly while PostgreSQL was down:

```
--- poll 1 at 2026-07-23T03:59:24.020Z ---
inner-cosmos-api-...-cznjj   0/1   Running   0
inner-cosmos-api-...-wbc5v   0/1   Running   1 (10m ago)
$ wget -qO- http://localhost:8080/actuator/health/readiness
wget: server returned error: HTTP/1.1 503
$ wget -qO- http://localhost:8080/actuator/health/liveness
{"status":"UP"}
--- poll 2 at 2026-07-23T03:59:58.940Z ---   (same: 0/1, readiness 503, liveness UP)
--- poll 3 at 2026-07-23T04:00:33.922Z ---   (same: 0/1, readiness 503, liveness UP)
```

Both API pods went READY 0/1 (removed from Service endpoints) within about 11s of the
PostgreSQL outage (matches readinessProbe periodSeconds:5, failureThreshold:2), and
`/actuator/health/liveness` stayed `{"status":"UP"}` on every single poll across the full
~2-minute outage window. RESTARTS counts never changed: `cznjj` stayed at 0,
`wbc5v` stayed at 1 (that one restart pre-dates this test - see
CN-ZERO-LOSS-DRAIN-001's hard-kill step - and did not increase during the DB outage).
Recovery:

```
$ kubectl -n inner-cosmos-w3 scale statefulset inner-cosmos-postgres --replicas=1
T_recover: 2026-07-23T04:01:15.635Z
...
READINESS_RECOVERED at 2026-07-23T04:01:30.460Z   (both pods back to 1/1)
```

Result: a ~2-minute PostgreSQL outage caused zero pod restarts and correct
traffic drain/recovery - the exact failure mode (liveness-driven restart storm on a
dependency blip) that 6.1-1 requires fixed is empirically absent.

## 6.1-2 - role-specific metrics, real Prometheus scrape per role (fixed + proven)

Before: `deploy/k8s/base/app-deployment.yml` had no `prometheus.io/*` annotations at
all (only `kind-dev`'s copy did); `application-prod.yml` narrowed
`management.endpoints.web.exposure.include` to `health,info`, silently dropping
`metrics,prometheus` for every profile that layers on `prod` (academy-eks, commercial-sg,
local-complete all activate `prod,<overlay>` together) - a real deployment would 404 on
`/actuator/prometheus` outside the dev-profile kind demo.

Fix: added the annotation triple to `base/app-deployment.yml` and restated
`health,metrics,prometheus,info` explicitly in `application-prod.yml`. All three
`kind-full` role deployments (api/worker/scheduler, ports 8080/8081/8082) carry it.

Live proof - deployed the full observability stack (`deploy/k8s/observability`,
Prometheus v2.54 + Grafana 11.3) into this kind cluster and queried real targets:

```
$ curl -s http://localhost:19090/api/v1/targets | ...
inner-cosmos-w3 inner-cosmos-api-...-wbc5v        api       up  http://10.244.0.8:8080/actuator/prometheus
inner-cosmos-w3 inner-cosmos-scheduler-...-hh4jq  scheduler up  http://10.244.0.7:8082/actuator/prometheus
inner-cosmos-w3 inner-cosmos-worker-...-2cknt     worker    up  http://10.244.0.10:8081/actuator/prometheus
inner-cosmos-w3 inner-cosmos-api-...-cznjj        api       up  http://10.244.0.16:8080/actuator/prometheus
```

Real per-role metric samples (not just "up"):

```
$ curl 'http://localhost:19090/api/v1/query?query=jvm_memory_used_bytes{component="worker",area="heap"}'
{...,"component":"worker",...,"pod":"inner-cosmos-worker-...","value":[...,"26214400"]}
$ curl 'http://localhost:19090/api/v1/query?query=jvm_memory_used_bytes{component="scheduler",area="heap"}'
{...,"component":"scheduler",...,"pod":"inner-cosmos-scheduler-...","value":[...,"47185920"]}
```

All three roles are independently discovered and scraped with distinct, correct ports
and correct `component` labels.

## 6.1-3 - NetworkPolicy: real deny/allow probe (not just object existence)

Cluster CNI note: this kind build's `kindnetd` (`v20260528-9350166c`) DOES enforce
`NetworkPolicy` (this is not true of all kind/kindnetd versions historically, so it was
verified empirically rather than assumed).

Positive control - a pod labeled `app.kubernetes.io/name: inner-cosmos` (matching the
`inner-cosmos-data` policy's allow-list):

```
$ kubectl -n inner-cosmos-w3 exec np-probe-allowed -- timeout 4 nc -vz inner-cosmos-postgres 5432
inner-cosmos-postgres (10.244.0.13:5432) open   (exit 0)
$ kubectl -n inner-cosmos-w3 exec np-probe-allowed -- timeout 4 nc -vz inner-cosmos-redis 6379
inner-cosmos-redis (10.96.134.76:6379) open     (exit 0)
```

Negative control - an otherwise-identical pod WITHOUT that label:

```
$ kubectl -n inner-cosmos-w3 exec np-probe -- timeout 4 nc -vz inner-cosmos-postgres 5432
punt!  (command terminated with exit code 143 - timed out, no RST, no connection)
$ kubectl -n inner-cosmos-w3 exec np-probe -- timeout 4 nc -vz inner-cosmos-redis 6379
punt!  (same: silent drop, exit 143)
```

Silent timeout (rather than "Connection refused") is exactly the signature of an
enforced default-deny NetworkPolicy dropping the SYN, not an application-level refusal.
This confirms `deploy/k8s/overlays/kind-full/network-policy.yaml` (mirrors
`base/app-network-policy.yml` + `academy-eks/{data,runtime}-network-policy.yml`) actually
restricts cross-pod data-port access on this CNI, not merely declares an object.

### A real integration bug this exposed and fixed

Turning on real NetworkPolicy enforcement broke the PostgreSQL backup Job:
`deploy/k8s/backup/pg-backup.yaml`'s pod template carried NO
`app.kubernetes.io/name: inner-cosmos` label, so once the `inner-cosmos-data` policy was
actually enforced, `pg_dump` inside the backup Job hung forever (silent packet drop,
not an auth/connection error - confirmed live: the Job sat "Running" for 40s+ with `pg_dump`
still alive per /proc, before it was diagnosed and killed). Fixed by adding the same
`app.kubernetes.io/name: inner-cosmos` label to the Job's pod template (see
`pg-backup.yaml`'s new `template.metadata.labels`). Re-ran and confirmed:

```
$ kubectl -n inner-cosmos-w3 create job --from=cronjob/inner-cosmos-pg-backup backup-test-2
$ kubectl -n inner-cosmos-w3 get job backup-test-2
NAME            STATUS     COMPLETIONS   DURATION
backup-test-2   Complete   1/1           5s
$ kubectl logs job/backup-test-2
pg_dump -> /backups/innercosmos_20260723-035811.dump
backup ok: -rw-r--r-- 1 postgres postgres 247462 ... innercosmos_20260723-035811.dump
```

This is the kind of dependency the credibility fixes must be checked against each other
for: hardening NetworkPolicy in isolation would have silently broken backups in any real
deployment that also applied it, with no error surfaced until someone tried to restore.

## 6.1-4 - backup CronJob overlay wiring + honest same-node RPO/RTO disclosure

Before: `deploy/k8s/backup/pg-backup.yaml` was not referenced by ANY
`kustomization.yml` (base or academy-eks) - `kubectl apply -k overlays/academy-eks` would
silently skip backups entirely, despite `evidence/g8/BACKUP-RESTORE-001` implying backups
run in-cluster. Fixed: added `deploy/k8s/backup/kustomization.yaml` and wired
`../../backup` into `overlays/academy-eks/kustomization.yml`
(`kubectl kustomize deploy/k8s/overlays/academy-eks` now renders the CronJob - confirmed
offline, no live Academy session used).

Same-node disclosure (live, on kind): the `inner-cosmos-postgres` PVC and the new
`inner-cosmos-backups` PVC both bind through the same default StorageClass
(`standard` / `rancher.io/local-path`):

```
$ kubectl get pv -o custom-columns=NAME:.metadata.name,STORAGECLASS:.spec.storageClassName,CLAIM:.spec.claimRef.name,PATH:.spec.hostPath.path
pvc-12d13...  standard  data-inner-cosmos-postgres-0  /var/local-path-provisioner/pvc-12d13.../inner-cosmos-w3_data-inner-cosmos-postgres-0
pvc-a1c50...  standard  inner-cosmos-backups          /var/local-path-provisioner/pvc-a1c50.../inner-cosmos-w3_inner-cosmos-backups
```

Both PVs are hostPath directories under /var/local-path-provisioner/ on the cluster's
single node (`kubedeploy-control-plane` - this kind cluster has exactly one node, so
this is unavoidable locally). This concretely confirms document 24's warning: a same-node
static-PV backup must not be described as off-cluster disaster recovery. Honest RPO/RTO
statement: this backup protects against logical/application-level data loss (bad
migration, accidental DROP TABLE, application bug) with RPO = the CronJob's schedule
(daily 03:00, or on-demand) and RTO = one pg_restore run (seconds at this data volume,
previously measured end-to-end in `evidence/g8/BACKUP-RESTORE-001`). It provides zero
protection against node loss, cluster loss, or storage-class failure - that requires an
actual off-cluster copy (object storage), which remains unimplemented. Verifying whether
the real academy-eks overlay's default StorageClass (EBS/gp2, not hostPath) gives a
genuinely different-node result is real, remaining work that needs a live Academy session
(out of scope this session per the "no live AWS" instruction).

## 6.1-5 - evidence de-identification (separate commit, already reconciled)

See commit `b2d0efb` (before this evidence file's commit): `evidence/g8/EKS-LIVE-002/summary.md`
and `docs/goal/single-session-state.yml` leaked a real AWS account id, ECR registry hostname,
and two ELB DNS names from a prior live-EKS session (introduced at commits `38fd43d`,
`a172a3d`). None are credentials - redacted to `<redacted-account-id>` /
`<redacted-elb-hostname>` matching the existing masking convention seen in
`evidence/academy/*/summary.md`. Working-tree only; the values remain in git history at
those two commits (history rewrite is a separate, irreversible, Supervisor-gated action,
intentionally not performed here).

## 6.1-6 - schema gate drift (real bug found + fixed, expand-contract redesign deferred)

`deploy/k8s/base/app-config.yml`'s `INNER_COSMOS_EXPECTED_SCHEMA_VERSION` still said "21"
while the highest checked-in Flyway migration is `V22__slow_letter_reply_link.sql` (V21 was
reassigned to mobile push delivery per document 24's W0V notes). This is a real,
machine-checked gate:

```
$ powershell ./scripts/academy/validate-schema-version.ps1     (BEFORE the fix)
Schema gate drift: manifest expects V21 but highest Flyway migration is V22.
$ powershell ./scripts/academy/validate-schema-version.ps1     (AFTER the fix)
Status                  : PASS
HighestFlywayMigration  : V22
ManifestExpectedVersion : 22
GatedWorkloads          : 3
FailedMigrationPolicy   : FAIL_CLOSED
```

Left unattempted: the deeper "exact-match to version-compatible range + expand-contract
migration contract" redesign document 24 item 6 calls for. That redesign exists
specifically to unblock CN-PROGRESSIVE-DELIVERY (a stable+canary deployment running two
schema-compatible app versions simultaneously); since progressive delivery was not
attempted this session (it's a secondary hard gate, not one of the three mandatory hero
gates), the exact-match gate is left as-is beyond fixing its version drift.

## What was NOT done in Phase 1

- Full re-verification of the academy-eks TLS/OIDC path against a live AWS Academy
  session - out of scope per this session's instructions (kind only).
- An actual off-cluster (object storage) backup copy - genuinely unimplemented, disclosed
  honestly above rather than worked around.
- Deeper NetworkPolicy egress micro-tests beyond the data-tier deny/allow pair above (an
  attempted API-to-arbitrary-pod egress probe was inconclusive due to a kubectl exec
  backgrounding artifact, not re-attempted given time budget - the data-tier result above
  is the one document 24 explicitly calls out (tighten cross-namespace data ports) and
  is conclusive).
