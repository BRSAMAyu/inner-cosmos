# Inner Cosmos

[中文说明](README.zh-CN.md) · [Current release-candidate handoff](对齐文档/22-最终集成与教师演示候选状态.md) · [Deployment handoff](对齐文档/18-组员与Coding-Agent启动部署交接指南.md) · [Complete-product goal](goal-objective.md) · [Acceptance ledger](docs/goal/complete-product-acceptance.yml)

Inner Cosmos is an AI-native self-understanding and slow-social platform. Aurora turns natural conversations into a long-lived, user-correctable model of memories, values, relationships, emotions, and goals. With explicit consent, that model can be compiled into bounded Echo Capsules that help people discover meaningful resonance before deciding whether to connect as humans.

This repository is not a finished commercial release yet. It is an actively implemented complete-product program with reproducible acceptance gates. The current application already spans a five-space React product shell, a stateful Aurora runtime, memory provenance and correction, a data-driven starfield, versioned Capsule Genome scaffolding, resonance discovery, slow letters, psychology skills, Capacitor mobile shells, and local/AWS Academy deployment profiles. Real-provider quality, complete bilingual and accessibility coverage, production operations, independent experience review, and several human release gates remain `IN_PROGRESS` or `UNASSESSED`; see the machine-readable ledger for the exact current state.

## Why it is different

- **Living Aurora** — typed SSE choreography, multi-message plans, interruption, stop/replan, durable WakeIntents, relationship state, and versioned Self/Constitution/Emergence foundations.
- **Living Inner Cosmos** — memories and profile claims retain provenance, confidence, lifecycle, corrections, and downstream invalidation; the starfield exposes why the system believes something.
- **Resonance Network** — authorized memories compile into versioned Echo Capsules with boundaries, sandbox feedback, public discovery, explainable matching, capsule conversations, and slow letters.
- **Psychology with guardrails** — versioned, non-diagnostic skills with consent, evidence metadata, risk paths, and user-controlled retention.
- **One product across surfaces** — a React/TypeScript web app evolving toward a verified PWA, Capacitor Android/iOS projects, and one Java artifact that can run as API, worker, scheduler, or migration roles.
- **Honest cloud proof** — `local-complete` preserves full product semantics; `academy-eks` proves Kubernetes behavior within Learner Lab constraints; `commercial-sg` remains the production target rather than a claim about the teaching account.

## Architecture snapshot

| Layer | Current complete-product baseline |
|---|---|
| Backend | Java 21, Spring Boot 3.5.14, Spring MVC/SSE, Spring Security, MyBatis-Plus |
| Data | PostgreSQL 16 + pgvector + Flyway for production truth; Redis for sessions/rates/leases; H2 only for zero-config development and focused tests |
| Frontend | React 19, TypeScript, Vite, Vitest, Playwright; legacy physical pages remain only where parity is not yet complete |
| Mobile | Capacitor 8 with Android/iOS projects, secure storage, network, app lifecycle, deep-link and push foundations |
| AI | Provider gateway for Mock, GLM, MiniMax, DeepSeek, MiMo/ASR, and OpenAI-compatible endpoints; production fails closed without a real provider |
| Delivery | Docker, Compose, Kustomize, Gateway API, multi-role Kubernetes workloads, signed OCI workflow, SBOM and security gates |

The target is an evolvable modular monolith with independently scalable runtime roles, not premature microservices. Product and architecture authority lives in [`goal-objective.md`](goal-objective.md) and [`对齐文档/README.md`](对齐文档/README.md).

## Quick start: offline-safe development

Requirements: JDK 21. The repository Maven Wrapper pins Maven 3.9.9. Node.js 22+ is only required when changing the React application.

