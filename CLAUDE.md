# CLAUDE.md — Inner Cosmos

This file orients an AI coding agent (Claude Code) working in this repository. Read it fully before
acting. It is the **accurate, current** entry point; some older docs describe a superseded V0.1
baseline (see "Document map" for which docs are authoritative).

Inner Cosmos (内宇宙) is an AI-native self-understanding and slow-social platform. **Aurora** turns
natural conversation into a long-lived, user-correctable model of a person (memories, values,
relationships, emotions, goals). With explicit consent, that model compiles into bounded **Echo
Capsules** that let people find meaningful resonance before deciding to connect as humans.

This is an actively implemented **complete-product ("完全体") program**, not a finished release. Work
is driven by machine-readable acceptance gates and reproducible evidence, and is intended to continue
autonomously toward the complete product (see "Autonomous continuation").

---

## Current stack (accurate as of this branch)

| Layer | Reality |
|---|---|
| Backend | Java 21, Spring Boot 3.5.x, Spring MVC + SSE, Spring Security, MyBatis-Plus |
| Data (prod) | PostgreSQL 16 + pgvector, Flyway migrations (`src/main/resources/db/migration/postgresql`), Redis (sessions, rate-limit, idempotency, streams, leases) |
| Data (dev/test) | H2 in-memory (`MODE=MySQL`) — zero-config, no Docker needed for most tests |
| Frontend | React 19 + TypeScript + Vite + Vitest, a single-page five-space AppShell in `web/`, built into `src/main/resources/static/app/aurora/` and served by Spring Boot |
| Mobile | Capacitor 8 Android/iOS projects in `web/` |
| AI | Provider gateway: Mock (default), GLM, MiniMax, DeepSeek, MiMo/ASR, OpenAI-compatible. `prod` fails closed without a real provider; `dev` falls back to Mock |
| Delivery | Docker, Compose (`local-complete`), Kustomize (`deploy/k8s` base + `academy-eks` overlay), Gateway API, multi-role workloads |

> Design remains an evolvable modular monolith with independently scalable runtime roles (API, AI
> worker, event worker, scheduler, migration) — not premature microservices.

---

## Run it locally (no API keys needed)

Prerequisites: **JDK 21** (a full JDK, not just a JRE — the Maven wrapper needs `JAVA_HOME`). Node.js
22+ only when changing the React app.

```bash
# from the repo root
./mvnw spring-boot:run           # macOS/Linux/Git-Bash
# or
.\mvnw.cmd spring-boot:run       # Windows PowerShell/cmd
```

If `JAVA_HOME` is unset (common on Windows), point it at your JDK 21 first, e.g.
`export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"` (Git-Bash) or the PowerShell equivalent.

Then open **http://localhost:8080/app/aurora/** and register a local account. The default `dev`
profile uses file-backed H2 and the **Mock AI provider**, so the full product loop works with no
credentials. Demo seed data is only injected when `SEED_ENABLED=true`.

Frontend work:

```bash
cd web
npm ci
npm test        # Vitest
npm run build   # writes the production AppShell into the Spring static resources
```

### Using a real AI provider (keys stay local, never committed)

Keys are operator-owned inputs. **Never** put a key in YAML, Markdown, a committed `.env`, a
manifest, a commit, or a chat/log. Inject per-process:

```bash
export LLM_MODE=dev
export LLM_PROVIDER=glm
export GLM_API_KEY="…"          # or MINIMAX_API_KEY / DEEPSEEK_API_KEY / MIMO_API_KEY / GLM_ASR_API_KEY
./mvnw spring-boot:run
```

`prod` and `local-complete` disable Mock fallback and fail fast if required identity, DB, Redis, TLS,
or provider config is missing.

---

## Verify (risk-proportional)

```bash
./mvnw test                      # full backend gate (835 tests green on this branch)
./mvnw test -Dtest=SomeTest      # focused during development
cd web && npm test && npm run build
pwsh scripts/scan-secrets.ps1    # tree must contain no credentials before pushing
```

- Some PostgreSQL/Redis integration tests use Testcontainers and need a running Docker engine. A
  missing Docker daemon is an infrastructure gap, **not** permission to mark those tests passed.
- `deploy/k8s` overlays validate offline with `kubectl kustomize deploy/k8s/base` and
  `kubectl kustomize deploy/k8s/overlays/academy-eks`.
- Evidence for every acceptance status lives under `evidence/`.

---

## How the code is organized

```text
src/main/java/com/innercosmos/   Spring Boot product + AI runtime (controllers, services, ai/*, streaming/*, safety/*, event/*, scheduler/*)
src/main/resources/              application*.yml, Flyway migrations (db/migration/postgresql), built web assets (static/app/aurora)
web/src/                         React AppShell (AuroraApp.tsx) + components/*, api.ts (generated + hand-written types), Capacitor
deploy/compose/                  local-complete PostgreSQL/Redis/TLS profile
deploy/k8s/                      Kustomize base + academy-eks overlay
scripts/                         build, verification, secret scan, local & Academy ops
docs/goal/                       machine-readable acceptance ledger + single-session recovery state
对齐文档/                          authoritative product, architecture, UX and execution specs
evidence/                        reproducible implementation & acceptance evidence
```

