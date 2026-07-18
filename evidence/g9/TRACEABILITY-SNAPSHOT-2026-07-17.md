# Traceability snapshot — 2026-07-17 (supervisor-first session)

> NOT a FINAL-TRACEABILITY PASS claim — an honest mid-flight map after one continuous session on branch
> `feat/supervisor-k8s-ai-g9`. Commits: e5b081c (Phase A), 6acd19e (Phase C ops), e596606 (Phase B AI).

## Supervisor's two questions — where they stand now

### Q1 "Is the k8s solid?" — strengthened from "good manifests" to "exercised operability"
| Capability | Before | Now | Evidence |
|---|---|---|---|
| Manifests build | base+academy build offline | unchanged (verified) | `kubectl kustomize` 6 + 20 |
| Real cloud deploy | ACADEMY-LIVE-001 (self-attested) | unchanged; HPA claim corrected 2..6→2..4 | evidence/academy/ACADEMY-LIVE-001 |
| Observability | none in-cluster | Prometheus+Grafana **as-code**, live dashboards | evidence/g8/OPS-OBSERVABILITY-001 |
| HPA autoscaling | installed, never tested | **exercised 2→4→2 under load** | evidence/g8/HPA-LOAD-001 |
| Resilience | pod-recovery only (academy) | **pod-kill + zero-downtime rolling + node-drain w/ PDB** | evidence/g8/OPS-RESILIENCE-001 |
| Backup/restore | orphan shell script | **CronJob + verified restore drill** | evidence/g8/BACKUP-RESTORE-001 |
| Runnable locally on k8s | prod-only | **kind-dev overlay runs the real image, zero secrets** | deploy/k8s/overlays/kind-dev |

### Q2 "Does the vision run end-to-end?" — from "skeleton runs" to "real AI proven, honestly"
| Capability | Before | Now | Evidence |
|---|---|---|---|
| One-command demo | `docker compose up` failed (all prod) | **keyless dev compose boots healthy** | deploy/compose/dev.yml, evidence/g8/DEV-COMPOSE-SMOKE-001 |
| Real LLM through product | never | **register→chat→real DeepSeek reply (12+30=42, 5.9s)** | evidence/g8/DEV-COMPOSE-SMOKE-001 |
| Dual-kernel effectiveness | code only, unrun on real provider | **real-pairwise executed on DeepSeek** (honest: lexical rubric doesn't favor it; blind-human panel is the gate) | evidence/innovation/INNO-EVAL-003 |
| Semantic retrieval | disabled default | **proven +100pts recall vs lexical**; real GLM embedder reachable | evidence/innovation/INNO-INNER-008 |

## Gate status delta (this session)
- **G8**: OPS-OBSERVABILITY UNASSESSED→IN_PROGRESS; OPS-RESILIENCE UNASSESSED→IN_PROGRESS;
  ACADEMY-RELIABILITY strengthened (HPA/backup/resilience exercised) + drift corrected.
- **G4**: AURORA-DUAL-KERNEL — real-provider pairwise executed (still IN_PROGRESS; blind-human gate).
- **G5/G6**: RETRIEVAL-QUALITY — semantic-beats-lexical proven; real embedding path verified.
- **G1**: Dockerfile portability fix (image now builds on restricted networks).
- **G0**: added a portable secret scanner (`scripts/scan-secrets.sh`) for machines without PowerShell.

## Cross-cutting fixes
- Dockerfile `go-offline` made best-effort (was breaking image builds on this network).
- False HPA `2..6` evidence claim corrected to the real `2..4`.
- `scripts/scan-secrets.sh` — portable, self-tested (catches planted token).

## Remaining machine-doable (no human gate) — for continuation (see single-session-state.yml supervisor_track)
- **G2 (Phase D)**: ARCH-MODULES (needs Spring Modulith module definitions — codebase is layer-structured,
  so this is a scoped design task, not a drop-in; ArchUnit layer tests are an alternative), EVENT-RELIABLE
  (outbox→idempotent→retry→DLQ Testcontainers), RUNTIME-ROLES (5-role isolation on compose/kind).
- **G8 remainder**: EKS-IAC (Terraform to validate/plan; install terraform), CI hardening (SBOM/SAST/scan
  gating), actuator `/prometheus` lockdown (spawned as a follow-up task).
- **G5/G6 remainder**: full app-E2E semantic retrieval with real GLM embedder; FORGET propagation to
  prompt-cache/sync-queue/analytics/backup.
- **G9**: FINAL-E2E (Playwright), 8–12 min reproducible demo (dev compose + kind), FINAL-OPERATIONS runbook
  (non-author rehearsal), full FINAL-TRACEABILITY matrix → then declare RELEASE_CANDIDATE.

## Standing human gates (unchanged; do not block the line)
REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW (blind human scoring of AI evals), HG-SECRET-ROTATION
(**rotate the DeepSeek+GLM keys pasted this session**), HG-PRODUCTION-ACCOUNTS (real AWS apply / device
signing), HG-PRIVACY-LEGAL, HG-PSYCHOLOGY-REVIEW, HG-REAL-USERS (FINAL-USABILITY).
