# FINAL-DEMO-001 — reproducible 8–12 minute demo script

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Ledger: G9 `FINAL-DEMO`.
> Covers the four required elements: **product differentiation · AI depth · data lineage · K8s operations.**
> Every step below was exercised this session; evidence paths are cited inline.
>
> Prereqs (this machine): JDK 21, Node 22+, Docker Desktop running, `kind` + `kubectl`. Provider keys are
> operator-injected via env only (never committed) — the demo also runs fully keyless in Mock mode.

---

## Part 0 — one-command startup (30s) — *product runs at all*
```bash
docker compose -f deploy/compose/dev.yml up -d          # zero config, H2 + Mock AI
open http://localhost:8080/app/aurora/                  # five-space AppShell
```
Talking point: unlike the prod compose files (fail-closed, need real DB/Redis/LLM/OIDC secrets), this
keyless dev compose boots healthy in ~20s. Evidence: `evidence/g8/DEV-COMPOSE-SMOKE-001/`.

## Part 1 — product differentiation (2–3 min)
Register a local account → walk the five spaces (Aurora chat, memory starfield, capsule workbench,
resonance/letters, Me). Show a full conversation loop, memory formation, and a compiled Echo Capsule.
(Mock mode is fine for the walk-through; switch to real AI for Part 2.)

## Part 2 — AI depth (2–3 min) — *real provider + dual-kernel*
```bash
# real DeepSeek, fallback disabled so Mock cannot mask it:
LLM_PROVIDER=deepseek LLM_API_KEY=$DEEPSEEK_API_KEY LLM_ALLOW_FALLBACK=false \
  docker compose -f deploy/compose/dev.yml up -d
# send a message in the UI; Aurora replies from the real model (5–6s, real reasoning)
```
Then show the dual-kernel (planner→speaker) vs single-pass comparison artifact:
```bash
cd ai-lab && . .venv/bin/activate
python -m evals.cli.main real-pairwise --profile deepseek --seed 7 --output /tmp/pairwise
```
Talking point (honest): the dual-kernel runtime executes on a real model; the *quality* verdict is the
blind-human panel (the one standing gate). Evidence: `evidence/innovation/INNO-EVAL-003/`,
`evidence/g8/DEV-COMPOSE-SMOKE-001/`.

## Part 3 — data lineage (1–2 min) — *memory → semantic retrieval → provenance*
```bash
./mvnw -Dtest=MemoryEmbeddingCandidateIntegrationTest test   # semantic recall 1.0 vs lexical 0.0
```
Show that memories carry source/consent/provenance and that semantic retrieval beats lexical on hard
paraphrase/temporal/multi-hop cases, while consent-scoped memories are never sent to the provider.
Evidence: `evidence/innovation/INNO-INNER-008/`.

## Part 4 — Kubernetes operations (3–4 min) — *the headline for Q1*
```bash
# cluster already up: kind cluster 'inner-cosmos-ops' with the app + observability
kubectl -n inner-cosmos-dev get pods,hpa
kubectl -n observability port-forward svc/grafana 3000:3000 &   # http://localhost:3000 (admin/admin, local)
```
Show live:
- **Observability**: Grafana "Inner Cosmos — App Health & JVM" dashboard (replicas up, HTTP rate, JVM heap,
  CPU) fed by Prometheus scraping `/actuator/prometheus`. Evidence: `evidence/g8/OPS-OBSERVABILITY-001/`.
- **Autoscaling**: drive load and watch HPA scale 2→4 then back.
  ```bash
  kubectl -n inner-cosmos-dev run loadgen --image=fortio/fortio --restart=Never -- \
    load -qps 0 -t 120s -c 100 http://inner-cosmos-api.inner-cosmos-dev.svc:8080/actuator/prometheus
  watch kubectl -n inner-cosmos-dev get hpa
  ```
  Evidence: `evidence/g8/HPA-LOAD-001/`.
- **Resilience**: `kubectl -n inner-cosmos-dev delete pod <one>` → recovers; `kubectl rollout restart` →
  zero downtime; `kubectl drain <worker>` → PDB protects availability, pods reschedule. Evidence:
  `evidence/g8/OPS-RESILIENCE-001/`.
- **Backup/restore**: `kubectl -n inner-cosmos-dev create job --from=cronjob/inner-cosmos-pg-backup demo-backup`
  then show the restore drill. Evidence: `evidence/g8/BACKUP-RESTORE-001/`.

## Part 5 — reliability & credibility (1 min)
```bash
./mvnw -Dtest=JdbcOutboxRepositoryIntegrationTest test   # outbox → idempotent → retry → DLQ → replay (3/3)
```
Point at the cross-cutting integrity work: real EKS deploy evidence (ACADEMY-LIVE-001, with the HPA claim
corrected to the true 2..4), transactional outbox with dead-letter replay, and a portable secret scan.

---

## Known behaviors (verified 2026-07-17 on the prod EKS stack — NOT bugs)
- **Capsule boundary editor works** via the UI (correct payload = comma-joined topic strings +
  `maxConversationTurns` + `If-Match` version header). A raw-API smoke that sent arrays/omitted If-Match
  failed, but the SPA sends the right shape — safe to demo.
- **Slow letters are intentionally delayed ~3 minutes** (`estimatedArrivalAt = now + 3min`; `LetterDeliveryJob`
  runs every 60s, SENT→FLYING→DELIVERED). In the demo, show the "已寄出 / 在路上(FLYING)" state, or wait
  ~3–4 min for it to land in the recipient's inbox — do not expect instant arrival.
- **Semantic memory retrieval now uses real GLM embeddings + pgvector** on prod (INNO-INNER-009).

## Status / remaining
- This is the demo **script** with every step exercised this session. To reach `FINAL-DEMO` PASS: capture a
  single recorded run (screen capture) end-to-end, and have a **non-author** drive it from a clean checkout.
- FINAL-E2E (automated Playwright across the five spaces) and the non-author FINAL-OPERATIONS runbook
  rehearsal are the remaining G9 items before declaring RELEASE_CANDIDATE.
