# PROFILE-FOUNDATION-001 three-profile and Academy manifest foundation

Builder status: `IMPLEMENTED`, acceptance remains `IN_PROGRESS`.

Implemented:

- Sanitized `13` connection guide with runtime discovery and no infrastructure identifiers.
- `local-complete`, `academy-eks`, and `commercial-sg` Spring configuration boundaries, all preserving complete product semantics.
- `local-complete` Compose topology with TLS PostgreSQL/pgvector, TLS Redis, non-root app, HTTPS edge, real-provider/OIDC injection contract, and deterministic teardown.
- `academy-eks` Kustomize overlay with two API replicas, static hostPath PostgreSQL, disposable TLS Redis, Gateway/HTTPRoute, startup/readiness/liveness probes, requests/limits, PDB, HPA, topology spread, preStop, NetworkPolicy, no committed Secret values, no SQS, no EBS CSI, and no IRSA.
- Live capability preflight with separate human and credential-free Pod probes and mandatory cleanup.
- Deployment helper that requires an immutable image digest, runtime-discovered storage node/GatewayClass, transient TLS/runtime Secrets, and current-session preflight.

Validation:

- Java 21 full regression: 99 suites, 695 tests, 0 failures/errors/skips.
- SpotBugs high-confidence gate: 0 findings; CycloneDX: 117 components.
- Kustomize/client dry-run: 16 resources; required-control and forbidden-dependency policy checks pass.
- Offline preflight: PASS. Live current-session preflight and cleanup: PASS.
- Local Compose config: PASS.
- Local runtime smoke: HTTPS health UP, PostgreSQL TLS true, Redis TLS PONG, runtime user `appuser`, 4 scheduler lease keys.
- Secret scan: 0 findings; `git diff --check`: PASS.

Evidence boundary and remaining work:

- The local smoke used non-secret validation placeholders and did not call or evaluate a real LLM/IdP.
- Academy resources have not yet been applied as an application workload and Gateway status has not been observed.
- The API deployment is explicitly labeled `all-in-one-transition`; independent worker/scheduler/migration roles remain pending.
- `jdbc-outbox` is declared as the Academy adapter contract, but transactional outbox/inbox implementation is pending.
- No Academy multi-replica session/rate/lease, HPA, rolling-update, Pod recovery, or external route result is claimed yet.
- `commercial-sg` is a target configuration boundary, not deployed production evidence.
- No Aurora, Self, portrait, capsule, matching, starfield, or slow-social semantics were removed or narrowed.
