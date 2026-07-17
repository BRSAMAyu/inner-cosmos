# EKS-PROD-001 — full production-shape stack on real AWS EKS (+ a real prod-blocking bug fixed)

> Date: 2026-07-17 · Branch: `feat/supervisor-k8s-ai-g9` · Cluster: MyEKS (k8s 1.36, 2 amd64 nodes),
> AWS Academy Learner Lab (temp creds, ~/.aws only, never committed). Overlay: `deploy/k8s/overlays/eks-prod`.

## What was deployed (prod profile, not dev)
The full production-shape stack, all Running on real EKS:
- **PostgreSQL 16** StatefulSet with **TLS** (`sslmode=verify-full`) on a persistent volume (static PV,
  node-pinned) — the real system-of-record.
- **Redis** with **TLS** — shared sessions / rate-limit / scheduler locks across replicas.
- **Migration Job** (Flyway) — applied V1..V17 to Postgres, then exited.
- **API x2 + worker + scheduler** — prod profile (`SPRING_PROFILES_ACTIVE=prod,academy-eks`), real
  **DeepSeek** provider (`LLM_ALLOW_FALLBACK=false`, so Mock cannot mask it), image
  `…/inner-cosmos:eks-prod@sha256:9f42cbfa…`.
- **Gateway (HTTPS/443, envoy `eg`)** + HTTPRoute → real AWS ELB with a self-signed edge cert
  (satisfies prod `COOKIE_SECURE=true`).
- Secrets (runtime + postgres/redis server-TLS + client-CA + edge-TLS) created out-of-band; OIDC set to
  well-formed placeholders (web username/password auth is what real testers use; OIDC is mobile-only and
  the prod guard only checks the vars are present).

## Real prod-blocking bug found and fixed (code)
On first prod boot the API crash-looped: `PSQLException: syntax error at or near "AUTO_INCREMENT"` from
`SchemaM16Initializer` creating `tb_data_use_grant`.
- **Root cause**: the 14 `SchemaMx*Initializer`s are H2/MySQL-mode dev schema patchers gated only on
  `spring.flyway.enabled=false`. In prod the guard **requires** Flyway disabled on non-migration roles
  (the migration Job owns Flyway), so `flyway.enabled=false` is true on the app → the H2 initializers ran
  against **PostgreSQL** and emitted MySQL DDL (`AUTO_INCREMENT`), a hard syntax error. `tb_data_use_grant`
  itself is correctly created by Flyway `V16`; the initializer should never have run on Postgres. This
  latent bug means the base academy-eks overlay could not cold-start a fresh Postgres with the newer
  initializers present.
- **Fix**: added an H2-only gate to all 14 initializers —
  `@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")`.
  They still run on H2 (dev/tests) and are skipped on Postgres (where Flyway owns the schema). Full suite
  **836 tests, 0 failures/0 errors**; a representative schema-dependent test (CapsuleEnergyDecayTest) still
  green on H2.

## Verified end-to-end on real EKS (through the HTTPS ELB)
- `GET /actuator/health` → `{"status":"UP"}`; app booted clean (no guard rejection, no DDL crash).
- **register → login → create session → Aurora reply** all succeed. Login works across the 2 replicas
  (session in shared Redis); data lands in the shared Postgres. Aurora replied from the **real DeepSeek**
  model (warm companion-mode text, not Mock's deterministic output).

## Suitable for real multi-user testing
Because data is in one shared Postgres (persistent) and sessions are in shared Redis, multiple teammates
can register/login/write letters/etc. against the HTTPS URL and see consistent, durable state — unlike the
earlier dev deployment (per-pod ephemeral H2). The dev overlay (`eks-dev`) was removed to leave one clean
prod URL.

## Boundary / housekeeping
- Edge TLS is self-signed → browsers show a one-time warning (click through). OIDC is placeholder
  (mobile-only path inert). Learner Lab creds/cluster are temporary; teardown: `kubectl delete -k
  deploy/k8s/overlays/eks-prod` (removes the ELB). Rotate the DeepSeek/GLM keys after testing.
