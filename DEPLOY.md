# Inner Cosmos Deployment Index

[English README](README.md) · [中文 README](README.zh-CN.md) · [Executable teammate/Coding Agent handoff](对齐文档/18-组员与Coding-Agent启动部署交接指南.md)

This file is intentionally a short routing page. The handoff guide above is the maintained runbook with prerequisites, environment contracts, exact commands, validation, failure recovery, and AWS Academy cleanup rules.

## Supported profiles

| Profile | Command entry | Intended evidence |
|---|---|---|
| `dev` | `./mvnw spring-boot:run` | Offline-safe H2/Mock development |
| `demo` | `SEED_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` | Disposable demo data; never production evidence |
| `local-complete` | `scripts/local-complete.ps1` | Full product semantics with PostgreSQL/pgvector, TLS Redis, real Provider, OIDC, and no Mock fallback |
| `academy-eks` | `scripts/academy/preflight.ps1`, then `scripts/academy/deploy.ps1` | Teaching-account Kubernetes behavior on the pre-provisioned `us-east-1` EKS cluster |
| `commercial-sg` | Acceptance/IaC track | Future Singapore production target; not yet a completed deployable claim |

## Safe quick start

```powershell
.\scripts\scan-secrets.ps1
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080/app/aurora/` and register a local user.

## Pre-publish checks

```powershell
.\scripts\scan-secrets.ps1
.\scripts\scan-secrets.ps1 -History
.\mvnw.cmd test

Push-Location web
npm ci
npm test
npm run build
Pop-Location

.\scripts\academy\validate-manifests.ps1
.\scripts\academy\preflight.ps1 -Mode Offline
```

## Non-negotiable boundaries

- Secrets remain in the operator's current process or an approved external secret store; never commit `.env`, kubeconfig, Provider keys, AWS credentials, certificates, or generated Secret YAML.
- `prod` and `local-complete` fail closed without a real Provider, PostgreSQL, Redis, TLS, and OIDC configuration. Do not enable Mock fallback or demo seeds to make them boot.
- AWS Academy credentials are short-lived human-session credentials. Never inject them into Pods. The Academy event path uses JDBC outbox because workload SQS identity is not available.
- Academy static `hostPath` PostgreSQL is rebuildable course storage, not commercial durability. Academy results cannot close Singapore production, managed-service, DR, legal, or owner gates.
- A passing build, local smoke, or Kubernetes screenshot is a checkpoint. Acceptance status only changes with current reproducible evidence and required independent review.