```powershell
git clone https://github.com/BRSAMAyu/inner-cosmos.git
cd inner-cosmos
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080/app/aurora/](http://localhost:8080/app/aurora/) and register a local account. The default `dev` profile uses file-backed H2 and Mock AI, so it does not require credentials. To use disposable demo data explicitly, set `SEED_ENABLED=true` and run the `demo` profile; production always disables demo seeding.

For frontend work:

```powershell
cd web
npm ci
npm test
npm run build
```

`npm run build` writes the production AppShell bundle into Spring Boot's static resources. The Java application serves the same built UI.

## Real-provider development

Keys are operator-owned local inputs. Never put a key in YAML, Markdown, command history, a committed `.env`, a Kubernetes manifest, or a chat transcript. Inject it into the current process or a Git-ignored local secret manager:

```powershell
$env:LLM_MODE = 'dev'
$env:LLM_PROVIDER = 'glm'
$env:GLM_API_KEY = (Get-Content -Raw "$HOME\.inner-cosmos\glm.key").Trim()
.\mvnw.cmd spring-boot:run
```

Equivalent provider-specific variables include `MINIMAX_API_KEY`, `DEEPSEEK_API_KEY`, `MIMO_API_KEY`, and `GLM_ASR_API_KEY`. `prod` and `local-complete` disable Mock fallback and fail fast when required identity, database, Redis, TLS, or provider configuration is missing.

## Deployment profiles

| Profile | Use it for | Entry point | Important boundary |
|---|---|---|---|
| `dev` / `demo` | Fast local development | `./mvnw spring-boot:run` | H2 and optional Mock; not production evidence |
| `local-complete` | Full product and real-provider acceptance | `scripts/local-complete.ps1` | Requires Docker, PostgreSQL/pgvector, TLS Redis, OIDC, and local secret injection |
| `academy-eks` | Course Kubernetes evidence on the pre-provisioned Learner Lab | `scripts/academy/preflight.ps1`, then `scripts/academy/deploy.ps1` | Fixed `us-east-1`, short-lived credentials, static PV, no workload SQS identity, no human AWS keys in Pods |
| `commercial-sg` | Future Singapore production | architecture/IaC acceptance track | Not yet a deployable production claim; Terraform, managed services, DR, legal and owner gates remain open |

The exact teammate/Coding Agent runbook, environment contract, validation commands, EKS limitations, and recovery checklist are in [`对齐文档/18-组员与Coding-Agent启动部署交接指南.md`](对齐文档/18-组员与Coding-Agent启动部署交接指南.md). [`DEPLOY.md`](DEPLOY.md) is the short deployment index.

## API contract

The first stable external slice is published as [OpenAPI 3.1 v1](src/main/resources/static/openapi/inner-cosmos-v1.yml) and served at `/openapi/inner-cosmos-v1.yml`. Its checked-in TypeScript declarations are generated with `cd web && pnpm run api:generate`; `api:check` and `api:diff` enforce drift and compatibility. New clients should use the `/api/v1` auth, Aurora, capsule, slow-letter, and persona routes covered there. Core writes require `Idempotency-Key`, capsule-boundary writes also require `If-Match`, and Aurora recovery uses `Last-Event-ID`. Legacy `/api` routes remain during migration; the acceptance ledger intentionally keeps the overall API contract `IN_PROGRESS` until all public domains, pagination, and cross-Pod live SSE are closed.

## Verification

Use risk-proportional checks during development:

```powershell
# Current tree and current branch history must contain no credentials
.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History

# Backend focused/full gate
.\mvnw.cmd test

# Frontend unit/type/build gate
Push-Location web
npm ci
npm test
npm run build
Pop-Location

# Offline-safe Academy manifest/preflight validation
.\scripts\academy\validate-manifests.ps1
.\scripts\academy\preflight.ps1 -Mode Offline
```

Some PostgreSQL/Redis integration tests require a working Docker engine. A missing Docker daemon is an infrastructure failure, not permission to mark those tests passed. Current evidence lives under [`evidence/`](evidence/) and every acceptance status must point to reproducible proof.

## Security and secret handling

- The current tree uses environment-variable references only; real keys are not configuration defaults.
- `.env`, local application overrides, kubeconfigs, AWS folders, private keys, certificates, mobile signing material, databases, and generated evidence are ignored.
- CI scans both the current tree and the reachable branch history. Run both scans before publishing.
- Removing a key from the latest file does **not** remove it from Git history. Rotate any exposed credential with its provider and scrub the reachable history before pushing.
- AWS Academy access keys are four-hour human-session credentials. They may be used by the local AWS CLI to reach the teaching account, but must never be injected into a Pod or committed.

Security rotation and human sign-off remain tracked by the acceptance ledger; automated scanning cannot prove provider-side revocation.

## Coding Agent bootstrap

Before making product or deployment decisions, a Coding Agent must read, in order:

1. [`AGENTS.md`](AGENTS.md)
2. [`goal-objective.md`](goal-objective.md)
3. [`对齐文档/README.md`](对齐文档/README.md)
4. For current integration/demo continuation: [current handoff](对齐文档/22-最终集成与教师演示候选状态.md) and [machine state](docs/goal/release-candidate-state.yml). Track documents are historical implementation evidence.
5. For a single-agent continuation: [`对齐文档/17-单会话持续Goal模式执行协议.md`](对齐文档/17-单会话持续Goal模式执行协议.md).
6. [`docs/goal/complete-product-acceptance.yml`](docs/goal/complete-product-acceptance.yml) (read-only inside either parallel track).
7. [`docs/goal/two-track-convergence.yml`](docs/goal/two-track-convergence.yml) or [`docs/goal/single-session-state.yml`](docs/goal/single-session-state.yml), matching the execution mode.
8. The deployment handoff linked above when the task involves running or deploying the system.

The agent must inspect live `HEAD`, the working tree, processes, configuration, and evidence before trusting summaries. A commit, green test, or Kubernetes screenshot is a checkpoint, not proof that the complete product is finished.

## Repository map

```text
src/main/java/                 Spring Boot product and AI runtime
src/main/resources/            configuration, Flyway migrations, built web assets
web/                           React PWA and Capacitor mobile projects
deploy/compose/                local-complete PostgreSQL/Redis/TLS profile
deploy/k8s/                    Kubernetes base and Academy overlay
scripts/                       build, verification, local and Academy operations
ai-lab/                        reproducible AI evaluations and pairwise tooling
docs/goal/                     machine-readable acceptance and recovery state
docs/tracks/                   two parallel complete-product execution specifications
对齐文档/                       authoritative product, architecture and execution specs
evidence/                      reproducible implementation and acceptance evidence
```

## Contribution discipline

Preserve unrelated working-tree changes, keep secrets external, bind every meaningful change to an acceptance gap, and verify observable behavior rather than counting files or tests. Do not silently weaken Aurora, memory provenance, Capsule fidelity, privacy, or deployment truth to make a gate easier to close.

## License

No open-source license has been declared yet. Unless a license file is added by the repository owner, treat the code and assets as all rights reserved.
