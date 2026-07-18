# EKS-LIVE-002 — the app running on a real AWS EKS cluster (independent, not self-attested)

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Operator-provided AWS Academy Learner Lab creds
> (temporary, env/`~/.aws` only — never committed). Directly answers the supervisor's Q1 with a live cloud
> run beyond the earlier self-attested ACADEMY-LIVE-001.

## Environment (read-only verified)
- Cluster **MyEKS**, Kubernetes **1.36**, platform **eks.6**, status ACTIVE, account 477296622190, us-east-1.
- **2 real EC2 worker nodes**, arch **amd64**/linux, both Ready.
- Cluster already runs the **envoy-gateway** Gateway API controller (GatewayClass `eg`, Accepted), external-dns,
  `gp2` EBS StorageClass, and a working metrics-server (`kubectl top` returns data → HPA-capable).

## What was deployed
- Cross-built an **amd64** image (the arm64 dev image can't run on amd64 nodes) via a thin runtime Dockerfile
  that copies the pre-built fat-jar onto an amd64 JRE base (avoids a slow emulated Maven build);
  pushed to ECR: `477296622190.dkr.ecr.us-east-1.amazonaws.com/inner-cosmos:eks-dev`
  @ `sha256:dad7f1edf41d876c6cb5439b4827563610b42f5479c377a35f38a5cada23f033` (immutable digest).
- `deploy/k8s/overlays/eks-dev` (reuses `kind-dev`'s hardened workload — probes/PDB/HPA 2..4/rolling/
  security context/topology spread — swaps in the ECR image, adds a Gateway + HTTPRoute on GatewayClass `eg`).
- The disposable guestbook app was removed first (operator-authorized); the shared envoy controller kept.

## Verified live on real EKS
- **Rollout**: 2/2 replicas Ready, pods **spread across both EC2 nodes** (topology spread honored).
- **External ingress**: Gateway `Programmed=True`, HTTPRoute `Accepted`+`ResolvedRefs`; a real **AWS ELB**
  (`a738b9cbd5be0419ea8fe61c0eff1e09-628283061.us-east-1.elb.amazonaws.com`) serves the app —
  `GET /app/aurora/` returns the AppShell (`<title>Aurora · Inner Cosmos</title>`), `GET /actuator/health`
  returns `{"status":"UP","groups":["liveness","readiness"]}`. (Some 000s during the first ~2 min are normal
  cross-AZ ELB target-registration warmup; steady 200 after.)
- **Autoscaling (real cloud)**: under fortio load, HPA read `cpu: 339%/70%` and scaled **2 → 3 → 4**
  (SuccessfulRescale events), reaching 4 pods balanced **2+2 across the two nodes**.
- **Resilience (real cloud)**: deleting a pod kept the ELB serving **200 on all 5 probes** during recovery;
  a `rollout restart` completed with zero downtime (maxUnavailable=0 + surge).

## Boundary / remaining
- This is the **dev-profile** workload (H2 + Mock AI) — it proves the app *runs and operates* on genuine EKS
  (real nodes, real ELB ingress, real HPA/resilience). The full **prod academy-eks overlay** (static-PV
  PostgreSQL + Redis + TLS + OIDC + real provider, fail-closed) is the remaining step and needs real
  IdP/provider secrets; ACADEMY-LIVE-001 covers that shape on a prior Learner Lab session.
- Learner Lab credentials are temporary and auto-expire; this run is reproducible from a fresh cred set via
  `deploy/k8s/overlays/eks-dev` (see its header).