Conventions: controllers extend `BaseController`, resolve the owner via `currentUserId(session)`, and
return `ApiResponse<T>`; services split interface/impl; MyBatis-Plus `BaseMapper` (no XML); entities
map camelCase↔`snake_case`; tables are `tb_*` with `created_at`/`updated_at`; every read is
owner-scoped (IDOR is a real risk — filter by user id, not just the path id).

---

## Document map (what is authoritative vs historical)

Read in this order before making product/architecture/deployment decisions:

1. [`goal-objective.md`](goal-objective.md) — the complete-product goal and authority hierarchy.
2. [`对齐文档/README.md`](对齐文档/README.md) — index of the authoritative spec system (product,
   architecture, UX, AI innovation, campaigns).
3. [`对齐文档/16-体验优先的完全体重构策略与产品战役.md`](对齐文档/16-体验优先的完全体重构策略与产品战役.md) — the campaign strategy (A–E).
4. For the current two-PR convergence, read [`对齐文档/19-双轨并行完全体收敛与交接计划.md`](对齐文档/19-双轨并行完全体收敛与交接计划.md), [`对齐文档/20-当前状态重对账与完全体差距基线.md`](对齐文档/20-当前状态重对账与完全体差距基线.md), and the assigned [`docs/tracks/`](docs/tracks/) spec.
5. For single-agent continuation, read [`对齐文档/17-单会话持续Goal模式执行协议.md`](对齐文档/17-单会话持续Goal模式执行协议.md).
6. [`docs/goal/complete-product-acceptance.yml`](docs/goal/complete-product-acceptance.yml) — the machine-readable acceptance ledger; it is read-only inside either parallel track.
7. Use [`docs/goal/two-track-convergence.yml`](docs/goal/two-track-convergence.yml) for parallel work or [`docs/goal/single-session-state.yml`](docs/goal/single-session-state.yml) for single-agent recovery.
8. [`对齐文档/18-组员与Coding-Agent启动部署交接指南.md`](对齐文档/18-组员与Coding-Agent启动部署交接指南.md) — the deployment/run handoff runbook (local, `local-complete`, `academy-eks`).
9. [`README.md`](README.md) / [`README.zh-CN.md`](README.zh-CN.md) — accurate project overview and quick start.

**Historical / superseded:** [`AGENTS.md`](AGENTS.md) contains rich, still-useful module and
design-pattern notes, but its tech-stack table, project structure, and run commands describe the
**V0.1 baseline** (Java 17, Spring Boot 3.3, pure static HTML, MySQL, `/pages/index.html`). For how to
build/run/architect *today*, trust this file, the README, and the authoritative specs above — not
AGENTS.md's stack/run sections.

---

## Working norms (follow these)

- **Evidence before assertions.** Never claim done/passing without running the command and seeing the
  output. A commit, a green test, or a screenshot is a checkpoint, not proof the product is finished.
- **TDD for behavior.** Write a failing test that pins the gap, then make it pass. Extend the labeled
  evaluation gates under `src/test/.../evaluation` and `evidence/` rather than self-scoring.
- **Bind work to an acceptance gap.** Every meaningful change should map to an item in the acceptance
  ledger; in a parallel track update only its track-local status/evidence, then let the integrator reconcile
  the ledger + `single-session-state.yml` after both PRs.
- **Preserve unrelated work.** Inspect live `HEAD`, working tree, and evidence before acting; do not
  overwrite or discard in-flight assets you did not create.
- **Secrets stay external.** Env-vars only; run `scripts/scan-secrets.ps1` before pushing. Removing a
  key from the latest file does not remove it from history.
- **Owner-scope everything** and never weaken Aurora quality, memory provenance, Capsule fidelity,
  privacy, or deployment truth just to close a gate.
- **Risk-proportional testing.** Focused tests during development; full `./mvnw test` + frontend at
  cross-domain / campaign / release checkpoints.
- **Real AI must be proven with real providers, vertical scenarios, and non-author review** — code
  volume, mocks, or self-scoring do not substitute. Real-provider quality is a documented human gate.

## Autonomous continuation (the "Goal" mode)

This project is meant to be advanced in a **single continuous session** toward the complete product,
not one ticket at a time. On resuming:

1. Read the document map above; re-derive reality from live `HEAD`, the working tree, and `evidence/`.
2. Read the operator's standing directive [`docs/goal/loop-goal-directive.md`](docs/goal/loop-goal-directive.md)
   (phased roadmap, experience-overhaul priority, agent-handoff protocol for the continuous /loop run),
   then pick up the `active_front` / `priority_queue` in `docs/goal/single-session-state.yml`.
3. Implement the next highest-value, machine-executable gap; verify; write evidence; update the
   ledger + state; commit a recoverable checkpoint; push; then immediately continue to the next gap.
4. A checkpoint, a passing test, or a context compaction is **not** a stopping point. Stop only when
   the acceptance ledger's required items truly PASS, or when the only remaining work is human-gated
   (real keys/accounts/devices/experts/legal/real users) — and log each such gate explicitly.

Keep commits scoped and messages descriptive; end commit messages with the project's
`Co-Authored-By` trailer if configured. Never force-push shared `main`.
