# DEV-COMPOSE-SMOKE-001 — keyless dev compose + real-provider smoke

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Machine: macOS/arm64, Docker 29.6.1
> Serves: G9 one-command demo env; partial evidence toward G8 `LOCAL-COMPLETE` (real-provider config path).

## What was added

- **`deploy/compose/dev.yml`** — the first keyless developer/demo compose. Unlike `local-complete.yml`
  and the root `docker-compose.yml` (both prod-profile, fail-closed, requiring real DB/Redis/LLM/OIDC
  secrets), this runs the offline-safe `dev` profile (file-backed H2 + Mock AI) and boots with **zero
  configuration**. Real provider is opt-in via env only.
- **Dockerfile fix** — `dependency:go-offline` made best-effort (`|| true`). It was aggressively resolving
  an optional `flyway-core -> google-cloud-storage -> opentelemetry` transitive from `maven.pkg.github.com`,
  which this build network's buildkit resolver cannot reach, failing the whole image build. `package`
  remains the authoritative fail-fast resolution and pulls only what the app needs (proven: host tests pass
  without that artifact cached). Fixes image builds on restricted networks (dev, prod-compose, CI alike).
- **`scripts/scan-secrets.sh`** — portable bash/ripgrep mirror of `scan-secrets.ps1` (this machine has no
  PowerShell). Same two rules + allowlist; never prints matched values. Self-tested: catches a planted
  `sk-…`/`api_key:` token (exit 1), passes clean tree (exit 0).

## Verification (all commands run, output observed)

### Baseline (honest, green)
- Backend: **835 tests, 0 failures, 0 errors, 0 skipped** (surefire reports; Testcontainers included, Docker up).
- Frontend: **115 tests passed (21 files)** via `npm test`.
- `kubectl kustomize deploy/k8s/base` = 6 resources; `.../overlays/academy-eks` = 20 resources (offline build OK).
- `scripts/scan-secrets.sh` → PASS, 0 findings.

### Keyless dev compose
```
docker compose -f deploy/compose/dev.yml up -d   # zero env needed
```
- Container `inner-cosmos-dev-app-1` reaches **healthy in ~20s**.
- `GET /actuator/health` → **200**; `GET /app/aurora/` → **200** (five-space AppShell served).
- Fix applied: `MANAGEMENT_HEALTH_REDIS_ENABLED=false` in dev (dev uses in-memory fallbacks; the Redis
  health contributor was making health DOWN despite the app being fully usable).

### Real-provider smoke (DeepSeek, fallback disabled)
```
LLM_PROVIDER=deepseek LLM_API_KEY=<env-injected> LLM_ALLOW_FALLBACK=false docker compose -f deploy/compose/dev.yml up -d
```
- Startup log: `Creating LlmClient for provider: deepseek, mode: dev, fallbackAllowed: false` — the **real**
  client is wired; Mock cannot mask it.
- Full product API path exercised: `POST /api/v1/auth/register` → `login` → `POST /api/dialog/session/create`
  → `POST /api/v1/aurora/message` (with CSRF + `Idempotency-Key`).
- Real reply (verbatim), latency **~5.9s** (real LLM, not Mock's instant canned text):
  > 我不能像人类那样思考——我没有意识、感受或主观体验。但我可以用算法处理信息、理解你的话并生成回答。**12 加 30 等于 42。**……
- Correct arithmetic (12+30=42) that the deterministic Mock provider does not produce → conclusive that
  the response is from the real provider.

## Provider keys
Operator-injected via environment variables only; never written to any tracked file, log, or this evidence.
Both provided keys (DeepSeek, GLM) independently validated against their APIs (HTTP 200). Rotation after the
session remains the human gate `HG-SECRET-ROTATION`.

## Boundary / not yet done
- This is the **keyless dev** stack (H2 + optional real LLM), NOT the full `LOCAL-COMPLETE` profile
  (PostgreSQL/pgvector + Redis + real provider + core E2E). `LOCAL-COMPLETE` stays IN_PROGRESS.
- HPA drift corrected in `evidence/academy/ACADEMY-LIVE-001/summary.md` (manifest is `2..4`, not `2..6`).
